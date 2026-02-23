package com.elgato.keylightcontroller.data.repository

import android.content.Context
import android.util.Log
import com.elgato.keylightcontroller.data.model.Light
import com.elgato.keylightcontroller.data.model.LightStateRequest
import com.elgato.keylightcontroller.data.model.SavedLight
import com.elgato.keylightcontroller.data.network.DiscoveredLight
import com.elgato.keylightcontroller.data.network.ElgatoApi
import com.elgato.keylightcontroller.data.network.LightDiscoveryService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Main repository for light operations
 */
class LightRepository(context: Context) {

    private val discoveryService = LightDiscoveryService(context)
    private val preferencesRepository = LightPreferencesRepository(context)

    companion object {
        private const val TAG = "LightRepository"
        private const val DISCOVERY_TIMEOUT_MS = 5000L
    }

    /**
     * Get saved lights flow
     */
    val savedLights: Flow<List<SavedLight>> = preferencesRepository.savedLights

    /**
     * Discover lights on the network and merge with saved lights
     * Returns a flow that emits lists of lights as they are discovered
     */
    fun discoverAndMergeLights(): Flow<List<Light>> = flow {
        val savedLightsList = preferencesRepository.savedLights.first()
        val discoveredLights = mutableMapOf<String, Light>()

        // First, emit saved lights as offline
        val initialLights = savedLightsList.map { saved ->
            Light(
                id = saved.id,
                name = saved.name,
                ipAddress = saved.ipAddress,
                port = saved.port,
                isOnline = false,
                isOn = false,
                brightness = 50,
                temperature = 200
            )
        }
        emit(initialLights)

        // Start discovery with timeout
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            discoveryService.discoverLights().collect { discovered ->
                Log.d(TAG, "Discovered: ${discovered.serviceName} at ${discovered.ipAddress}")

                // Fetch light info and state
                val light = fetchLightDetails(discovered)
                if (light != null) {
                    discoveredLights[light.id] = light

                    // Save to preferences
                    preferencesRepository.saveLight(
                        SavedLight(
                            id = light.id,
                            name = light.name,
                            ipAddress = light.ipAddress,
                            port = light.port
                        )
                    )

                    // Merge discovered with saved (mark discovered as online)
                    val mergedLights = mergeLights(savedLightsList, discoveredLights.values.toList())
                    emit(mergedLights)
                }
            }
        }

        // After discovery timeout, do final check on saved lights that weren't discovered
        val finalLights = checkSavedLightsOnline(savedLightsList, discoveredLights)
        emit(finalLights)

