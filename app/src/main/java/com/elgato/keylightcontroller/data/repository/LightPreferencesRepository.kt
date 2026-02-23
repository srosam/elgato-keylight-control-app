package com.elgato.keylightcontroller.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.elgato.keylightcontroller.data.model.SavedLight
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lights_preferences")

/**
 * Repository for persisting discovered lights across app sessions
 */
class LightPreferencesRepository(private val context: Context) {

    private val gson = Gson()

    companion object {
        private val SAVED_LIGHTS_KEY = stringPreferencesKey("saved_lights")
    }

    /**
     * Get all saved lights as a Flow
     */
    val savedLights: Flow<List<SavedLight>> = context.dataStore.data.map { preferences ->
        val json = preferences[SAVED_LIGHTS_KEY] ?: "[]"
        try {
            val type = object : TypeToken<List<SavedLight>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Save a new light or update existing one
     */
    suspend fun saveLight(light: SavedLight) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SAVED_LIGHTS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<SavedLight>>() {}.type
            val lights: MutableList<SavedLight> = try {
                gson.fromJson(currentJson, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Remove existing light with same ID and add the new one
            lights.removeAll { it.id == light.id }
            lights.add(light)

            preferences[SAVED_LIGHTS_KEY] = gson.toJson(lights)
        }
    }

    /**
     * Save multiple lights at once
     */
    suspend fun saveLights(newLights: List<SavedLight>) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SAVED_LIGHTS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<SavedLight>>() {}.type
            val lights: MutableList<SavedLight> = try {
                gson.fromJson(currentJson, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            // Update or add new lights
            for (newLight in newLights) {
                lights.removeAll { it.id == newLight.id }
                lights.add(newLight)
            }

            preferences[SAVED_LIGHTS_KEY] = gson.toJson(lights)
        }
    }

    /**
     * Remove a light from saved list
     */
    suspend fun removeLight(lightId: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SAVED_LIGHTS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<SavedLight>>() {}.type
            val lights: MutableList<SavedLight> = try {
                gson.fromJson(currentJson, type) ?: mutableListOf()
            } catch (e: Exception) {
                mutableListOf()
            }

            lights.removeAll { it.id == lightId }
            preferences[SAVED_LIGHTS_KEY] = gson.toJson(lights)
        }
    }

    /**
     * Clear all saved lights
     */
    suspend fun clearAllLights() {
        context.dataStore.edit { preferences ->
            preferences[SAVED_LIGHTS_KEY] = "[]"
        }
    }
}
