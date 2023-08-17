@file:Suppress("UNUSED_VARIABLE")

package com.example.metricrunble

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import com.google.gson.Gson
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleConnection
import com.polidea.rxandroidble2.RxBleDevice
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.scheduleAtFixedRate
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers

interface DeviceProvider {
    fun getDevice(): RxBleDevice?
}


class DeviceActivity : AppCompatActivity(), DeviceProvider {

    private var calibrated: Int? = null
    private val adcReadings = mutableListOf<AdcReading>()
    private lateinit var rxBleClient: RxBleClient
    private var userEmail: String = ""
    private lateinit var device: RxBleDevice
    private var isConnectionActive = false
    private val okHttpClient = OkHttpClient()
    private var rxBleConnection: RxBleConnection? = null

    override fun getDevice(): RxBleDevice? {
        return device
    }
    interface ServerResponseCallback {
        fun onResponse(response: String)
        fun onError(error: String)
    }

    class MyDialogFragment : DialogFragment() {
        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            return inflater.inflate(R.layout.calibrate_layout, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val dialog = super.onCreateDialog(savedInstanceState)
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
            return dialog
        }

        override fun onStart() {
            super.onStart()
            dialog?.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device)
        Log.d("DeviceActivity", "Calibration status: $calibrated")

        rxBleClient = RxBleClient.create(this)
        val macAddress = intent.getStringExtra("mac_address") ?: return
        device = rxBleClient.getBleDevice(macAddress)
        val imageView: ImageView = findViewById(R.id.imageView)
        imageView.setImageResource(R.drawable.top)
        Timer().scheduleAtFixedRate(0, 1000) {}

        val switch: Switch = findViewById(R.id.switch1)
        switch.setOnCheckedChangeListener { _, isChecked ->
            isConnectionActive = isChecked
            if (isChecked) {
                connectToDeviceAndReadAdcValues(device)
            }
        }
        val button = findViewById<Button>(R.id.myButton)
        button.setOnClickListener {
            val dialogFragment = MyDialogFragment()
            dialogFragment.show(supportFragmentManager, "MyDialogFragment")
        }
    }

    private fun showCalibrationDialog() {
        val dialogFragment = CalibrationDialogFragment()
        dialogFragment.show(supportFragmentManager, "calibrationDialog")
    }

    override fun onStart() {
        super.onStart()

        userEmail = intent.getStringExtra("userEmail") ?: ""
        fetchUserName()
        val macAddress = intent.getStringExtra("mac_address") ?: return
        val userEmail = intent.getStringExtra("userEmail") ?: return
        val calibrated = intent.getIntExtra("calibrated", 0)
        Log.d("DeviceActivity", "Calibration status in Device: $calibrated")
        fetchLastConnection(macAddress, userEmail)
        if (calibrated == 0) {
            showCalibrationDialog()
        }
    }

    private val CHARACTERISTIC_UUID = "beb5483e-36e1-4688-b7f5-ea07361b26a8"

    private fun connectToDeviceAndReadAdcValues(device: RxBleDevice) {
        val disposable = device.establishConnection(false)
        device.establishConnection(false)
            .flatMap { connection ->
                rxBleConnection = connection
                connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
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
                },
                { throwable ->
                    Log.e("DeviceActivity", "Error while setting up notifications", throwable)
                }
            )
    }

    internal fun readAdcValuesForCalibration(device: RxBleDevice, callback: ServerResponseCallback?) {
        Log.d("DeviceActivity", "Inside readAdcValuesForCalibration function")

        val stopSignal = Observable.timer(20, TimeUnit.SECONDS).publish()

        val connectionToUse: Observable<RxBleConnection> = rxBleConnection?.let {
            Observable.just(it)
        } ?: device.establishConnection(false).observeOn(AndroidSchedulers.mainThread())

        connectionToUse
            .flatMap { connection ->
                rxBleConnection = connection
                connection.readCharacteristic(UUID.fromString(CHARACTERISTIC_UUID))
                    .repeatWhen { completed -> completed.delay(1, TimeUnit.SECONDS) }
                    .toObservable()
                    .takeUntil(stopSignal)
            }
            .subscribe(
                { characteristicValue ->
                    // Procesamiento del valor de la característica
                    val buffer = ByteBuffer.wrap(characteristicValue).order(ByteOrder.LITTLE_ENDIAN)
                    val adcValues = IntArray(4) { buffer.int }
                    val macAddress = device.macAddress
                    val timeStamp = SimpleDateFormat("yyyy.MM.dd.HH.mm.ss", Locale.US).format(Date())
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
                    if (adcReadings.size == 10) {
                        postDataToServer(adcReadings)
                        adcReadings.clear()
                    }
                },
                { throwable ->
                    // Manejo de errores
                    Log.e("DeviceActivity", "Error reading ADC values", throwable)
                }
            )

        stopSignal.connect()
    }

    private fun postDataToServer(adcReadings: List<AdcReading>, callback: ServerResponseCallback? = null) {
        Log.d("DeviceActivity", "postDataToServer called with ${adcReadings.size} readings")
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
                callback?.onError(e.message ?: "Unknown error")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""
                Log.d("DeviceActivity", "Data posted successfully: $responseBody")
                if (!response.isSuccessful) {
                    Log.e("DeviceActivity", "Unsuccessful response: ${response.message}")
                    callback?.onError(response.message ?: "Unknown error")
                } else {
                    Log.i("DeviceActivity", "Successfully posted data")
                    callback?.onResponse(responseBody)
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
            .addPathSegment("get_date.php")
            .addQueryParameter("macAddress", macAddress)
            .addQueryParameter("userEmail", userEmail)
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
                            val lastConnectionTextView: TextView = findViewById(R.id.lastConnectionTextView)
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




