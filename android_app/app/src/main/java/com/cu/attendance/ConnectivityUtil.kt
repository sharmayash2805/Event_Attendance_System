package com.cu.attendance

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

object ConnectivityUtil {
	fun isOnline(context: Context): Boolean {
		val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
		val network = cm.activeNetwork ?: return false
		val caps = cm.getNetworkCapabilities(network) ?: return false
		return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
			caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
	}

	fun observeOnline(context: Context): Flow<Boolean> {
		return callbackFlow {
			val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
			if (cm == null) {
				trySend(false)
				close()
				return@callbackFlow
			}

			val callback = object : ConnectivityManager.NetworkCallback() {
				override fun onAvailable(network: Network) {
					trySend(isOnline(context))
				}

				override fun onLost(network: Network) {
					trySend(isOnline(context))
				}

				override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
					trySend(isOnline(context))
				}
			}

			trySend(isOnline(context))

			val request = NetworkRequest.Builder()
				.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
				.build()
			cm.registerNetworkCallback(request, callback)

			awaitClose {
				runCatching { cm.unregisterNetworkCallback(callback) }
			}
		}.distinctUntilChanged()
	}
}
