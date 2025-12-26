package com.cu.attendance

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Small OkHttp wrapper with a configurable base URL (see BuildConfig.BASE_URL).
 */
object ApiClient {
	// Default LAN IP for the Flask server; override via -PSERVER_URL when building.
	private const val DEFAULT_BASE_URL = "http://192.168.1.39:5000"

	val client: OkHttpClient by lazy {
		OkHttpClient.Builder()
			// Render (and similar hosts) can cold-start and exceed a few seconds.
			.connectTimeout(10, TimeUnit.SECONDS)
			.readTimeout(20, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.build()
	}

	fun url(path: String): String {
		if (path.startsWith("http")) return path
		val normalizedPath = if (path.startsWith("/")) path else "/$path"
		val base = ServerConfig.getBaseUrl().ifBlank { DEFAULT_BASE_URL }.trimEnd('/')
		return "$base$normalizedPath"
	}
}
