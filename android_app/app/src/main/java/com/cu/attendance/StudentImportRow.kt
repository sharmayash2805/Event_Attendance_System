package com.cu.attendance

data class StudentImportRow(
    val uid: String,
    val name: String,
    val branch: String,
    val year: String,
    val isValid: Boolean
)
