package com.cu.attendance

data class EventDto(
	val eventId: Long,
	val eventName: String,
	val startTime: String = "",
	val endTime: String = "",
	val isActive: Boolean = true
)

data class SelectedEvent(
	val eventId: Long,
	val eventName: String
)
