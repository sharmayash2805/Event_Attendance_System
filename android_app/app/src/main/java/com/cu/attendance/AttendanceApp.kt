package com.cu.attendance

import android.app.Application

class AttendanceApp : Application() {
	override fun onCreate() {
		super.onCreate()
		ServerConfig.load(this)
		// Keep background sync running whenever possible.
		SyncWork.enqueuePeriodic(this)
	}
}