        discoveryService.stopDiscovery()
    }.flowOn(Dispatchers.IO)

    /**
     * Fetch details for a discovered light
     */
    private suspend fun fetchLightDetails(discovered: DiscoveredLight): Light? {
        return try {
            val api = ElgatoApi.create(discovered.ipAddress, discovered.port)

            // Get accessory info for name/serial
            val infoResponse = api.getAccessoryInfo()
            val info = infoResponse.body()

            // Get current light state
            val stateResponse = api.getLightState()
            val state = stateResponse.body()?.lights?.firstOrNull()

            if (info != null) {
                Light(
                    id = info.serialNumber ?: discovered.serviceName,
                    name = info.displayName ?: info.productName ?: discovered.serviceName,
                    ipAddress = discovered.ipAddress,
                    port = discovered.port,
                    isOnline = true,
                    isOn = state?.on == 1,
                    brightness = state?.brightness ?: 50,
                    temperature = state?.temperature ?: 200
                )
            } else {
                // Fallback if accessory-info fails
                Light(
                    id = discovered.serviceName,
                    name = discovered.serviceName,
                    ipAddress = discovered.ipAddress,
                    port = discovered.port,
                    isOnline = true,
                    isOn = state?.on == 1,
                    brightness = state?.brightness ?: 50,
                    temperature = state?.temperature ?: 200
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch light details", e)
            null
        }
    }

    /**
     * Merge saved lights with discovered lights
     */
    private fun mergeLights(saved: List<SavedLight>, discovered: List<Light>): List<Light> {
        val discoveredIds = discovered.map { it.id }.toSet()
        val result = mutableListOf<Light>()

        // Add all discovered lights (they are online)
        result.addAll(discovered)

        // Add saved lights that weren't discovered (mark as offline)
        for (savedLight in saved) {
            if (savedLight.id !in discoveredIds) {
                result.add(
                    Light(
                        id = savedLight.id,
                        name = savedLight.name,
                        ipAddress = savedLight.ipAddress,
                        port = savedLight.port,
                        isOnline = false,
                        isOn = false,
                        brightness = 50,
                        temperature = 200
                    )
                )
            }
        }

        return result
    }

    /**
     * Check if saved lights (that weren't discovered via mDNS) are actually online
     */
    private suspend fun checkSavedLightsOnline(
        saved: List<SavedLight>,
        discovered: Map<String, Light>
    ): List<Light> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Light>()

        // Add all discovered lights
        result.addAll(discovered.values)

        // Check saved lights that weren't discovered
        for (savedLight in saved) {
            if (savedLight.id !in discovered) {
                // Try to connect directly
                val light = tryConnectToLight(savedLight)
                result.add(light)
            }
        }

        result
    }

    /**
     * Try to connect directly to a saved light
     */
    private suspend fun tryConnectToLight(saved: SavedLight): Light {
        return try {
            val api = ElgatoApi.create(saved.ipAddress, saved.port)
            val stateResponse = api.getLightState()

            if (stateResponse.isSuccessful) {
                val state = stateResponse.body()?.lights?.firstOrNull()
                Light(
                    id = saved.id,
                    name = saved.name,
                    ipAddress = saved.ipAddress,
                    port = saved.port,
                    isOnline = true,
                    isOn = state?.on == 1,
                    brightness = state?.brightness ?: 50,
                    temperature = state?.temperature ?: 200
                )
            } else {
                Light(
                    id = saved.id,
                    name = saved.name,
                    ipAddress = saved.ipAddress,
                    port = saved.port,
                    isOnline = false,
                    isOn = false,
                    brightness = 50,
                    temperature = 200
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to saved light ${saved.name}", e)
            Light(
                id = saved.id,
                name = saved.name,
                ipAddress = saved.ipAddress,
                port = saved.port,
                isOnline = false,
                isOn = false,
                brightness = 50,
                temperature = 200
            )
        }
    }

    /**
     * Refresh the state of a single light
     */
    suspend fun refreshLightState(light: Light): Light = withContext(Dispatchers.IO) {
        try {
            val api = ElgatoApi.create(light.ipAddress, light.port)
            val response = api.getLightState()

            if (response.isSuccessful) {
                val state = response.body()?.lights?.firstOrNull()
                light.copy(
                    isOnline = true,
                    isOn = state?.on == 1,
                    brightness = state?.brightness ?: light.brightness,
                    temperature = state?.temperature ?: light.temperature
                )
            } else {
                light.copy(isOnline = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh light state", e)
            light.copy(isOnline = false)
        }
    }

    /**
     * Toggle light power
     */
    suspend fun togglePower(light: Light): Light = withContext(Dispatchers.IO) {
        try {
            val api = ElgatoApi.create(light.ipAddress, light.port)
            val newState = !light.isOn
            val request = LightStateRequest.power(newState)
            val response = api.setLightState(request)

            if (response.isSuccessful) {
                light.copy(isOn = newState, isOnline = true)
            } else {
                light.copy(isOnline = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle power", e)
            light.copy(isOnline = false)
        }
    }

    /**
     * Set light power state
     */
    suspend fun setPower(light: Light, on: Boolean): Light = withContext(Dispatchers.IO) {
        try {
            val api = ElgatoApi.create(light.ipAddress, light.port)
            val request = LightStateRequest.power(on)
            val response = api.setLightState(request)

            if (response.isSuccessful) {
                light.copy(isOn = on, isOnline = true)
            } else {
                light.copy(isOnline = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set power", e)
            light.copy(isOnline = false)
        }
    }

    /**
     * Set light brightness
     */
    suspend fun setBrightness(light: Light, brightness: Int): Light = withContext(Dispatchers.IO) {
        val clampedBrightness = brightness.coerceIn(Light.MIN_BRIGHTNESS, Light.MAX_BRIGHTNESS)
        try {
            val api = ElgatoApi.create(light.ipAddress, light.port)
            val request = LightStateRequest.brightness(clampedBrightness)
            val response = api.setLightState(request)

            if (response.isSuccessful) {
                light.copy(brightness = clampedBrightness, isOnline = true)
            } else {
                light.copy(isOnline = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness", e)
            light.copy(isOnline = false)
        }
    }

    /**
     * Set light temperature
     */
    suspend fun setTemperature(light: Light, temperature: Int): Light = withContext(Dispatchers.IO) {
        val clampedTemp = temperature.coerceIn(Light.MIN_TEMPERATURE, Light.MAX_TEMPERATURE)
        try {
            val api = ElgatoApi.create(light.ipAddress, light.port)
            val request = LightStateRequest.temperature(clampedTemp)
            val response = api.setLightState(request)

            if (response.isSuccessful) {
                light.copy(temperature = clampedTemp, isOnline = true)
            } else {
                light.copy(isOnline = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set temperature", e)
            light.copy(isOnline = false)
        }
    }

    /**
     * Remove a light from saved list
     */
    suspend fun removeLight(lightId: String) {
        preferencesRepository.removeLight(lightId)
    }

    /**
     * Stop any ongoing discovery
     */
    fun stopDiscovery() {
        discoveryService.stopDiscovery()
    }
}
