package com.cu.attendance

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StudentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(students: List<StudentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(student: StudentEntity)

    @Query("SELECT * FROM students WHERE eventId = :eventId")
    suspend fun getAllStudents(eventId: Long): List<StudentEntity>

    @Query("SELECT * FROM students WHERE uid = :uid AND eventId = :eventId LIMIT 1")
    suspend fun getStudentByUid(eventId: Long, uid: String): StudentEntity?

    @Query("SELECT * FROM students WHERE eventId = :eventId AND (name LIKE '%' || :query || '%' OR uid LIKE '%' || :query || '%') ORDER BY name")
    suspend fun searchStudents(eventId: Long, query: String): List<StudentEntity>

    @Query("UPDATE students SET status = :status, timestamp = :timestamp WHERE uid = :uid AND eventId = :eventId")
    suspend fun updateStatus(eventId: Long, uid: String, status: String, timestamp: String)

    @Query("SELECT COUNT(*) FROM students WHERE status = 'Present' AND eventId = :eventId")
    suspend fun getPresentCount(eventId: Long): Int

    @Query("SELECT COUNT(*) FROM students WHERE eventId = :eventId")
    suspend fun getCount(eventId: Long): Int

    @Query("DELETE FROM students WHERE eventId = :eventId")
    suspend fun deleteAll(eventId: Long)
}
