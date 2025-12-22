package com.cu.attendance

import android.content.Context
import android.net.Uri
import android.util.Log
import org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

// Blocking read; callers invoke on background thread.
fun readExcelFile(context: Context, uri: Uri): List<StudentImportRow> {
    val rows = mutableListOf<StudentImportRow>()

    fun InputStream.parse(): List<StudentImportRow> {
        val parsed = mutableListOf<StudentImportRow>()
        WorkbookFactory.create(this).use { workbook ->
            val sheet = workbook.getSheetAt(0) ?: return parsed
            val last = sheet.lastRowNum
            if (last < 1) return parsed

            for (i in 1..last) {
                val row = sheet.getRow(i) ?: continue

                val uid = row.getCell(0)?.toString()?.trim().orEmpty()
                val name = row.getCell(1)?.toString()?.trim().orEmpty()
                val branch = row.getCell(2)?.toString()?.trim().orEmpty()
                val year = row.getCell(3)?.toString()?.trim().orEmpty()

                val isValid = uid.isNotEmpty() && name.isNotEmpty()
                parsed.add(StudentImportRow(uid, name, branch, year, isValid))
            }
        }
        return parsed
    }

    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            rows.addAll(input.parse())
        }
        return rows
    } catch (e: OLE2NotOfficeXmlFileException) {
        Log.e("ExcelReader", "Not an Excel file: ${e.message}", e)
        return emptyList()
    } catch (e: SecurityException) {
        Log.e("ExcelReader", "Permission denied opening Excel: ${e.message}", e)
        return emptyList()
    } catch (e: Exception) {
        Log.e("ExcelReader", "Failed to read Excel: ${e.message}", e)
        return emptyList()
    }
}
