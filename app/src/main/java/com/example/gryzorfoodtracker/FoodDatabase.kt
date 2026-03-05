package com.example.gryzorfoodtracker

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "meals")
data class MealEntity(
    @PrimaryKey val id: String,
    val date: String,
    val time: String,
    val type: String,
    val description: String
)

@Entity(tableName = "daily_tags")
data class DailyTagEntity(
    @PrimaryKey val date: String,
    val tags: String
)

@Entity(tableName = "daily_insights")
data class DailyInsightEntity(
    @PrimaryKey val date: String,
    val insight: String
)

@Entity(tableName = "daily_metrics")
data class DailyMetricEntity(
    @PrimaryKey val date: String,
    val totalKcal: String,
    val deficit: String
)

@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val date: String,
    val weight: String,
    val bodyFat: String
)

data class DayCount(
    val date: String,
    val count: Int
)

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE date = :targetDate ORDER BY time ASC")
    fun getMealsForDate(targetDate: String): Flow<List<MealEntity>>

    @Query("SELECT DISTINCT description FROM meals WHERE type = :mealType ORDER BY date DESC LIMIT 15")
    fun getSuggestions(mealType: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeal(meal: MealEntity)

    @Update
    fun updateMeal(meal: MealEntity)

    @Delete
    fun deleteMeal(meal: MealEntity)

    @Query("SELECT * FROM daily_tags WHERE date = :targetDate")
    fun getTagsForDate(targetDate: String): Flow<DailyTagEntity?>

    @Query("SELECT * FROM daily_tags")
    fun getAllTags(): Flow<List<DailyTagEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTags(tagEntity: DailyTagEntity)

    @Query("SELECT * FROM daily_insights WHERE date = :targetDate")
    fun getInsightForDate(targetDate: String): Flow<DailyInsightEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertInsight(insightEntity: DailyInsightEntity)

    @Query("SELECT * FROM daily_metrics WHERE date = :targetDate")
    fun getMetricsForDate(targetDate: String): Flow<DailyMetricEntity?>

    @Query("SELECT * FROM daily_metrics")
    fun getAllMetrics(): Flow<List<DailyMetricEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMetric(metricEntity: DailyMetricEntity)

    @Query("SELECT * FROM measurements WHERE date = :targetDate")
    fun getMeasurementForDate(targetDate: String): Flow<MeasurementEntity?>

    @Query("SELECT * FROM measurements ORDER BY date ASC")
    fun getAllMeasurements(): Flow<List<MeasurementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMeasurement(measurement: MeasurementEntity)

    @Query("SELECT date, COUNT(id) as count FROM meals GROUP BY date")
    fun getMealCounts(): Flow<List<DayCount>>
}

@Database(
    entities = [MealEntity::class, DailyTagEntity::class, DailyInsightEntity::class, DailyMetricEntity::class, MeasurementEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealDao(): MealDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_tags` (`date` TEXT NOT NULL, `tags` TEXT NOT NULL, PRIMARY KEY(`date`))")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_insights` (`date` TEXT NOT NULL, `insight` TEXT NOT NULL, PRIMARY KEY(`date`))")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `daily_metrics` (`date` TEXT NOT NULL, `totalKcal` TEXT NOT NULL, `deficit` TEXT NOT NULL, PRIMARY KEY(`date`))")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `measurements` (`date` TEXT NOT NULL, `weight` TEXT NOT NULL, `bodyFat` TEXT NOT NULL, PRIMARY KEY(`date`))")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "food_tracker_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}