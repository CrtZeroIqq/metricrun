package com.example.metricrunble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.metricrunble.ui.theme.MetricRunBLETheme
import com.polidea.rxandroidble2.RxBleClient
import com.polidea.rxandroidble2.RxBleDevice
import com.polidea.rxandroidble2.scan.ScanResult
import com.polidea.rxandroidble2.scan.ScanSettings
import io.reactivex.disposables.Disposable
import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.example.metricrunble.R
import android.provider.Settings
import android.net.Uri

class MainActivity : ComponentActivity() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 123
    }

    private var scanDisposable: Disposable? = null
    private var connectionDisposable: Disposable? = null
    private val deviceList = mutableStateListOf<ScanResult>()
    private lateinit var rxBleClient: RxBleClient
    private var userEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        rxBleClient = RxBleClient.create(this)
        checkBluetoothAvailability()
        userEmail = intent.getStringExtra("userEmail") ?: ""
        setContent {
            MetricRunBLETheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logometric),
                                contentDescription = null,
                                modifier = Modifier
                                    .height(200.dp)
                                    .align(Alignment.CenterHorizontally)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { startScan() },
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text(text = "Buscar Dispositivo", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    val intent = Intent(this@MainActivity, PrincipalActivity::class.java)
                                    startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors()
                            ) {
                                Text(text = "Quiero ver mis datos", color = Color.White)
                            }

                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(deviceList) { scanResult ->
                                    val deviceName = scanResult.bleDevice.name ?: "Unknown Device"
                                    val deviceMacAddress = scanResult.bleDevice.macAddress
                                    val deviceInfo = "Device: $deviceMacAddress - $deviceName"
                                    Text(
                                        text = deviceInfo,
                                        modifier = Modifier
                                            .clickable { onDeviceSelected(scanResult.bleDevice) }
                                            .fillMaxWidth()
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startScan() {
        scanDisposable = rxBleClient.scanBleDevices(
            ScanSettings.Builder()
                .build()
        )
            .subscribe(
                { scanResult ->
                    if (scanResult.bleDevice.name == "MetricRun" &&
                        deviceList.none { it.bleDevice.macAddress == scanResult.bleDevice.macAddress }) {
                        deviceList.add(scanResult)
                    }
                },
                { throwable ->
                    Log.e("MainActivity", "Scan failed", throwable)
                }
            )
    }



    private fun onDeviceSelected(device: RxBleDevice) {
        val intent = Intent(this, DeviceActivity::class.java).apply {
            putExtra("mac_address", device.macAddress)
            putExtra("userEmail", userEmail)
        }
        startActivity(intent)
    }


    private fun checkBluetoothAvailability() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // Bluetooth is disabled
            // Here you can request the user to enable Bluetooth with an Intent action BluetoothAdapter.ACTION_REQUEST_ENABLE
        }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            // Device doesn't support Bluetooth Low Energy
            // Handle this situation accordingly
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN")
            != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT")
            != PackageManager.PERMISSION_GRANTED) {
            // You can request the permission.
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, "android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT"),
                LOCATION_PERMISSION_REQUEST_CODE
            )
            // If permissions are not granted, open the app settings
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_SCAN")
                != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, "android.permission.BLUETOOTH_CONNECT")
                != PackageManager.PERMISSION_GRANTED) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted
                    startScan()
                } else {
                    // Permission was denied. Handle this situation as you wish.
                    // You might want to show a message to the user explaining why you need the permission.
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanDisposable?.dispose()
        connectionDisposable?.dispose()
    }
}

@Preview(showBackground = true)
@Composable
fun Preview() {
    MetricRunBLETheme {
        Text("Android")
    }
}
