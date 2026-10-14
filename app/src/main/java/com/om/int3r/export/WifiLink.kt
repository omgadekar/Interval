package com.om.int3r.export

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import kotlin.coroutines.resume

/**
 * Brings up Wi-Fi for as long as we hold the request. Watches usually talk to the phone over
 * Bluetooth and keep Wi-Fi off, so an export has to ask for it explicitly.
 */
class WifiLink(context: Context) {
    private val cm = context.getSystemService(ConnectivityManager::class.java)
    private var callback: ConnectivityManager.NetworkCallback? = null

    /** The watch's Wi-Fi IPv4 address, or null if Wi-Fi did not come up in time. */
    suspend fun connect(timeoutMs: Long = 30_000): String? = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val address = cm.getLinkProperties(network)?.linkAddresses
                        ?.map { it.address }
                        ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                        ?.hostAddress
                    if (address != null && cont.isActive) cont.resume(address)
                }
            }
            callback = cb
            cm.requestNetwork(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), cb)
            cont.invokeOnCancellation { release() }
        }
    }

    fun release() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
    }
}