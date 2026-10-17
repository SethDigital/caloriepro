# CaloriePro

A calorie counter for Android that keeps everything on your phone.

- **Daily log by date.** Today's food grouped by meal, with calories against
  your goal and a meter for each nutrient against its daily limit. Anything
  you've gone over is called out. Move between days with the arrows or the
  calendar.
- **Recommended daily limits, or your own.** Out of the box the limits are the
  U.S. FDA Daily Values from nutrition labels (2,000 kcal diet): protein at
  least 50 g, carbs up to 275 g, fat up to 78 g, saturated fat up to 20 g, sugar
  up to 50 g, fiber at least 28 g, sodium up to 2,300 mg, plus caffeine up to
  400 mg. Change any of them in Settings, or put them back with
  "Use recommended".
- **Manual entry.** Type in a food's name, servings and nutrition values.
  Foods you've logged before show up as suggestions, so re-logging takes one tap.
- **Label scanning.** Point the camera at the nutrition facts on a package (or
  pick a photo) and the app fills in calories, protein, carbs, fat, saturated
  fat, sugar, fiber, sodium, caffeine and the serving size. You check the
  numbers before saving.
- **History.** A 7-day chart and per-day totals. Tap a day to open it.
- **Swipe left** on an entry to delete it (with undo). Tap it to edit.

## Your data stays on the phone

- The log lives in a local SQLite database inside the app.
- The app has **no internet permission**, so it cannot send anything anywhere.
- Label text is recognized on the device with Google ML Kit's bundled model.
  Photos are never saved.
- The log is excluded from Android cloud backup. Uninstalling the app deletes it.

## Install it on your phone

Each push builds a signed APK on GitHub Actions.

1. Open the repository's **Releases** page on your phone and download
   `CaloriePro.apk` from the **latest** release. (The release is refreshed on
   every push to `main`. For other branches, download the `CaloriePro-apk`
   artifact from the workflow run on the **Actions** tab instead. It is a zip
   with the APK inside.)
2. Open the file. Android will ask you to allow installs from your browser or
   file manager the first time.
3. To update later, install a newer APK the same way. Your log is kept.

Requires Android 8.0 or newer.

### Tips for scanning

- Fill the frame with the Nutrition Facts panel and hold the phone parallel to it.
- Use the flashlight button in dim light. Avoid glare on shiny packages.
- US/Canadian panels and UK/EU tables both work. For EU tables the per-serving
  column is used when there is one; otherwise values are per 100 g/ml.
- "Show scanned text" under the scan notice shows exactly what was read.

## Building

Open the project in Android Studio, or from the command line with JDK 17 and
the Android SDK installed:

```sh
./gradlew testDebugUnitTest      # parser unit tests (JVM)
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew connectedAndroidTest   # instrumented tests, needs a device or emulator
```

Release builds are signed with `app/signing/caloriepro.jks` (password
`caloriepro`), which is checked in on purpose: every build, local or CI, has the
same signature, so you can always install a new build over the old one without
losing data. Debug builds install alongside as "com.sethdigital.caloriepro.debug".

## Code layout

```
app/src/main/java/com/sethdigital/caloriepro/
  data/            Room database, entries, daily summaries, goals
  scan/            Camera screen and ML Kit text recognition
  scan/parser/     Pure-Kotlin nutrition label parser (unit tested)
  ui/day/          Daily log screen
  ui/entry/        Add/edit food form
  ui/history/      History and weekly chart
  ui/settings/     Goals and privacy info
```

The label parser rebuilds the label's rows from the recognized text boxes
(undoing any tilt), then reads each nutrient from its row, handling OCR slips
such as `Og` for `0g`, `% Daily Value` columns, kJ/kcal, salt vs. sodium and
per-100g columns.

Database schema changes must come with a Room migration (exported schemas go
to `app/schemas/`). There is deliberately no destructive fallback, so an update
can never wipe the log.
