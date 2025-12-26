package com.cu.attendance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OfflineQueueDao {
	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun enqueue(item: OfflineQueueEntity): Long

	@Query("SELECT id FROM offline_queue WHERE action = :action AND eventId = :eventId AND uid = :uid LIMIT 1")
	suspend fun findExistingId(action: String, eventId: Long, uid: String): Long?

	@Query("SELECT * FROM offline_queue ORDER BY createdAt ASC")
	suspend fun getAllPending(): List<OfflineQueueEntity>

	@Query("SELECT COUNT(*) FROM offline_queue")
	suspend fun getPendingCount(): Int

	@Query("SELECT * FROM offline_queue WHERE eventId = :eventId ORDER BY createdAt ASC")
	suspend fun getAll(eventId: Long): List<OfflineQueueEntity>

	@Query("DELETE FROM offline_queue WHERE id = :id")
	suspend fun deleteById(id: Long)

	@Query("DELETE FROM offline_queue WHERE eventId = :eventId")
	suspend fun clear(eventId: Long)
}
