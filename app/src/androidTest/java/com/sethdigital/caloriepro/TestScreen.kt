package com.sethdigital.caloriepro

import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.printToString
import androidx.test.ext.junit.rules.ActivityScenarioRule

typealias AppTestRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

/** A freshly booted emulator may be locked or asleep; make sure the app is actually on screen. */
fun AppTestRule.keepScreenOn() {
    activityRule.scenario.onActivity { activity ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        }
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity.getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(activity, null)
    }
}

/** Waits for a node matching [matcher], failing with a dump of what is on screen instead. */
fun AppTestRule.waitFor(matcher: SemanticsMatcher, timeoutMillis: Long = 15_000) {
    try {
        waitUntil(timeoutMillis) { onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    } catch (e: Throwable) { // ComposeTimeoutException is a Throwable, not an Exception
        val screen = runCatching { onAllNodes(isRoot(), useUnmergedTree = true).printToString() }
            .getOrElse { "(could not read the screen: $it)" }
        // One line, because the Gradle console only shows the first lines of a failure.
        val oneLine = screen.lines().joinToString(" ⏎ ") { it.trim() }
        throw AssertionError(
            "Never appeared: ${matcher.description}. Activity state: ${activityRule.scenario.state}. On screen: $oneLine",
            e,
        )
    }
}
