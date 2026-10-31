package com.kimifreepainter.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "painter_task")
data class PainterTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val prompt: String,
    val status: String,
    val botUserId: String = "",
    val botName: String = "",
    val triggerMomentId: String? = null,
    val resultMomentId: String? = null,
    val description: String? = null,
    val imageUrls: String = "[]",
    val localPaths: String? = null,
    val errorCode: Int? = null,
    val errorMsg: String? = null,
    val cleanFailed: Boolean = false,
    val deletedFromCommunity: Boolean = false,
    val retryOf: Long? = null,
    val createdAt: Long,
    val finishedAt: Long? = null
)

@Entity(tableName = "seen_moment")
data class SeenMoment(
    @PrimaryKey val momentId: String,
    val seenAt: Long
)

@Entity(tableName = "watermark")
data class Watermark(
    @PrimaryKey val userId: String,
    val lastTime: Long,
    val updatedAt: Long
)

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(task: PainterTask): Long

    @Update
    suspend fun update(task: PainterTask)

    @Query("SELECT * FROM painter_task ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PainterTask>>

    @Query("SELECT * FROM painter_task WHERE id = :id")
    suspend fun byId(id: Long): PainterTask?

    @Query("SELECT * FROM painter_task WHERE status IN ('QUEUED','POSTING','GENERATING','DOWNLOADING','CLEANING') ORDER BY createdAt")
    suspend fun pending(): List<PainterTask>

    @Query("DELETE FROM painter_task WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM painter_task")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM painter_task WHERE status IN ('QUEUED','POSTING','GENERATING','DOWNLOADING','CLEANING')")
    suspend fun activeCount(): Int
}

@Dao
interface MomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun remember(m: SeenMoment)

    @Query("SELECT momentId FROM seen_moment")
    suspend fun all(): List<String>

    @Query("DELETE FROM seen_moment WHERE momentId NOT IN (SELECT momentId FROM seen_moment ORDER BY seenAt DESC LIMIT 500)")
    suspend fun trim()

    @Query("SELECT lastTime FROM watermark WHERE userId = :uid")
    suspend fun watermark(uid: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setWatermark(w: Watermark)
}

@Database(entities = [PainterTask::class, SeenMoment::class, Watermark::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tasks(): TaskDao
    abstract fun moments(): MomentDao

    companion object {
        @Volatile private var inst: AppDatabase? = null

        fun get(context: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "kimpainter.db")
                .build().also { inst = it }
        }
    }
}
