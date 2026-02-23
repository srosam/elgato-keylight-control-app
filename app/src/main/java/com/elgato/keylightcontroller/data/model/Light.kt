package com.elgato.keylightcontroller.data.model

import com.google.gson.annotations.SerializedName

/**
 * Represents an Elgato Key Light device
 */
data class Light(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int = 9123,
    val isOnline: Boolean = true,
    val isOn: Boolean = false,
    val brightness: Int = 50,      // 0-100
    val temperature: Int = 200     // 143-344 (7000K-2900K inverse)
) {
    /**
     * Convert API temperature value to Kelvin for display
     * API uses 143 (cool/7000K) to 344 (warm/2900K)
     */
    val temperatureKelvin: Int
        get() = ((1000000.0 / (temperature + 60)).toInt() / 50) * 50  // Round to nearest 50K

    companion object {
        const val MIN_TEMPERATURE = 143  // 7000K (cool)
        const val MAX_TEMPERATURE = 344  // 2900K (warm)
        const val MIN_BRIGHTNESS = 0
        const val MAX_BRIGHTNESS = 100

        /**
         * Convert Kelvin to API temperature value
         */
        fun kelvinToApiTemp(kelvin: Int): Int {
            return ((1000000.0 / kelvin) - 60).toInt().coerceIn(MIN_TEMPERATURE, MAX_TEMPERATURE)
        }
    }
}

/**
 * API response for GET /elgato/lights
 */
data class LightStateResponse(
    @SerializedName("numberOfLights")
    val numberOfLights: Int,
    @SerializedName("lights")
    val lights: List<LightSettings>
)

/**
 * Individual light settings from API
 */
data class LightSettings(
    @SerializedName("on")
    val on: Int,           // 0 or 1
    @SerializedName("brightness")
    val brightness: Int,   // 0-100
    @SerializedName("temperature")
    val temperature: Int   // 143-344
)

/**
 * API request for PUT /elgato/lights
 */
data class LightStateRequest(
    @SerializedName("numberOfLights")
    val numberOfLights: Int = 1,
    @SerializedName("lights")
    val lights: List<LightSettings>
) {
    companion object {
        fun create(on: Boolean? = null, brightness: Int? = null, temperature: Int? = null): LightStateRequest {
            val settings = LightSettings(
                on = if (on == true) 1 else if (on == false) 0 else -1,
                brightness = brightness ?: -1,
                temperature = temperature ?: -1
            )
            return LightStateRequest(lights = listOf(settings))
        }

        fun power(on: Boolean): LightStateRequest {
            return LightStateRequest(
                lights = listOf(LightSettings(on = if (on) 1 else 0, brightness = -1, temperature = -1))
            )
        }

        fun brightness(value: Int): LightStateRequest {
            return LightStateRequest(
                lights = listOf(LightSettings(on = -1, brightness = value, temperature = -1))
            )
        }

        fun temperature(value: Int): LightStateRequest {
            return LightStateRequest(
                lights = listOf(LightSettings(on = -1, brightness = -1, temperature = value))
            )
        }

        fun full(on: Boolean, brightness: Int, temperature: Int): LightStateRequest {
            return LightStateRequest(
                lights = listOf(LightSettings(on = if (on) 1 else 0, brightness = brightness, temperature = temperature))
            )
        }
    }
}

/**
 * API response for GET /elgato/accessory-info
 */
data class AccessoryInfo(
    @SerializedName("productName")
    val productName: String?,
    @SerializedName("hardwareBoardType")
    val hardwareBoardType: Int?,
    @SerializedName("firmwareBuildNumber")
    val firmwareBuildNumber: Int?,
    @SerializedName("firmwareVersion")
    val firmwareVersion: String?,
    @SerializedName("serialNumber")
    val serialNumber: String?,
    @SerializedName("displayName")
    val displayName: String?
)

/**
 * Serializable data for storing lights in preferences
 */
data class SavedLight(
    val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int
)
