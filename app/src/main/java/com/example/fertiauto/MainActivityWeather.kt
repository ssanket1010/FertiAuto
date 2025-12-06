package com.example.fertiauto

import com.google.gson.annotations.SerializedName

object MainActivityWeather {

    data class WeatherResponse(
        @SerializedName("main") val main: Main,
        @SerializedName("wind") val wind: Wind,
        @SerializedName("rain") val rain: Map<String, Double>?
    )

    data class Main(
        @SerializedName("temp") val temp: Double,
        @SerializedName("humidity") val humidity: Int
    )

    data class Wind(
        @SerializedName("speed") val speed: Double
    )
}