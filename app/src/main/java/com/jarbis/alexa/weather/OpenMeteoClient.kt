package com.jarbis.alexa.weather

import com.jarbis.alexa.commands.WeatherSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@Serializable
data class GeoResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("admin1") val region: String? = null,
)

@Serializable
data class GeoResponse(val results: List<GeoResult>? = null)

@Serializable
data class CurrentWeather(
    @SerialName("temperature_2m") val temperatureC: Double,
    @SerialName("apparent_temperature") val apparentC: Double,
    @SerialName("weather_code") val wmoCode: Int,
)

@Serializable
data class ForecastResponse(val current: CurrentWeather? = null)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,apparent_temperature,weather_code",
        @Query("forecast_days") days: Int = 1,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("format") format: String = "json",
        @Query("language") language: String = "es",
    ): GeoResponse
}

/** Cliente de Open-Meteo (sin API key). Resuelve ciudad y devuelve clima actual. */
class OpenMeteoWeatherClient {

    private val forecastApi: OpenMeteoApi
    private val geocodingApi: GeocodingApi

    init {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
        fun retrofit(base: String): Retrofit = Retrofit.Builder()
            .baseUrl(base)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        forecastApi = retrofit("https://api.open-meteo.com/").create(OpenMeteoApi::class.java)
        geocodingApi = retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java)
    }

    suspend fun currentWeather(city: String): WeatherSummary? {
        val geo = geocodingApi.search(city).results?.firstOrNull() ?: return null
        val current = forecastApi.forecast(geo.latitude, geo.longitude).current ?: return null
        return WeatherSummary(
            city = geo.name,
            temperatureC = current.temperatureC,
            apparentC = current.apparentC,
            wmoCode = current.wmoCode,
        )
    }
}