package com.cu.attendance

import android.content.Context

object EventPrefs {
	private const val PREFS = "event_prefs"
	private const val KEY_ID = "selected_event_id"
	private const val KEY_NAME = "selected_event_name"

	fun loadSelectedEvent(context: Context): SelectedEvent? {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val id = prefs.getLong(KEY_ID, -1L)
		val name = prefs.getString(KEY_NAME, "").orEmpty()
		if (id <= 0L || name.isBlank()) return null
		return SelectedEvent(eventId = id, eventName = name)
	}

	fun saveSelectedEvent(context: Context, selected: SelectedEvent?) {
		val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		if (selected == null) {
			prefs.edit().remove(KEY_ID).remove(KEY_NAME).apply()
			return
		}
		prefs.edit()
			.putLong(KEY_ID, selected.eventId)
			.putString(KEY_NAME, selected.eventName)
			.apply()
	}
}
