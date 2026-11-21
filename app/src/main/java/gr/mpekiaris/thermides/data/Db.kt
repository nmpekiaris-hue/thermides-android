package gr.mpekiaris.thermides.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class Meal(val label: String) {
    BREAKFAST("Πρωινό"),
    LUNCH("Μεσημεριανό"),
    DINNER("Βραδινό"),
    SNACK("Σνακ");

    companion object {
        fun of(name: String): Meal = entries.firstOrNull { it.name == name } ?: SNACK

        fun forHour(hour: Int): Meal = when {
            hour < 11 -> BREAKFAST
            hour < 17 -> LUNCH
            else -> DINNER
        }
    }
}

@Entity(tableName = "food", indices = [Index("time")])
data class FoodEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kcal: Int,
    /** Ώρα κατανάλωσης, epoch millis. */
    val time: Long,
    /** Όνομα του [Meal]. */
    val meal: String,
)

data class FrequentFood(
    val name: String,
    val kcal: Int,
    val uses: Int,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food WHERE time >= :from AND time < :to ORDER BY time")
    fun between(from: Long, to: Long): Flow<List<FoodEntry>>

    @Query(
        "SELECT name, kcal, COUNT(*) AS uses FROM food " +
            "GROUP BY name, kcal ORDER BY uses DESC, MAX(time) DESC LIMIT 12"
    )
    fun frequent(): Flow<List<FrequentFood>>

    @Insert
    suspend fun insert(entry: FoodEntry): Long

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("DELETE FROM food WHERE time >= :from AND time < :to")
    suspend fun deleteBetween(from: Long, to: Long): Int
}

@Database(entities = [FoodEntry::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun food(): FoodDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "thermides.db")
                .build()
                .also { instance = it }
        }
    }
}
