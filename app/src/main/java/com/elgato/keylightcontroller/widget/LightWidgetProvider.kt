package com.elgato.keylightcontroller.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.elgato.keylightcontroller.R
import com.elgato.keylightcontroller.data.model.LightSettings
import com.elgato.keylightcontroller.data.model.LightStateRequest
import com.elgato.keylightcontroller.data.model.LightStateResponse
import com.elgato.keylightcontroller.data.network.ElgatoApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LightWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_LIGHT = "com.elgato.keylightcontroller.ACTION_TOGGLE_LIGHT"
        const val EXTRA_WIDGET_ID = "widget_id"

        private const val PREFS_NAME = "light_widget_prefs"
        private const val PREF_PREFIX_LIGHT_ID = "widget_light_id_"
        private const val PREF_PREFIX_LIGHT_NAME = "widget_light_name_"
        private const val PREF_PREFIX_LIGHT_IP = "widget_light_ip_"
        private const val PREF_PREFIX_LIGHT_PORT = "widget_light_port_"

        fun saveLightForWidget(context: Context, appWidgetId: Int, lightId: String, lightName: String, lightIp: String, lightPort: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(PREF_PREFIX_LIGHT_ID + appWidgetId, lightId)
                putString(PREF_PREFIX_LIGHT_NAME + appWidgetId, lightName)
                putString(PREF_PREFIX_LIGHT_IP + appWidgetId, lightIp)
                putInt(PREF_PREFIX_LIGHT_PORT + appWidgetId, lightPort)
                apply()
            }
        }

        fun getLightForWidget(context: Context, appWidgetId: Int): WidgetLightInfo? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lightId = prefs.getString(PREF_PREFIX_LIGHT_ID + appWidgetId, null) ?: return null
            val lightName = prefs.getString(PREF_PREFIX_LIGHT_NAME + appWidgetId, "Studio Light") ?: "Studio Light"
            val lightIp = prefs.getString(PREF_PREFIX_LIGHT_IP + appWidgetId, null) ?: return null
            val lightPort = prefs.getInt(PREF_PREFIX_LIGHT_PORT + appWidgetId, 9123)
            return WidgetLightInfo(lightId, lightName, lightIp, lightPort)
        }

        fun deleteLightForWidget(context: Context, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove(PREF_PREFIX_LIGHT_ID + appWidgetId)
                remove(PREF_PREFIX_LIGHT_NAME + appWidgetId)
                remove(PREF_PREFIX_LIGHT_IP + appWidgetId)
                remove(PREF_PREFIX_LIGHT_PORT + appWidgetId)
                apply()
            }
        }

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val lightInfo = getLightForWidget(context, appWidgetId)

            val views = RemoteViews(context.packageName, R.layout.widget_light_toggle)

            if (lightInfo != null) {
                views.setTextViewText(R.id.widget_light_name, lightInfo.name)

                // Set up click intent for toggle
                val toggleIntent = Intent(context, LightWidgetProvider::class.java).apply {
                    action = ACTION_TOGGLE_LIGHT
                    putExtra(EXTRA_WIDGET_ID, appWidgetId)
                }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, appWidgetId, toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)

                // Fetch current state and update UI
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val api = ElgatoApi.create(lightInfo.ip, lightInfo.port)
                        val response = api.getLightState()
                        if (response.isSuccessful) {
                            val state = response.body()?.lights?.firstOrNull()
                            val isOn = state?.on == 1
                            withContext(Dispatchers.Main) {
                                views.setImageViewResource(
                                    R.id.widget_icon,
                                    if (isOn) R.drawable.ic_lightbulb_on else R.drawable.ic_lightbulb
                                )
                                views.setTextViewText(
                                    R.id.widget_status,
                                    if (isOn) "On • ${state?.brightness}%" else "Off"
                                )
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                views.setTextViewText(R.id.widget_status, "Offline")
                                appWidgetManager.updateAppWidget(appWidgetId, views)
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            views.setTextViewText(R.id.widget_status, "Offline")
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                }
            } else {
                views.setTextViewText(R.id.widget_light_name, "Not configured")
                views.setTextViewText(R.id.widget_status, "Tap to setup")
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            deleteLightForWidget(context, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        if (intent.action == ACTION_TOGGLE_LIGHT) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                toggleLight(context, appWidgetId)
            }
        }
    }

    private fun toggleLight(context: Context, appWidgetId: Int) {
        val lightInfo = getLightForWidget(context, appWidgetId) ?: return

        scope.launch {
            try {
                val api = ElgatoApi.create(lightInfo.ip, lightInfo.port)

                // Get current state
                val currentState = api.getLightState()
                val isCurrentlyOn = currentState.body()?.lights?.firstOrNull()?.on == 1

                // Toggle
                val newState = !isCurrentlyOn
                val request = LightStateRequest(
                    numberOfLights = 1,
                    lights = listOf(LightSettings(on = if (newState) 1 else 0, brightness = -1, temperature = -1))
                )
                api.setLightState(request)

                // Update widget
                withContext(Dispatchers.Main) {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    updateWidget(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                // Handle error silently, widget will show offline status on next update
            }
        }
    }
}

data class WidgetLightInfo(
    val id: String,
    val name: String,
    val ip: String,
    val port: Int
)
