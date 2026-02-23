package com.elgato.keylightcontroller.data.network

import com.elgato.keylightcontroller.data.model.AccessoryInfo
import com.elgato.keylightcontroller.data.model.LightStateRequest
import com.elgato.keylightcontroller.data.model.LightStateResponse
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT
import java.util.concurrent.TimeUnit

interface ElgatoApi {

    @GET("elgato/lights")
    suspend fun getLightState(): Response<LightStateResponse>

    @PUT("elgato/lights")
    suspend fun setLightState(@Body request: LightStateRequest): Response<LightStateResponse>

    @GET("elgato/accessory-info")
    suspend fun getAccessoryInfo(): Response<AccessoryInfo>

    companion object {
        private val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

        fun create(ipAddress: String, port: Int = 9123): ElgatoApi {
            return Retrofit.Builder()
                .baseUrl("http://$ipAddress:$port/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ElgatoApi::class.java)
        }
    }
}
