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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    // --------------------------------------
    // WEATHER API
    // --------------------------------------
    interface WeatherAPI {
        @retrofit2.http.GET("weather")
        suspend fun getWeather(
            @retrofit2.http.Query("q") city: String,
            @retrofit2.http.Query("appid") apiKey: String,
            @retrofit2.http.Query("units") units: String = "metric"
        ): MainActivityWeather.WeatherResponse
    }

    // --------------------------------------
    // FARMING TIPS
    // --------------------------------------
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

    // --------------------------------------
    // FIREBASE UPCOMING EVENTS
    // --------------------------------------
    private val scheduleRef = Firebase.database.getReference("schedule")

    private lateinit var rvUpcoming: RecyclerView
    private lateinit var upcomingAdapter: UpcomingAdapter
    private val upcomingList = mutableListOf<Schedule>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val root = inflater.inflate(R.layout.fragment_home, container, false)

        // UI elements
        val welcomeText = root.findViewById<TextView>(R.id.welcomeName)

        val tempText = root.findViewById<TextView>(R.id.homeTemp)
        val humidityText = root.findViewById<TextView>(R.id.homeHumidity)
        val windText = root.findViewById<TextView>(R.id.homeWind)
        val rainText = root.findViewById<TextView>(R.id.homeRain)

        val iconTemp = root.findViewById<ImageView>(R.id.iconTemp)
        val weatherCard = root.findViewById<CardView>(R.id.weatherCard)

        val tipText = root.findViewById<TextView>(R.id.homeTip)

        // Upcoming schedule RecyclerView
        rvUpcoming = root.findViewById(R.id.rvUpcoming)
        upcomingAdapter = UpcomingAdapter(upcomingList)
        rvUpcoming.layoutManager = LinearLayoutManager(requireContext())
        rvUpcoming.adapter = upcomingAdapter

        // Welcome text
        welcomeText.text = "Welcome, Yash"

        // Random tip
        tipText.text = tips.random()

        // --------------------------------------
        // WEATHER VIA RETROFIT
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

                tempText.text = String.format("%.1f °C", temp)
                humidityText.text = "Humidity: $humidity %"
                windText.text = "Wind: $wind m/s"
                rainText.text = "Rain: $rain mm"

                when {
                    temp >= 30 -> iconTemp.setImageResource(R.drawable.ic_wb_sunny)
                    temp <= 15 -> iconTemp.setImageResource(R.drawable.ic_cloud)
                }

                val bgColor = when {
                    temp >= 30 -> "#FFF3E0"
                    temp <= 15 -> "#E3F2FD"
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

        // --------------------------------------
        // LOAD UPCOMING SCHEDULES
        // --------------------------------------
        loadUpcomingSchedules()

        return root
    }

    private fun loadUpcomingSchedules() {
        scheduleRef.addValueEventListener(object : ValueEventListener {

            override fun onDataChange(snapshot: DataSnapshot) {

                val allSchedules = mutableListOf<Schedule>()

                for (c in snapshot.children) {
                    val map = c.value as? Map<*, *> ?: continue

                    val s = Schedule(
                        id = map["id"]?.toString() ?: "",
                        motor1 = (map["motor1"] as? Number)?.toInt() ?: 0,
                        motor2 = (map["motor2"] as? Number)?.toInt() ?: 0,
                        motor3 = (map["motor3"] as? Number)?.toInt() ?: 0,
                        year = (map["year"] as? Number)?.toInt() ?: 0,
                        month = (map["month"] as? Number)?.toInt() ?: 0,
                        day = (map["day"] as? Number)?.toInt() ?: 0,
                        hour = (map["hour"] as? Number)?.toInt() ?: 0,
                        minute = (map["minute"] as? Number)?.toInt() ?: 0,
                        repeat = map["repeat"]?.toString() ?: "Once",
                        status = map["status"]?.toString() ?: "PENDING"
                    )

                    allSchedules.add(s)
                }

                // Sort by time
                allSchedules.sortWith(
                    compareBy({ it.year }, { it.month }, { it.day }, { it.hour }, { it.minute })
                )

                // Filter upcoming events
                val now = Calendar.getInstance()

                val upcoming = allSchedules.filter { s ->
                    val c = Calendar.getInstance()
                    c.set(s.year, s.month - 1, s.day, s.hour, s.minute)
                    c.timeInMillis >= now.timeInMillis && s.status == "PENDING"
                }.take(3)

                upcomingList.clear()
                upcomingList.addAll(upcoming)
                upcomingAdapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // --------------------------------------
    // ADAPTER FOR UPCOMING EVENTS
    // --------------------------------------
    class UpcomingAdapter(private val items: List<Schedule>) :
        RecyclerView.Adapter<UpcomingAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvDate: TextView = v.findViewById(R.id.tvDate)
            val tvLevels: TextView = v.findViewById(R.id.tvLevels)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.upcoming_item, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = items[position]

            val cal = Calendar.getInstance()
            cal.set(s.year, s.month - 1, s.day, s.hour, s.minute)

            val fmt = SimpleDateFormat("dd MMM yyyy | HH:mm", Locale.getDefault())
            holder.tvDate.text = fmt.format(cal.time)

            holder.tvLevels.text =
                "M1:${s.motor1}  M2:${s.motor2}  M3:${s.motor3}"
        }
    }
}