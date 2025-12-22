package com.cu.attendance

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a pending action that must be synced when online.
 * Example actions: MARK_PRESENT, INSERT_STUDENT, UPDATE_STUDENT.
 */
@Entity(tableName = "offline_queue")
data class OfflineQueueEntity(
	@PrimaryKey(autoGenerate = true) val id: Long = 0,
	val action: String,
	val uid: String,
	val payload: String? = null, // optional JSON payload for extra data
	val createdAt: Long = System.currentTimeMillis(),
	val eventId: Long = 0
)
