package com.example.fertiauto

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class HomeFragment : Fragment() {

    interface WeatherAPI {
        @retrofit2.http.GET("weather")
        suspend fun getWeather(
            @retrofit2.http.Query("q") city: String,
            @retrofit2.http.Query("appid") apiKey: String,
            @retrofit2.http.Query("units") units: String = "metric"
        ): MainActivityWeather.WeatherResponse
    }

    // 30 static farming tips
    private val tips = listOf(
        "Check moisture and pH before scheduling fertilizer.",
        "Split nitrogen doses for better crop uptake.",
        "Apply fertilizer during early morning or late evening.",
        "Avoid fertilizing before heavy rain.",
        "Use compost to improve soil health.",
        "Calibrate fertilizer spreaders regularly.",
        "Test soil nutrients every season.",
        "Use slow-release fertilizers to avoid leaching.",
        "Monitor leaf color for nutrient deficiencies.",
        "Keep fertilizer stored dry and sealed.",
        "Use precision fertigation where possible.",
        "Avoid over-watering after fertilizing.",
        "Use organic manure to maintain soil microbes.",
        "Rotate crops to balance soil nutrients.",
        "Apply micronutrients when leaf symptoms appear.",
        "Check weather forecast before fertilizing.",
        "Avoid fertilizer application on waterlogged fields.",
        "Use cover crops to add natural nitrogen.",
        "Record fertilizer usage for future planning.",
        "Use drip irrigation for efficient nutrient delivery.",
        "Do not mix incompatible fertilizers.",
        "Maintain equipment to ensure uniform application.",
        "Use mulching to reduce nutrient loss.",
        "Avoid applying fertilizer to wet leaves.",
        "Improve soil aeration before fertilizing.",
        "Combine organic + chemical fertilizers for best results.",
        "Use foliar feeding only for quick corrections.",
        "Check EC and TDS of irrigation water.",
        "Store fertilizers away from heat and moisture.",
        "Do not overuse nitrogen—can reduce yield quality."
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val root = inflater.inflate(R.layout.fragment_home, container, false)

        // UI elements from the new XML
        val welcomeText = root.findViewById<TextView>(R.id.welcomeName)

        val tempText = root.findViewById<TextView>(R.id.homeTemp)
        val humidityText = root.findViewById<TextView>(R.id.homeHumidity)
        val windText = root.findViewById<TextView>(R.id.homeWind)
        val rainText = root.findViewById<TextView>(R.id.homeRain)

        val iconTemp = root.findViewById<ImageView>(R.id.iconTemp)
        val weatherCard = root.findViewById<CardView>(R.id.weatherCard)

        val tipText = root.findViewById<TextView>(R.id.homeTip)

        // Set welcome text
        welcomeText.text = "Welcome, Yash"

        // Show a random tip every time
        tipText.text = tips.random()

        // Retrofit setup
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(WeatherAPI::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getWeather(
                    "Dharwad,IN",
                    "055f827db89ca8b04be6260418b495b7"
                )

                val temp = response.main.temp
                val humidity = response.main.humidity
                val wind = response.wind.speed
                val rain = response.rain?.get("1h") ?: 0.0

                // Fill UI
                tempText.text = String.format("%.1f °C", temp)
                humidityText.text = "Humidity: $humidity %"
                windText.text = "Wind: $wind m/s"
                rainText.text = "Rain: $rain mm"

                // Change icon based on temperature
                when {
                    temp >= 30 -> iconTemp.setImageResource(R.drawable.ic_wb_sunny)
                    temp <= 15 -> iconTemp.setImageResource(R.drawable.ic_cloud)
                }

                // Background color logic
                val bgColor = when {
                    temp >= 30 -> "#FFF3E0" // warm orange
                    temp <= 15 -> "#E3F2FD" // cool blue
                    else -> "#FFFFFF"
                }
                weatherCard.setCardBackgroundColor(Color.parseColor(bgColor))

            } catch (e: Exception) {
                tempText.text = "Error loading weather"
                humidityText.text = ""
                windText.text = ""
                rainText.text = ""
            }
        }

        return root
    }
}