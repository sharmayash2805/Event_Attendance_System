package com.cu.attendance

import android.content.Context
import android.net.Uri

/**
 * Central place to store and retrieve the server base URL. Uses a shared preference so
 * the setting survives app restarts and is available to all activities.
 */
object ServerConfig {
    private const val PREFS = "server_prefs"
    private const val KEY_URL = "server_url"

    @Volatile
    private var overrideBaseUrl: String? = null

    /** Load the persisted URL into memory. Call once per process (Activity onCreate is fine). */
    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_URL, "").orEmpty().trim()
        overrideBaseUrl = saved.ifBlank { null }
    }

    /** Current effective base URL (trimmed, trailing slash removed). */
    fun getBaseUrl(): String {
        val fallback = BuildConfig.BASE_URL.trim().trimEnd('/')
        val candidate = overrideBaseUrl?.let { normalize(it) }
        return (candidate ?: fallback).trimEnd('/')
    }

    /** Persist and apply a new base URL. Returns the normalized value or fallback if invalid. */
    fun update(context: Context, url: String): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val normalized = normalize(url) ?: BuildConfig.BASE_URL.trim().trimEnd('/')
        overrideBaseUrl = normalized
        prefs.edit().putString(KEY_URL, normalized).apply()
        return normalized
    }

    /** Short host (with port) for display purposes. */
    fun displayHost(): String {
        return runCatching {
            val uri = Uri.parse(getBaseUrl())
            val host = uri.host ?: return getBaseUrl()
            val port = if (uri.port != -1) ":${uri.port}" else ""
            "$host$port"
        }.getOrDefault(getBaseUrl())
    }

    private fun normalize(url: String): String? {
        var candidate = url.trim()
        if (candidate.isEmpty()) return null
        if (!candidate.startsWith("http://") && !candidate.startsWith("https://")) {
            candidate = "http://$candidate"
        }
        return candidate.trimEnd('/')
    }
}
