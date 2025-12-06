package com.example.fertiauto

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlin.concurrent.thread
import java.text.SimpleDateFormat
import java.util.*

// ----------------------------
// MODEL CLASS FOR SCHEDULE
// ----------------------------
data class Schedule(
    val id: String = "",
    val motor1: Int = 0,
    val motor2: Int = 0,
    val motor3: Int = 0,
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val hour: Int = 0,
    val minute: Int = 0,
    val repeat: String = "Once",
    val status: String = "PENDING"
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "motor1" to motor1,
        "motor2" to motor2,
        "motor3" to motor3,
        "year" to year,
        "month" to month,
        "day" to day,
        "hour" to hour,
        "minute" to minute,
        "repeat" to repeat,
        "status" to status
    )
}

class ControlFragment : Fragment() {

    // Firebase references
    private val db = Firebase.database
    private val commandsRef = db.getReference("commands")
    private val scheduleRef = db.getReference("schedule")

    private val uiHandler = Handler(Looper.getMainLooper())
    private val DEBOUNCE_MS = 200L

    private var motor1Runnable: Runnable? = null
    private var motor2Runnable: Runnable? = null
    private var motor3Runnable: Runnable? = null

    // UI references
    private lateinit var statusText: TextView
    private lateinit var txtFlow: TextView

    private lateinit var pct1: TextView
    private lateinit var pct2: TextView
    private lateinit var pct3: TextView

    private lateinit var btnMotorToggle: Button
    private lateinit var btnValve1Toggle: Button
    private lateinit var btnValve2Toggle: Button

    // Scheduler UI: three seekbars
    private lateinit var scheduleSeek1: SeekBar
    private lateinit var scheduleSeek2: SeekBar
    private lateinit var scheduleSeek3: SeekBar
    private lateinit var schedulePct1: TextView
    private lateinit var schedulePct2: TextView
    private lateinit var schedulePct3: TextView
    private lateinit var spinnerRepeat: Spinner
    private lateinit var btnPickDate: Button
    private lateinit var btnPickTime: Button
    private lateinit var btnSaveSchedule: Button

    // Selected date/time (default -1 so 0:00 is allowed)
    private var selectedYear = 0
    private var selectedMonth = 0
    private var selectedDay = 0
    private var selectedHour = -1
    private var selectedMinute = -1

    // Recycler
    private lateinit var rvSchedules: RecyclerView
    private val schedules = mutableListOf<Schedule>()
    private lateinit var adapter: ScheduleAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        val root = inflater.inflate(R.layout.fragment_control, container, false)

        // ---- Motor & Valve UI Binding ----
        val seek1 = root.findViewById<SeekBar>(R.id.seekMotor1)
        val seek2 = root.findViewById<SeekBar>(R.id.seekMotor2)
        val seek3 = root.findViewById<SeekBar>(R.id.seekMotor3)

        pct1 = root.findViewById(R.id.pct1)
        pct2 = root.findViewById(R.id.pct2)
        pct3 = root.findViewById(R.id.pct3)

        statusText = root.findViewById(R.id.statusText)
        txtFlow = root.findViewById(R.id.txtFlow)

        btnMotorToggle = root.findViewById(R.id.btnMotorToggle)
        btnValve1Toggle = root.findViewById(R.id.btnValve1Toggle)
        btnValve2Toggle = root.findViewById(R.id.btnValve2Toggle)

        // Update top percent labels
        updatePctLabel(pct1, seek1.progress, seek1.max)
        updatePctLabel(pct2, seek2.progress, seek2.max)
        updatePctLabel(pct3, seek3.progress, seek3.max)

        // -------------------------
        // MOTOR TOGGLE CONTROLS
        // -------------------------
        btnMotorToggle.setOnClickListener {
            val newState = if (btnMotorToggle.text.contains("OFF")) "ON" else "OFF"
            updateToggleButton(btnMotorToggle, "Motor", newState)
            writeString("motorSwitch", newState)
        }

