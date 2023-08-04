package com.example.metricrunble

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import io.reactivex.rxjava3.core.Observable
import okhttp3.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.github.anastr.speedviewlib.SpeedView
import com.github.anastr.speedviewlib.Speedometer
import java.text.SimpleDateFormat
import java.util.Date


data class Reading(
    val id: Int,
    val apint: Int,
    val apext: Int,
    val talon: Int,
    val macAddress: String,
    val timeStamp: String
)

data class Averages(
    val average_apint: Double,
    val average_apext: Double
)

data class ChartValues(
    val values: List<Reading>
)

class DeviceActivity : Activity() {

    private val adcReadings = mutableListOf<AdcReading>()
    private val chartReadings = mutableListOf<Reading>()
    private lateinit var rxBleClient: RxBleClient
    private lateinit var adcValue1: TextView
    private lateinit var adcValue2: TextView
    private lateinit var averageApint: TextView
    private lateinit var averageApext: TextView
    private lateinit var chart: LineChart
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()
    private lateinit var apintSpeedometer: SpeedView
    private lateinit var apextSpeedometer: SpeedView
    private var userEmail: String = "" // Declare the userEmail variable here

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        adcValue1 = findViewById(R.id.adc_value_1)
        adcValue2 = findViewById(R.id.adc_value_2)
        averageApint = findViewById(R.id.average_apint)
        averageApext = findViewById(R.id.average_apext)
        apintSpeedometer = findViewById(R.id.apint_speedometer)
        apextSpeedometer = findViewById(R.id.apext_speedometer)
        chart = findViewById(R.id.lineChart)

         // Retrieve the userEmail from the Intent

        apintSpeedometer.setMaxSpeed(4095F)
        apintSpeedometer.unitUnderSpeedText = false

        apextSpeedometer.setMaxSpeed(4095F)

        rxBleClient = RxBleClient.create(this)
        val macAddress = intent.getStringExtra("mac_address") ?: return
        val device = rxBleClient.getBleDevice(macAddress)

        Timer().scheduleAtFixedRate(0, 1000) {
            fetchAndDisplayData(device)
            fetchAndDisplayChartData(device)
        }

        connectToDeviceAndReadAdcValues(device)
    }
    override fun onStart() {
        super.onStart()

        userEmail = intent.getStringExtra("userEmail") ?: "" // Retrieve the userEmail from the Intent

        // ...
    }
    private val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"
    private fun connectToDeviceAndReadAdcValues(device: RxBleDevice) {
        device.establishConnection(false)
            .flatMap { rxBleConnection ->
                rxBleConnection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    .repeatWhen { completed -> completed.delay(1, TimeUnit.SECONDS) }
                    .toObservable()
            }
            .subscribe(
                { characteristicValue ->
                    val macAddress = device.macAddress
                    val timeStamp = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(Date())
                    val buffer = ByteBuffer.wrap(characteristicValue).order(ByteOrder.LITTLE_ENDIAN)
                    val adcValues = IntArray(4) { buffer.int }
                    val newReading = AdcReading(adcValues[0], adcValues[1], adcValues[2], adcValues[3], macAddress, timeStamp, userEmail)
                    adcReadings.add(newReading)
                    Log.d("DeviceActivity", "adcReadings: $adcReadings")
                    if (adcReadings.size == 10) {
                        postDataToServer(adcReadings)
                        adcReadings.clear()
                    }

                    runOnUiThread {
                        adcValue1.text = adcValues[0].toString()
                        adcValue2.text = adcValues[1].toString()
                    }
                },
                { throwable ->
                    Log.e("DeviceActivity", "Error while setting up notifications", throwable)
                }
            )
    }

    private fun postDataToServer(adcReadings: List<AdcReading>) {
        val client = OkHttpClient()
        val jsonData = Gson().toJson(adcReadings)
        Log.d("DeviceActivity", "Posting data: $jsonData")
        val requestBody = jsonData.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("http://54.221.216.132/metricrun/update.php")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceActivity", "Error posting data", e)
            }

            override fun onResponse(call: Call, response: Response) {
                Log.i("DeviceActivity", "Server response body: ${response.body?.string()}")
                if (!response.isSuccessful) {
                    Log.e("DeviceActivity", "Unsuccessful response: ${response.message}")
                } else {
                    Log.i("DeviceActivity", "Successfully posted data")
                }
            }
        })
    }

    private fun fetchAndDisplayData(device: RxBleDevice) {
        val macAddress = device.macAddress

        val url = HttpUrl.Builder()
            .scheme("http")
            .host("54.221.216.132")
            .addPathSegment("metricrun")
            .addPathSegment("get_data.php")
            .addQueryParameter("macAddress", macAddress)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceActivity", "Error fetching data", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        val averages: Averages = gson.fromJson(body, Averages::class.java)

                        Log.d("DeviceActivity", "Averages from server: $averages")

                        runOnUiThread {
                            averageApint.text = averages.average_apint.toString()
                            averageApext.text = averages.average_apext.toString()

                            apintSpeedometer.speedTo(averages.average_apint.toFloat(), 2000)
                            apextSpeedometer.speedTo(averages.average_apext.toFloat(), 2000)
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.e("DeviceActivity", "Json parsing error", e)
                    }
                } else {
                    Log.e("DeviceActivity", "Server returned empty response body")
                }
            }
        })
    }


    private fun fetchAndDisplayChartData(device: RxBleDevice) {
        val macAddress = device.macAddress

        val url = HttpUrl.Builder()
            .scheme("http")
            .host("54.221.216.132")
            .addPathSegment("metricrun")
            .addPathSegment("get_chart.php")
            .addQueryParameter("macAddress", macAddress)
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceActivity", "Error fetching chart data", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        val chartValues: ChartValues = gson.fromJson(body, ChartValues::class.java)

                        Log.d("DeviceActivity", "Chart values from server: $chartValues")

                        runOnUiThread {
                            chartValues.values?.let {
                                displayChart(it)
                            }
                        }
                    } catch (e: JsonSyntaxException) {
                        Log.e("DeviceActivity", "Json parsing error", e)
                    }
                } else {
                    Log.e("DeviceActivity", "Server returned empty response body")
                }
            }
        })
    }


    private fun displayChart(readings: List<Reading>?) {
        val apintValues = mutableListOf<Entry>()
        val apextValues = mutableListOf<Entry>()

        readings?.let { list ->
            for (i in list.indices) {
                apintValues.add(Entry(i.toFloat(), list[i].apint.toFloat()))
                apextValues.add(Entry(i.toFloat(), list[i].apext.toFloat()))
            }
        }

        val apintDataSet = LineDataSet(apintValues, "APInt").apply {
            setColor(Color.RED)
        }
        val apextDataSet = LineDataSet(apextValues, "APExt").apply {
            setColor(Color.GREEN)
        }
        val data = LineData(apintDataSet, apextDataSet)
        chart.data = data
        chart.invalidate()
    }

}
