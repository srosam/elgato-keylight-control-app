package com.elgato.keylightcontroller.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elgato.keylightcontroller.data.model.Light
import com.elgato.keylightcontroller.data.repository.LightRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI State for the home screen
 */
data class LightUiState(
    val lights: List<Light> = emptyList(),
    val isScanning: Boolean = false,
    val hasOfflineLights: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel for managing light state and operations
 */
class LightViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LightRepository(application)

    private val _uiState = MutableStateFlow(LightUiState())
    val uiState: StateFlow<LightUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null

    init {
        // Start scanning on launch
        scanForLights()
    }

    /**
     * Scan for lights on the network
     */
    fun scanForLights() {
        // Cancel any existing scan
        scanJob?.cancel()

        scanJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isScanning = true,
                errorMessage = null
            )

            try {
                repository.discoverAndMergeLights().collect { lights ->
                    val hasOffline = lights.any { !it.isOnline }
                    _uiState.value = _uiState.value.copy(
                        lights = lights,
                        hasOfflineLights = hasOffline
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to scan: ${e.message}"
                )
            } finally {
                _uiState.value = _uiState.value.copy(isScanning = false)
            }
        }
    }

    /**
     * Toggle light power on/off
     */
    fun togglePower(light: Light) {
        viewModelScope.launch {
            val updatedLight = repository.togglePower(light)
            updateLightInState(updatedLight)
        }
    }

    /**
     * Set power state explicitly
     */
    fun setPower(light: Light, on: Boolean) {
        viewModelScope.launch {
            val updatedLight = repository.setPower(light, on)
            updateLightInState(updatedLight)
        }
    }

    /**
     * Set brightness (0-100)
     */
    fun setBrightness(light: Light, brightness: Int) {
        viewModelScope.launch {
            val updatedLight = repository.setBrightness(light, brightness)
            updateLightInState(updatedLight)
        }
    }

    /**
     * Set color temperature (143-344)
     */
    fun setTemperature(light: Light, temperature: Int) {
        viewModelScope.launch {
            val updatedLight = repository.setTemperature(light, temperature)
            updateLightInState(updatedLight)
        }
    }

    /**
     * Refresh state of a single light
     */
    fun refreshLight(light: Light) {
        viewModelScope.launch {
            val updatedLight = repository.refreshLightState(light)
            updateLightInState(updatedLight)
        }
    }

    /**
     * Remove a light from the saved list
     */
    fun removeLight(light: Light) {
        viewModelScope.launch {
            repository.removeLight(light.id)
            val currentLights = _uiState.value.lights.toMutableList()
            currentLights.removeAll { it.id == light.id }
            _uiState.value = _uiState.value.copy(
                lights = currentLights,
                hasOfflineLights = currentLights.any { !it.isOnline }
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Update a single light in the state
     */
    private fun updateLightInState(updatedLight: Light) {
        val currentLights = _uiState.value.lights.toMutableList()
        val index = currentLights.indexOfFirst { it.id == updatedLight.id }
        if (index >= 0) {
            currentLights[index] = updatedLight
            _uiState.value = _uiState.value.copy(
                lights = currentLights,
                hasOfflineLights = currentLights.any { !it.isOnline }
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopDiscovery()
    }
}
