package com.elgato.keylightcontroller.data.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Discovered light info from mDNS
 */
data class DiscoveredLight(
    val serviceName: String,
    val ipAddress: String,
    val port: Int
)

/**
 * Service for discovering Elgato Key Light devices via mDNS/NSD
 */
class LightDiscoveryService(private val context: Context) {

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    companion object {
        private const val TAG = "LightDiscovery"
        private const val SERVICE_TYPE = "_elg._tcp."
    }

    /**
     * Discover Elgato lights on the network
     * Emits DiscoveredLight objects as they are found
     */
    fun discoverLights(): Flow<DiscoveredLight> = callbackFlow {
        Log.d(TAG, "Starting light discovery")

        // Acquire multicast lock for mDNS
        multicastLock = wifiManager.createMulticastLock("elgato_discovery").apply {
            setReferenceCounted(true)
            acquire()
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                // Resolve the service to get IP and port
                resolveService(serviceInfo) { resolved ->
                    resolved?.let {
                        trySend(it)
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped for $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                close(Exception("Discovery start failed with code $errorCode"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        discoveryListener = listener

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            close(e)
        }

        awaitClose {
            stopDiscovery()
        }
    }

    private fun resolveService(serviceInfo: NsdServiceInfo, callback: (DiscoveredLight?) -> Unit) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                callback(null)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host?.hostAddress}:${serviceInfo.port}")
                val hostAddress = serviceInfo.host?.hostAddress
                if (hostAddress != null) {
                    callback(
                        DiscoveredLight(
                            serviceName = serviceInfo.serviceName,
                            ipAddress = hostAddress,
                            port = serviceInfo.port
                        )
                    )
                } else {
                    callback(null)
                }
            }
        }

        try {
            nsdManager.resolveService(serviceInfo, resolveListener)
        } catch (e: Exception) {
            Log.e(TAG, "Exception resolving service", e)
            callback(null)
        }
    }

    /**
     * Stop discovery and release resources
     */
    fun stopDiscovery() {
        Log.d(TAG, "Stopping discovery")

        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping discovery", e)
            }
            discoveryListener = null
        }

        multicastLock?.let { lock ->
            if (lock.isHeld) {
                lock.release()
            }
            multicastLock = null
        }
    }

    /**
     * Check if a specific light is reachable
     */
    suspend fun checkLightOnline(ipAddress: String, port: Int): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val api = ElgatoApi.create(ipAddress, port)
                kotlinx.coroutines.runBlocking {
                    try {
                        val response = api.getLightState()
                        continuation.resume(response.isSuccessful)
                    } catch (e: Exception) {
                        continuation.resume(false)
                    }
                }
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }
    }
}
