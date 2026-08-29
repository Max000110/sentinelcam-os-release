package com.sentinelcam.node.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.sentinelcam.node.data.PreferencesManager
import com.sentinelcam.node.receiver.WatchdogReceiver
import com.sentinelcam.node.service.CctvForegroundService
import com.sentinelcam.node.service.NodeState
import com.sentinelcam.node.service.NodeStateHolder
import com.sentinelcam.node.util.OEMBackgroundHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var prefs: PreferencesManager
    private var isBatteryOptIgnored by mutableStateOf(false)
    private var isBlackScreenActive by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val audioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        if (cameraGranted && audioGranted) {
            checkAndPromptBatteryOptimization()
            startCctvService()
        } else {
            Toast.makeText(this, "Camera and Audio permissions are required for 24x7 CCTV operation", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = PreferencesManager(this)

        updateBatteryOptimizationState()
        checkAndRequestPermissions()

        val isAutoStarted = intent?.getBooleanExtra("AUTO_STARTED_FROM_BOOT", false) ?: false
        val isCrashRestart = intent?.getBooleanExtra("CRASH_RESTART", false) ?: false

        if (isAutoStarted || isCrashRestart) {
            startCctvService()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00E676),
                    secondary = Color(0xFF38BDF8),
                    background = Color(0xFF0F172A),
                    surface = Color(0xFF1E293B)
                )
            ) {
                if (isBlackScreenActive) {
                    OledBlackScreenView(
                        onExitBlackScreen = { setBlackScreenMode(false) }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CctvNodeDashboard(
                            prefs = prefs,
                            isBatteryOptimized = isBatteryOptIgnored,
                            isVivoDevice = OEMBackgroundHelper.isVivoOrIqoo(),
                            onStartService = { startCctvService() },
                            onStopService = { stopCctvService() },
                            onRequestBatteryOptimization = { requestIgnoreBatteryOptimization() },
                            onOpenAppSettings = { openAppSettings() },
                            onOpenVivoAutoStart = { OEMBackgroundHelper.openVivoAutoStart(this) },
                            onOpenVivoPowerWhitelist = { OEMBackgroundHelper.openVivoHighPowerConsumption(this) },
                            onEnterBlackScreen = { setBlackScreenMode(true) }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryOptimizationState()
    }

    private fun setBlackScreenMode(enabled: Boolean) {
        isBlackScreenActive = enabled
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            lp.screenBrightness = 0.01f
            window.attributes = lp
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val lp = window.attributes
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = lp
        }
    }

    private fun updateBatteryOptimizationState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            isBatteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            isBatteryOptIgnored = true
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
            checkAndPromptBatteryOptimization()
            startCctvService()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun checkAndPromptBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                requestIgnoreBatteryOptimization()
            }
        }
    }

    private fun startCctvService() {
        val intent = Intent(this, CctvForegroundService::class.java).apply {
            action = CctvForegroundService.ACTION_START
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Service start note: ${e.message}", Toast.LENGTH_SHORT).show()
        }
        WatchdogReceiver.scheduleWatchdog(this)
    }

    private fun stopCctvService() {
        val intent = Intent(this, CctvForegroundService::class.java).apply {
            action = CctvForegroundService.ACTION_STOP
        }
        startService(intent)
        WatchdogReceiver.cancelWatchdog(this)
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    openAppSettings()
                }
            } else {
                Toast.makeText(this, "Battery optimization is already disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openAppSettings() {
        OEMBackgroundHelper.openAppDetails(this)
    }
}

