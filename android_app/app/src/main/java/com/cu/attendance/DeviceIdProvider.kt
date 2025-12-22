package com.cu.attendance

import android.content.Context
import java.util.UUID

object DeviceIdProvider {
	private const val PREFS = "device_prefs"
	private const val KEY_DEVICE_ID = "device_id"

	fun getOrCreate(context: Context): String {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val existing = prefs.getString(KEY_DEVICE_ID, "").orEmpty().trim()
		if (existing.isNotEmpty()) return existing
		val created = UUID.randomUUID().toString()
		prefs.edit().putString(KEY_DEVICE_ID, created).apply()
		return created
	}
}
