package com.sentinelcam.node.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sentinelcam.node.service.CctvForegroundService
import com.sentinelcam.node.service.WatchdogManager
import com.sentinelcam.node.telemetry.DeviceHealthMonitor
import com.sentinelcam.node.telemetry.DeviceHealthStats

class MainActivity : ComponentActivity() {

    private var isServiceRunning by mutableStateOf(false)
    private var serverUrl by mutableStateOf("http://127.0.0.1:8000")
    private var deviceId by mutableStateOf("cam_livingroom_01")
    private var liveStats by mutableStateOf<DeviceHealthStats?>(null)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            requestBatteryExemption()
            startCctvService()
        } else {
            Toast.makeText(this, "Camera & Audio permissions required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WatchdogManager.scheduleWatchdog(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    surface = Color(0xFF121212),
                    background = Color(0xFF0A0A0A)
                )
            ) {
                NodeStatusScreen(
                    serverUrl = serverUrl,
                    onServerUrlChange = { serverUrl = it },
                    deviceId = deviceId,
                    onDeviceIdChange = { deviceId = it },
                    isRunning = isServiceRunning,
                    stats = liveStats,
                    onToggleService = {
                        if (isServiceRunning) {
                            stopCctvService()
                        } else {
                            checkAndRequestPermissions()
                        }
                    },
                    onRefreshStats = {
                        val monitor = DeviceHealthMonitor(this, serverUrl, deviceId)
                        liveStats = monitor.collectCurrentStats()
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            requestBatteryExemption()
            startCctvService()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }

    private fun startCctvService() {
        val intent = Intent(this, CctvForegroundService::class.java).apply {
            putExtra("EXTRA_SERVER_URL", serverUrl)
            putExtra("EXTRA_DEVICE_ID", deviceId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        isServiceRunning = true
        Toast.makeText(this, "SentinelCam 24x7 Node Active", Toast.LENGTH_SHORT).show()
    }

    private fun stopCctvService() {
        stopService(Intent(this, CctvForegroundService::class.java))
        isServiceRunning = false
        Toast.makeText(this, "SentinelCam Node Stopped", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeStatusScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    deviceId: String,
    onDeviceIdChange: (String) -> Unit,
    isRunning: Boolean,
    stats: DeviceHealthStats?,
    onToggleService: () -> Unit,
    onRefreshStats: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "SentinelCam CCTV Node",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141414))
            )
        },
        containerColor = Color(0xFF0A0A0A)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Banner Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) Color(0xFF1B382B) else Color(0xFF2E2020)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Videocam else Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = if (isRunning) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isRunning) "24x7 STREAMING ACTIVE" else "NODE IDLE / STOPPED",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isRunning) "WebRTC Publisher running in background" else "Ready to connect to VPS",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Configuration Section
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Node Configuration", fontWeight = FontWeight.SemiBold, color = Color.White)
                    
                    OutlinedTextField(
                        value = deviceId,
                        onValueChange = onDeviceIdChange,
                        label = { Text("Device ID") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning
                    )

                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("VPS Server URL") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isRunning
                    )
                }
            }

            // Health Telemetry Grid
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Device Telemetry", fontWeight = FontWeight.SemiBold, color = Color.White)
                        IconButton(onClick = onRefreshStats) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color(0xFF00E676))
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("🔋 Battery: ${stats?.batteryLevel ?: "--"}% (${stats?.isCharging ?: "N/A"})", color = Color(0xFFCCCCCC))
                        Text("🌡️ Temp: ${stats?.temperatureC ?: "--"}°C", color = Color(0xFFCCCCCC))
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📶 Network: ${stats?.networkType ?: "--"} (${stats?.wifiRssiDbm ?: "--"} dBm)", color = Color(0xFFCCCCCC))
                        Text("💾 Free: ${stats?.storageFreeMb ?: "--"} MB", color = Color(0xFFCCCCCC))
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Action Button
            Button(
                onClick = onToggleService,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFFF5252) else Color(0xFF00E676)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = if (isRunning) "STOP 24x7 CCTV NODE" else "START 24x7 CCTV NODE",
                    fontWeight = FontWeight.Bold,
                    color = if (isRunning) Color.White else Color.Black,
                    fontSize = 16.sp
                )
            }
        }
    }
}
