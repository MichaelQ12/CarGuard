package com.example.bluetoothsensorapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bluetoothsensorapp.ui.theme.BluetoothSensorAppTheme
import java.util.*

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeScanner: BluetoothLeScanner
    private var scanning = false
    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000

    private val discoveredDevices = mutableStateListOf<BluetoothDevice>()
    private var bluetoothGatt: BluetoothGatt? = null
    private var messages = mutableStateListOf<String>()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            // Permissions granted, proceed with scanning
            scanLeDevice()
        } else {
            // Permissions denied, show a message to the user
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothSensorAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        discoveredDevices = discoveredDevices,
                        messages = messages,
                        onScanClick = { checkPermissionsAndScan() },
                        onDeviceClick = { device -> connectToDevice(device) }
                    )
                }
            }
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun checkPermissionsAndScan() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                // Permissions are already granted, proceed with scanning
                scanLeDevice()
            }
            else -> {
                // Request permissions
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        if (!scanning) {
            handler.postDelayed({
                scanning = false
                bluetoothLeScanner.stopScan(leScanCallback)
            }, SCAN_PERIOD)
            scanning = true
            discoveredDevices.clear() // Clear the list before starting a new scan
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            scanning = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device: BluetoothDevice = result.device
            if (device.name != null && !discoveredDevices.contains(device)) {
                discoveredDevices.add(device)
            }
        }

        @SuppressLint("MissingPermission")
        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            for (result in results) {
                val device: BluetoothDevice = result.device
                if (device.name != null && !discoveredDevices.contains(device)) {
                    discoveredDevices.add(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            println("Scan failed with error code: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
                runOnUiThread {
                    messages.add("Connected to ${gatt?.device?.name}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    messages.add("Disconnected from ${gatt?.device?.name}")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(HM10_UUID_SERVICE)
                val characteristic = service?.getCharacteristic(HM10_UUID_CHAR)
                gatt?.setCharacteristicNotification(characteristic, true)
                val descriptor = characteristic?.getDescriptor(HM10_UUID_DESCRIPTOR)
                descriptor?.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt?.writeDescriptor(descriptor)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { value ->
                val message = value.toString(Charsets.UTF_8)
                runOnUiThread {
                    messages.add("Message from ${gatt?.device?.name}: $message")
                }
            }
        }
    }

    companion object {
        val HM10_UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val HM10_UUID_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        val HM10_UUID_DESCRIPTOR = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

@Composable
fun MainScreen(discoveredDevices: List<BluetoothDevice>, messages: List<String>, onScanClick: () -> Unit, onDeviceClick: (BluetoothDevice) -> Unit) {
    var status by remember { mutableStateOf("Disconnected") }
    var sensorData by remember { mutableStateOf("N/A") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            onScanClick()
        }) {
            Text(text = "Scan for Devices")
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(discoveredDevices) { device ->
                Text(
                    text = "${device.name} - ${device.address}",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .clickable {
                            onDeviceClick(device)
                        }
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(messages) { message ->
                Text(text = message)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    BluetoothSensorAppTheme {
        MainScreen(discoveredDevices = listOf(), messages = listOf(), onScanClick = {}, onDeviceClick = {})
    }
}
