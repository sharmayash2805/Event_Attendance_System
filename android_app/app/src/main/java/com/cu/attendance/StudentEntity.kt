package com.cu.attendance

import androidx.room.Entity
@Entity(tableName = "students", primaryKeys = ["eventId", "uid"])
data class StudentEntity(
    val eventId: Long,
    val uid: String,
    val name: String,
    val branch: String,
    val year: String,
    val status: String = "Absent",
    val timestamp: String = ""
)
