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
import androidx.navigation.NavHostController
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    private var isConnected by mutableStateOf(false) // Track connection status
    private var isToggledOn by mutableStateOf(false) // Track toggle state

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.BLUETOOTH_SCAN] == true &&
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true &&
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            scanLeDevice()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    @Composable
    fun NavigationComponent(navController: NavHostController) {
        NavHost(navController, startDestination = "login") {
            composable("login") {
                LoginScreen(navController = navController)
            }
            composable("main") {
                MainScreen(
                    discoveredDevices = discoveredDevices,
                    messages = messages,
                    status = if (isConnected) "Connected" else "Disconnected",  // Passing status
                    onScanClick = { checkPermissionsAndScan() },
                    onDeviceClick = { device -> connectToDevice(device) },
                    onToggleChanged = { toggleState -> writeToggleToDevice(toggleState) }
                )
            }
        }
    }
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluetoothSensorAppTheme {
                val navController = rememberNavController()
                NavigationComponent(navController = navController)
            }
        }

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
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
                scanLeDevice()
            }
            else -> {
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
            discoveredDevices.clear()
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
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
                runOnUiThread {
                    isConnected = true
                    messages.add("Connected to ${gatt?.device?.name}")
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                runOnUiThread {
                    isConnected = false
                    messages.add("Disconnected from ${gatt?.device?.name}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun writeToggleToDevice(toggleState: Boolean) {
        val value = if (toggleState) "U" else "L"
        val service = bluetoothGatt?.getService(HM10_UUID_SERVICE)
        val characteristic = service?.getCharacteristic(HM10_UUID_CHAR)

        characteristic?.value = value.toByteArray()
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    companion object {
        val HM10_UUID_SERVICE = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb")
        val HM10_UUID_CHAR = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
    }
}



@SuppressLint("MissingPermission")
@Composable
fun LoginScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "CarGuard",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp)) // Add space below the title
        Text(
            text = "Login",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (error) {
            Text(
                text = "Invalid credentials, try again.",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Button(onClick = {
            // Hardcoded logic for allowing specific credentials
            if (username == "user1" && password == "bums1") {
                navController.navigate("main")
            } else {
                error = true
            }
        }) {
            Text(text = "Login")
        }
    }
}


@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    discoveredDevices: List<BluetoothDevice>,
    messages: List<String>,
    status: String,
    onScanClick: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit,
    onToggleChanged: (Boolean) -> Unit
) {
    var isToggledOn by remember { mutableStateOf(false) }

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
        Button(onClick = { onScanClick() }) {
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

        if (status == "Connected") {
            Switch(
                checked = isToggledOn,
                onCheckedChange = { toggled ->
                    isToggledOn = toggled
                    onToggleChanged(toggled)
                },
                modifier = Modifier.padding(8.dp)
            )
            Text(
                text = "Send: ${if (isToggledOn) "U" else "L"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(messages) { message ->
                Text(text = message)
            }
        }
    }

}
