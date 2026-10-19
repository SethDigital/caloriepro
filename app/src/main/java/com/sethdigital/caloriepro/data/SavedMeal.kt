package com.sethdigital.caloriepro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** A named group of foods, such as "My usual breakfast", that can be logged in one tap. */
@Entity(tableName = "saved_meals")
data class SavedMeal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

/** One food in a [SavedMeal]; the same values as a [FoodEntry], without a day or time. */
@Entity(tableName = "saved_meal_items", indices = [Index("mealId")])
data class SavedMealItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mealId: Long,
    /** Order within the meal. */
    val position: Int,
    val name: String,
    val servings: Double,
    val servingSize: String?,
    val calories: Double,
    val proteinG: Double?,
    val carbsG: Double?,
    val fatG: Double?,
    val saturatedFatG: Double?,
    val sugarG: Double?,
    val fiberG: Double?,
    val sodiumMg: Double?,
    val caffeineMg: Double?,
    val barcode: String?,
    val byWeight: Boolean,
) {
    fun toEntry(epochDay: Long, meal: Meal, createdAt: Long) = FoodEntry(
        epochDay = epochDay,
        name = name,
        meal = meal,
        servings = servings,
        servingSize = servingSize,
        calories = calories,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        saturatedFatG = saturatedFatG,
        sugarG = sugarG,
        fiberG = fiberG,
        sodiumMg = sodiumMg,
        caffeineMg = caffeineMg,
        createdAt = createdAt,
        barcode = barcode,
        byWeight = byWeight,
    )

    companion object {
        fun from(entry: FoodEntry, position: Int) = SavedMealItem(
            mealId = 0,
            position = position,
            name = entry.name,
            servings = entry.servings,
            servingSize = entry.servingSize,
            calories = entry.calories,
            proteinG = entry.proteinG,
            carbsG = entry.carbsG,
            fatG = entry.fatG,
            saturatedFatG = entry.saturatedFatG,
            sugarG = entry.sugarG,
            fiberG = entry.fiberG,
            sodiumMg = entry.sodiumMg,
            caffeineMg = entry.caffeineMg,
            barcode = entry.barcode,
            byWeight = entry.byWeight,
        )
    }
}

data class SavedMealWithItems(
    @Embedded val meal: SavedMeal,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<SavedMealItem>,
) {
    /** Items in the order they were saved. */
    val orderedItems: List<SavedMealItem> get() = items.sortedBy { it.position }

    val calories: Double get() = items.sumOf { it.calories * it.servings }
}

@Dao
abstract class SavedMealDao {

    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<SavedMealWithItems>>

    @Insert
    protected abstract suspend fun insertMeal(meal: SavedMeal): Long

    @Insert
    protected abstract suspend fun insertItems(items: List<SavedMealItem>)

    @Query("DELETE FROM saved_meal_items WHERE mealId = :mealId")
    protected abstract suspend fun deleteItems(mealId: Long)

    @Delete
    protected abstract suspend fun deleteMeal(meal: SavedMeal)

    @Transaction
    open suspend fun insert(meal: SavedMeal, items: List<SavedMealItem>): Long {
        val id = insertMeal(meal)
        insertItems(items.map { it.copy(id = 0, mealId = id) })
        return id
    }

    @Transaction
    open suspend fun delete(meal: SavedMeal) {
        deleteItems(meal.id)
        deleteMeal(meal)
    }
}
