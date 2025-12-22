package com.cu.attendance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cu.attendance.HomeScreen
import com.cu.attendance.ImportPreviewScreen
import com.cu.attendance.SearchScreen
import com.cu.attendance.StudentImportRow

@Composable
fun AppNavHostWithScan(onScan: () -> Unit) {
    val navController = rememberNavController()
    var importedStudents by remember { mutableStateOf<List<StudentImportRow>>(emptyList()) }
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onScanClick = { navController.navigate("scanner") },
                onSearchClick = { navController.navigate("search") },
                onImportClick = { navController.navigate("import") },
                onExportClick = { /* Not implemented */ }
            )
        }
        composable("scanner") {
            // Launch scan intent from MainActivity
            onScan()
            navController.popBackStack()
        }
        composable("search") {
            SearchScreen(onBack = { navController.popBackStack() })
        }
        composable("import") {
            ImportPreviewScreen(
                students = importedStudents,
                onBack = { navController.popBackStack() },
                onConfirm = { validStudents ->
                    importedStudents = validStudents
                    navController.popBackStack()
                }
            )
        }
        // composable("export") { /* ExportScreen() */ } // Not implemented
    }
}
