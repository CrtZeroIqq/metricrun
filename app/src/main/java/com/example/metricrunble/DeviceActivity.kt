@file:Suppress("UNUSED_VARIABLE")

package com.example.metricrunble

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.Switch
import com.google.gson.Gson
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import okhttp3.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import android.widget.TextView
import org.json.JSONException
import org.json.JSONObject


class DeviceActivity : Activity() {

    private val adcReadings = mutableListOf<AdcReading>()
    private lateinit var rxBleClient: RxBleClient
    private var userEmail: String = "" // Declare the userEmail variable here
    private lateinit var device: RxBleDevice
    private var isConnectionActive = false
    private val okHttpClient = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)

        rxBleClient = RxBleClient.create(this)
        val macAddress = intent.getStringExtra("mac_address") ?: return
        device = rxBleClient.getBleDevice(macAddress)
        val imageView: ImageView = findViewById(R.id.imageView)
        imageView.setImageResource(R.drawable.top)
        Timer().scheduleAtFixedRate(0, 1000) {

        }
        val switch: Switch = findViewById(R.id.switch1)
        switch.setOnCheckedChangeListener { _, isChecked ->
            isConnectionActive = isChecked
            if (isChecked) {
                connectToDeviceAndReadAdcValues(device)
            }
        }


    }

    override fun onStart() {
        super.onStart()

        userEmail =
            intent.getStringExtra("userEmail") ?: "" // Retrieve the userEmail from the Intent
        fetchUserName()
        val macAddress = intent.getStringExtra("mac_address") ?: return
        val userEmail = intent.getStringExtra("userEmail") ?: return
        fetchLastConnection(macAddress, userEmail)
        // ...
    }

    private val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"

    private fun connectToDeviceAndReadAdcValues(device: RxBleDevice) {
        val disposable = device.establishConnection(false)
        device.establishConnection(false)
            .flatMap { rxBleConnection ->
                rxBleConnection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    .repeatWhen { completed -> completed.delay(1, TimeUnit.SECONDS) }
                    .toObservable()
            }
            .subscribe(
                { characteristicValue ->
                    if (!isConnectionActive) return@subscribe
                    val macAddress = device.macAddress
                    val timeStamp = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss",Locale.US).format(Date())
                    val buffer = ByteBuffer.wrap(characteristicValue).order(ByteOrder.LITTLE_ENDIAN)
                    val adcValues = IntArray(4) { buffer.int }
                    val newReading = AdcReading(
                        adcValues[0],
                        adcValues[1],
                        adcValues[2],
                        adcValues[3],
                        macAddress,
                        timeStamp,
                        userEmail
                    )
                    adcReadings.add(newReading)
                    Log.d("DeviceActivity", "adcReadings: $adcReadings")
                    if (adcReadings.size == 10) {
                        postDataToServer(adcReadings)
                        adcReadings.clear()
                    }

                    runOnUiThread {

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
    private fun fetchUserName() {
        val url = "http://54.221.216.132/metricrun/get_username.php?email=$userEmail"
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .build()
        Log.d("fetchUserName", "URL: $url")
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("DeviceActivity", "Error fetching username", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val responseString = response.body?.string()
                if (response.isSuccessful && responseString != null) {
                    val jsonObject = JSONObject(responseString)
                    val username = jsonObject.getString("username")
                    runOnUiThread {
                        // Actualiza el TextView con el nombre de usuario
                        val userNameTextView = findViewById<TextView>(R.id.userName)
                        userNameTextView.text = username
                        Log.i("DeviceActivity", "Username: $username")
                    }
                } else {
                    Log.e("DeviceActivity", "Unsuccessful response: ${response.message}")
                }
            }
        })
    }

    private fun fetchLastConnection(macAddress: String, userEmail: String) {
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("54.221.216.132")
            .addPathSegment("metricrun")
            .addPathSegment("get_date.php") // Cambia esto a la ruta correcta de tu archivo PHP
            .addQueryParameter("macAddress", macAddress)
            .addQueryParameter("userEmail", userEmail) // Asegúrate de pasar el correo electrónico del usuario logueado
            .build()

        val request = Request.Builder()
            .url(url)
            .build()

        okHttpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PrincipalActivity", "Error fetching last connection", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (body != null) {
                    try {
                        val jsonObject = JSONObject(body)
                        val lastConnection = jsonObject.getString("last_connection")

                        runOnUiThread {
                            val lastConnectionTextView: TextView = findViewById(R.id.lastConnectionTextView) // Cambia esto al ID correcto de tu TextView
                            lastConnectionTextView.text = "$lastConnection"
                        }
                    } catch (e: JSONException) {
                        Log.e("PrincipalActivity", "Json parsing error", e)
                    }
                } else {
                    Log.e("PrincipalActivity", "Server returned empty response body")
                }
            }
        })
    }


}







