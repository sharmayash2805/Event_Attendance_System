package com.cu.attendance

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [StudentEntity::class, OfflineQueueEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
	abstract fun studentDao(): StudentDao
	abstract fun offlineQueueDao(): OfflineQueueDao

	companion object {
		@Volatile
		private var INSTANCE: AppDatabase? = null

		fun getDatabase(context: Context): AppDatabase {
			return INSTANCE ?: synchronized(this) {
				val instance = Room.databaseBuilder(
					context.applicationContext,
					AppDatabase::class.java,
					"attendance_database"
				)
					// Note: keep DB work off main thread in production.
					.addMigrations(MIGRATION_2_3, MIGRATION_3_4)
					.build()
				INSTANCE = instance
				instance
			}
		}

		val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
			override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
				database.execSQL("ALTER TABLE students ADD COLUMN session TEXT NOT NULL DEFAULT 'Session 1'")
				database.execSQL("ALTER TABLE offline_queue ADD COLUMN session TEXT NOT NULL DEFAULT 'Session 1'")
			}
		}


		val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
			override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
				// Students: move from (uid PK, session) -> (eventId+uid PK).
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS students_new (
						eventId INTEGER NOT NULL,
						uid TEXT NOT NULL,
						name TEXT NOT NULL,
						branch TEXT NOT NULL,
						year TEXT NOT NULL,
						status TEXT NOT NULL,
						timestamp TEXT NOT NULL,
						PRIMARY KEY(eventId, uid)
					)
					""".trimIndent()
				)
				database.execSQL(
					"""
					INSERT INTO students_new (eventId, uid, name, branch, year, status, timestamp)
					SELECT 1 AS eventId,
					       uid,
					       COALESCE(name, ''),
					       COALESCE(branch, ''),
					       COALESCE(year, ''),
					       COALESCE(status, 'Absent'),
					       COALESCE(timestamp, '')
					FROM students
					""".trimIndent()
				)
				database.execSQL("DROP TABLE students")
				database.execSQL("ALTER TABLE students_new RENAME TO students")

				// Offline queue: session -> eventId. Pending items map to eventId=1.
				database.execSQL(
					"""
					CREATE TABLE IF NOT EXISTS offline_queue_new (
						id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
						action TEXT NOT NULL,
						uid TEXT NOT NULL,
						payload TEXT,
						createdAt INTEGER NOT NULL,
						eventId INTEGER NOT NULL
					)
					""".trimIndent()
				)
				database.execSQL(
					"""
					INSERT INTO offline_queue_new (id, action, uid, payload, createdAt, eventId)
					SELECT id, action, uid, payload, createdAt, 1 AS eventId FROM offline_queue
					""".trimIndent()
				)
				database.execSQL("DROP TABLE offline_queue")
				database.execSQL("ALTER TABLE offline_queue_new RENAME TO offline_queue")
			}
		}
	}
}