@Composable
fun OledBlackScreenView(
    onExitBlackScreen: () -> Unit
) {
    var showHint by remember { mutableStateOf(true) }
    var tapCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        delay(4000L)
        showHint = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                tapCount++
                if (tapCount >= 2) {
                    onExitBlackScreen()
                } else {
                    showHint = true
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (showHint) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "SentinelCam 24x7 OLED Mode",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Camera streaming actively at 0% backlight power.\nDouble-tap screen anywhere to unlock controls.",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CctvNodeDashboard(
    prefs: PreferencesManager,
    isBatteryOptimized: Boolean,
    isVivoDevice: Boolean,
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onRequestBatteryOptimization: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenVivoAutoStart: () -> Unit,
    onOpenVivoPowerWhitelist: () -> Unit,
    onEnterBlackScreen: () -> Unit
) {
    var deviceId by remember { mutableStateOf(prefs.deviceId) }
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var isPrivacyMode by remember { mutableStateOf(prefs.isPrivacyModeEnabled) }
    var autoStartOnBoot by remember { mutableStateOf(prefs.autoStartOnBoot) }

    LaunchedEffect(deviceId) {
        delay(500L)
        prefs.deviceId = deviceId
    }
    LaunchedEffect(serverUrl) {
        delay(500L)
        prefs.serverUrl = serverUrl
    }

    val nodeState by NodeStateHolder.connectionState.collectAsState()
    val apiStatus by NodeStateHolder.apiStatus.collectAsState()
    val wsStatus by NodeStateHolder.wsStatus.collectAsState()
    val rtcStatus by NodeStateHolder.rtcStatus.collectAsState()
    val lastError by NodeStateHolder.lastError.collectAsState()
    val lastSuccess by NodeStateHolder.lastSuccess.collectAsState()
    val fps by NodeStateHolder.fps.collectAsState()

    var diagnosticResults by remember { mutableStateOf<List<com.sentinelcam.node.diagnostics.ValidationItem>>(emptyList()) }
    var isRunningDiagnostics by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val isServiceActive = CctvForegroundService.isRunning || nodeState != NodeState.STOPPED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = Color(0xFF00E676),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "SentinelCam Node",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "24x7 Android 14/15/16 CCTV • Auto-Restart on Boot",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Vivo V40 5G / OEM Specific Whitelist Panel
        if (isVivoDevice) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = Color(0xFF818CF8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Vivo V40 5G / Funtouch OS 24x7 Setup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Vivo iManager blocks background apps by default. Enable both settings below to prevent background killing:",
                        fontSize = 11.sp,
                        color = Color(0xFFC7D2FE)
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenVivoAutoStart,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("1. Auto-Start", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onOpenVivoPowerWhitelist,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F46E5)),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text("2. High Power", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }

        // Battery Protection Banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isBatteryOptimized) Color(0xFF064E3B) else Color(0xFF78350F)
            ),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isBatteryOptimized) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isBatteryOptimized) Color(0xFF34D399) else Color(0xFFFBBF24),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isBatteryOptimized) "24x7 Background Protected" else "Background Killing Risk",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isBatteryOptimized) "Battery optimization disabled (Unrestricted)" else "Android may close app in background. Tap to fix.",
                            fontSize = 11.sp,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }

                if (!isBatteryOptimized) {
                    Button(
                        onClick = onRequestBatteryOptimization,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Fix Now", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Live Authoritative Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NODE STATE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Badge(
                        containerColor = when (nodeState) {
                            NodeState.STREAMING -> Color(0xFF00E676)
                            NodeState.WEBSOCKET_CONNECTED, NodeState.REGISTERED -> Color(0xFF38BDF8)
                            NodeState.STARTING, NodeState.CONNECTING -> Color(0xFFFBBF24)
                            NodeState.ERROR -> Color(0xFFEF4444)
                            else -> if (isServiceActive) Color(0xFF00E676) else Color(0xFF64748B)
                        }
                    ) {
                        Text(
                            text = nodeState.name,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "API: $apiStatus  |  WS: $wsStatus  |  WebRTC: $rtcStatus  |  FPS: $fps",
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    fontWeight = FontWeight.Medium
                )

                if (lastError != "None") {
                    Text(
                        text = "Error: $lastError",
                        fontSize = 12.sp,
                        color = Color(0xFFEF4444)
                    )
                }

                Text(
                    text = "Last Success: $lastSuccess",
                    fontSize = 11.sp,
                    color = Color(0xFF10B981)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (isServiceActive) {
                                onStopService()
                            } else {
                                onStartService()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServiceActive) Color(0xFFEF4444) else Color(0xFF00E676)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (isServiceActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isServiceActive) "Stop Service" else "Start Service")
                    }

                    Button(
                        onClick = onEnterBlackScreen,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OLED Black Screen", fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onOpenAppSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("App Battery & Permission Settings")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. System Validation Panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SYSTEM VALIDATION PANEL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Button(
                        onClick = {
                            isRunningDiagnostics = true
                            scope.launch(Dispatchers.IO) {
                                val validator = com.sentinelcam.node.diagnostics.SystemValidator(
                                    context = context,
                                    serverUrl = prefs.serverUrl,
                                    deviceId = prefs.deviceId
                                )
                                val results = validator.runFullDiagnosticSuite()
                                diagnosticResults = results
                                isRunningDiagnostics = false
                            }
                        },
                        enabled = !isRunningDiagnostics,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8))
                    ) {
                        Text(if (isRunningDiagnostics) "Testing..." else "Run Diagnostics", fontSize = 12.sp)
                    }
                }

                if (diagnosticResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    diagnosticResults.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = item.details,
                                    fontSize = 11.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Badge(
                                containerColor = if (item.isPassed) Color(0xFF10B981) else Color(0xFFEF4444)
                            ) {
                                Text(
                                    text = if (item.isPassed) "PASS" else "FAIL",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Device & Server Configuration Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DEVICE CONFIGURATION & REBOOT SETTINGS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device Identifier") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("VPS Server Base URL") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            serverUrl = "http://161.118.183.23:8000"
                            prefs.serverUrl = serverUrl
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset URL", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            prefs.serverUrl = serverUrl
                            prefs.deviceId = deviceId
                            Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save", fontSize = 12.sp, color = Color.Black)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Auto-Start on Boot toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Start on Boot / Reboot", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Automatically launch 24x7 CCTV stream when phone turns on", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoStartOnBoot,
                        onCheckedChange = {
                            autoStartOnBoot = it
                            prefs.autoStartOnBoot = it
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Privacy Mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Privacy Mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text("Halt face recognition & AI", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    }
                    Switch(
                        checked = isPrivacyMode,
                        onCheckedChange = {
                            isPrivacyMode = it
                            prefs.isPrivacyModeEnabled = it
                        }
                    )
                }
            }
        }
    }
}