        btnValve1Toggle.setOnClickListener {
            val newState = if (btnValve1Toggle.text.contains("OFF")) "ON" else "OFF"
            updateToggleButton(btnValve1Toggle, "Valve1", newState)
            writeString("valve1", newState)
        }

        btnValve2Toggle.setOnClickListener {
            val newState = if (btnValve2Toggle.text.contains("OFF")) "ON" else "OFF"
            updateToggleButton(btnValve2Toggle, "Valve2", newState)
            writeString("valve2", newState)
        }

        // -------------------------
        // SEEKBAR MOTOR SPEEDS (top)
        // -------------------------
        seek1.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
                updatePctLabel(pct1, v, seek1.max)
                motor1Runnable?.let { uiHandler.removeCallbacks(it) }
                motor1Runnable = Runnable { writeNumber("motor1", v) }
                uiHandler.postDelayed(motor1Runnable!!, DEBOUNCE_MS)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seek2.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
                updatePctLabel(pct2, v, seek2.max)
                motor2Runnable?.let { uiHandler.removeCallbacks(it) }
                motor2Runnable = Runnable { writeNumber("motor2", v) }
                uiHandler.postDelayed(motor2Runnable!!, DEBOUNCE_MS)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        seek3.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
                updatePctLabel(pct3, v, seek3.max)
                motor3Runnable?.let { uiHandler.removeCallbacks(it) }
                motor3Runnable = Runnable {
                    // send numeric motor3 value (0..100)
                    writeNumber("motor3", v)
                }
                uiHandler.postDelayed(motor3Runnable!!, DEBOUNCE_MS)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        // -------------------------
        // FLOW SENSOR LISTENER
        // -------------------------
        commandsRef.child("Flow").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(Double::class.java) ?: 0.0
                txtFlow.text = "Flow: %.2f L/min".format(value)
            }
            override fun onCancelled(error: DatabaseError) {
                txtFlow.text = "Flow: Error"
            }
        })

        // -------------------------
        // SCHEDULER UI BINDINGS (seekbars + date/time)
        // -------------------------
        scheduleSeek1 = root.findViewById(R.id.scheduleSeek1)
        scheduleSeek2 = root.findViewById(R.id.scheduleSeek2)
        scheduleSeek3 = root.findViewById(R.id.scheduleSeek3)

        schedulePct1 = root.findViewById(R.id.schedulePct1)
        schedulePct2 = root.findViewById(R.id.schedulePct2)
        schedulePct3 = root.findViewById(R.id.schedulePct3)

        spinnerRepeat = root.findViewById(R.id.spinnerRepeat)
        btnPickDate = root.findViewById(R.id.btnPickDate)
        btnPickTime = root.findViewById(R.id.btnPickTime)
        btnSaveSchedule = root.findViewById(R.id.btnSaveSchedule)

        // Init schedule seekbars' labels
        scheduleSeek1.setOnSeekBarChangeListener(labelUpdater(schedulePct1, 255))
        scheduleSeek2.setOnSeekBarChangeListener(labelUpdater(schedulePct2, 255))
        scheduleSeek3.setOnSeekBarChangeListener(labelUpdater(schedulePct3, 100))

        spinnerRepeat.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Once", "Daily", "Weekly")
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // -------------------------
        // MATERIAL DATE PICKER
        // -------------------------
        btnPickDate.setOnClickListener {
            val picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date")
                .build()

            picker.addOnPositiveButtonClickListener { selection ->
                val cal = Calendar.getInstance()
                cal.timeInMillis = selection

                selectedYear = cal.get(Calendar.YEAR)
                selectedMonth = cal.get(Calendar.MONTH) + 1
                selectedDay = cal.get(Calendar.DAY_OF_MONTH)

                btnPickDate.text = "Date: $selectedDay-$selectedMonth-$selectedYear"
            }

            picker.show(parentFragmentManager, "DATE_PICK")
        }

        // -------------------------
        // MATERIAL TIME PICKER
        // -------------------------
        btnPickTime.setOnClickListener {
            val picker = MaterialTimePicker.Builder()
                .setTitleText("Select Time")
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                btnPickTime.text = "Time: %02d:%02d".format(selectedHour, selectedMinute)
            }

            picker.show(parentFragmentManager, "TIME_PICK")
        }

        btnSaveSchedule.setOnClickListener { saveSchedule() }

        // -------------------------
        // RECYCLER VIEW FOR SCHEDULES
        // -------------------------
        rvSchedules = root.findViewById(R.id.rvSchedules)
        adapter = ScheduleAdapter(
            schedules,
            onDelete = { deleteSchedule(it) },
            onRunNow = { runNow(it) }
        )
        rvSchedules.layoutManager = LinearLayoutManager(requireContext())
        rvSchedules.adapter = adapter

        observeSchedules()

        return root
    }

    // -------------------------
    // SAVE SCHEDULE TO FIREBASE
    // -------------------------
    private fun saveSchedule() {

        if (selectedYear == 0) {
            statusText.text = "Please select date"
            return
        }

        if (selectedHour < 0 || selectedMinute < 0) {
            statusText.text = "Please select time"
            return
        }

        val repeat = spinnerRepeat.selectedItem.toString()

        val id = scheduleRef.push().key ?: System.currentTimeMillis().toString()

        val s = Schedule(
            id = id,
            motor1 = scheduleSeek1.progress,
            motor2 = scheduleSeek2.progress,
            motor3 = scheduleSeek3.progress,
            year = selectedYear,
            month = selectedMonth,
            day = selectedDay,
            hour = selectedHour,
            minute = selectedMinute,
            repeat = repeat,
            status = "PENDING"
        )

        statusText.text = "Saving schedule..."

        scheduleRef.child(id).setValue(s.toMap())
            .addOnSuccessListener { statusText.text = "Schedule saved" }
            .addOnFailureListener { statusText.text = "Failed to save" }
    }

    // -------------------------
    // OBSERVE SCHEDULE LIST
    // -------------------------
    private fun observeSchedules() {
        scheduleRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                schedules.clear()
                for (c in snapshot.children) {
                    val map = c.value as? Map<*, *> ?: continue

                    // safe extraction with defaults
                    val id = map["id"]?.toString() ?: c.key ?: ""
                    val m1 = (map["motor1"] as? Number)?.toInt() ?: 0
                    val m2 = (map["motor2"] as? Number)?.toInt() ?: 0
                    val m3 = (map["motor3"] as? Number)?.toInt() ?: 0
                    val year = (map["year"] as? Number)?.toInt() ?: 0
                    val month = (map["month"] as? Number)?.toInt() ?: 0
                    val day = (map["day"] as? Number)?.toInt() ?: 0
                    val hour = (map["hour"] as? Number)?.toInt() ?: 0
                    val minute = (map["minute"] as? Number)?.toInt() ?: 0
                    val repeat = map["repeat"]?.toString() ?: "Once"
                    val status = map["status"]?.toString() ?: "PENDING"

                    val s = Schedule(
                        id = id,
                        motor1 = m1,
                        motor2 = m2,
                        motor3 = m3,
                        year = year,
                        month = month,
                        day = day,
                        hour = hour,
                        minute = minute,
                        repeat = repeat,
                        status = status
                    )
                    schedules.add(s)
                }

                schedules.sortWith(
                    compareBy({ it.year }, { it.month }, { it.day }, { it.hour }, { it.minute })
                )

                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                statusText.text = "Schedule load failed"
            }
        })
    }

    // -------------------------
    // DELETE SCHEDULE
    // -------------------------
    private fun deleteSchedule(s: Schedule) {
        scheduleRef.child(s.id).removeValue()
            .addOnSuccessListener { statusText.text = "Schedule deleted" }
            .addOnFailureListener { statusText.text = "Delete failed" }
    }

    // -------------------------
    // RUN NOW (manual execution)
    // -------------------------
    private fun runNow(s: Schedule) {
        statusText.text = "Running scheduled motors..."

        thread {
            // Write numeric motor levels to firebase
            commandsRef.child("motor1").setValue(s.motor1)
            commandsRef.child("motor2").setValue(s.motor2)
            commandsRef.child("motor3").setValue(s.motor3)
                .addOnSuccessListener {
                    uiHandler.post { statusText.text = "Executed scheduled motors" }
                }
                .addOnFailureListener {
                    uiHandler.post { statusText.text = "Execution failed" }
                }
        }

        if (s.repeat == "Once") {
            scheduleRef.child(s.id).child("status").setValue("DONE")
        }
    }

    // -------------------------
    // HELPERS
    // -------------------------
    private fun updatePctLabel(tv: TextView, v: Int, max: Int) {
        val percent = ((v.toFloat() / max) * 100).toInt()
        tv.text = "$percent%"
    }

    private fun labelUpdater(tv: TextView, max: Int)
            = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(sb: SeekBar?, v: Int, f: Boolean) {
            val pct = ((v.toFloat() / max) * 100).toInt()
            tv.text = "$pct%"
        }
        override fun onStartTrackingTouch(sb: SeekBar?) {}
        override fun onStopTrackingTouch(sb: SeekBar?) {}
    }

    @SuppressLint("NewApi")
    private fun updateToggleButton(btn: Button, label: String, state: String) {
        if (state == "ON") {
            btn.text = "$label ON"
            btn.backgroundTintList =
                resources.getColorStateList(android.R.color.holo_green_light, null)
        } else {
            btn.text = "$label OFF"
            btn.backgroundTintList =
                resources.getColorStateList(android.R.color.holo_red_light, null)
        }
    }

    private fun writeNumber(key: String, v: Int) {
        statusText.text = "Sending $key=$v..."
        thread {
            commandsRef.child(key).setValue(v)
                .addOnSuccessListener { uiHandler.post { statusText.text = "Updated" } }
                .addOnFailureListener { uiHandler.post { statusText.text = "Error" } }
        }
    }

    private fun writeString(key: String, value: String) {
        statusText.text = "Sending $key=$value..."
        thread {
            commandsRef.child(key).setValue(value)
                .addOnSuccessListener { uiHandler.post { statusText.text = "Updated" } }
                .addOnFailureListener { uiHandler.post { statusText.text = "Error" } }
        }
    }

    // -------------------------
    // RECYCLER ADAPTER
    // -------------------------
    inner class ScheduleAdapter(
        private val items: List<Schedule>,
        private val onDelete: (Schedule) -> Unit,
        private val onRunNow: (Schedule) -> Unit
    ) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvMotor: TextView = v.findViewById(R.id.tvMotor)
            val tvWhen: TextView = v.findViewById(R.id.tvWhen)
            val tvRepeat: TextView = v.findViewById(R.id.tvRepeat)
            val tvLevels: TextView = v.findViewById(R.id.tvLevels)
            val btnRunNow: Button = v.findViewById(R.id.btnRunNow)
            val btnDelete: Button = v.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.schedule_item, parent, false)
            return ViewHolder(v)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val s = items[position]

            holder.tvMotor.text = "Schedule ${position + 1}"
            holder.tvWhen.text = formatDate(s)
            holder.tvRepeat.text = "Repeat: ${s.repeat}"

            holder.tvLevels.text = "M1:${pct(s.motor1, 255)}%   M2:${pct(s.motor2, 255)}%   M3:${pct(s.motor3, 100)}%"

            holder.btnRunNow.setOnClickListener { onRunNow(s) }
            holder.btnDelete.setOnClickListener { onDelete(s) }
        }

        private fun formatDate(s: Schedule): String {
            val cal = Calendar.getInstance()
            cal.set(s.year, s.month - 1, s.day, s.hour, s.minute)
            val fmt = SimpleDateFormat("yyyy-MM-dd | HH:mm", Locale.getDefault())
            return fmt.format(cal.time)
        }

        private fun pct(v:Int, max:Int) = ((v.toFloat() / max) * 100).toInt()
    }
}