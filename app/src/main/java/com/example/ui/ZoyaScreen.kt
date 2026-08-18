package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.BuildConfig
import com.example.R
import com.example.ZoyaForegroundService
import com.example.live.ZoyaState
import com.example.voice.ContinuousSpeechRecognizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ZoyaScreen() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToChat = { navController.navigate("chat") }
            )
        }
        composable("chat") {
            ChatScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onNavigateToChat: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ZoyaPrefs", Context.MODE_PRIVATE) }
    
    val envKey = if (BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
        BuildConfig.GEMINI_API_KEY
    } else ""
    var apiKey by remember { mutableStateOf(prefs.getString("api_key", envKey) ?: envKey) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var zoyaState by remember { mutableStateOf(ZoyaForegroundService.currentState) }
    var serviceStarted by remember { mutableStateOf(ZoyaForegroundService.activeService != null) }
    var showMenu by remember { mutableStateOf(false) }
    var quickTextInput by remember { mutableStateOf("") }

    // 24-Hour Continuous Speech Recognizer instance for home quick-chat
    val speechRecognizer = remember {
        ContinuousSpeechRecognizer(context) { recognizedText ->
            if (recognizedText.isNotBlank()) {
                val service = ZoyaForegroundService.activeService
                if (service != null) {
                    service.sendTextMessage(recognizedText)
                } else {
                    val intent = Intent(context, ZoyaForegroundService::class.java)
                    ContextCompat.startForegroundService(context, intent)
                    serviceStarted = true
                    // Will send once connected
                    ZoyaForegroundService.activeService?.sendTextMessage(recognizedText)
                }
            }
        }
    }

    val is24hListening by speechRecognizer.is24HourModeActive.collectAsState()
    val partialText by speechRecognizer.partialText.collectAsState()
    val rmsDb by speechRecognizer.rmsDb.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[android.Manifest.permission.RECORD_AUDIO] == true) {
            val intent = Intent(context, ZoyaForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
            serviceStarted = true
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required for voice assistant!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        ZoyaForegroundService.onStateChange = { state ->
            zoyaState = state
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_zoya_logo),
                            contentDescription = "Zoya AI Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "ZOYA AI",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                letterSpacing = 1.5.sp
                            )
                            Text(
                                text = if (is24hListening) "🟢 24h Mic Active • Listening" else if (serviceStarted) "Active • Background Ready" else "Ready to Initialize",
                                color = if (is24hListening) Color(0xFF00E676) else if (serviceStarted) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
                actions = {
                    IconButton(
                        onClick = onNavigateToChat,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .testTag("open_chatbox_button")
                    ) {
                        Text("💬", fontSize = 18.sp)
                    }
                    IconButton(
                        onClick = { showMenu = !showMenu },
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .background(Color(0xFF1E1E2E).copy(alpha = 0.95f))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open AI Chatbox", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onNavigateToChat()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Gemini API Key Settings", color = Color.White) },
                            onClick = {
                                showMenu = false
                                showApiKeyDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Accessibility Settings (Auto-Click)", color = Color.White) },
                            onClick = {
                                showMenu = false
                                val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            }
                        )
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1A1A2E), Color(0xFF0F0F1A), Color(0xFF07070E)),
                        radius = 1600f
                    )
                )
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Status Pill Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.07f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color = when {
                                        is24hListening -> Color(0xFF00E676)
                                        zoyaState == ZoyaState.SPEAKING -> Color(0xFF00E676)
                                        zoyaState == ZoyaState.LISTENING -> Color(0xFF00E5FF)
                                        zoyaState == ZoyaState.THINKING -> Color(0xFFFFD180)
                                        else -> if (serviceStarted) Color(0xFF80D8FF) else Color(0xFF9E9E9E)
                                    },
                                    shape = CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                is24hListening -> "24h Mic Active • Always Listening..."
                                !serviceStarted -> "AI Standby"
                                zoyaState == ZoyaState.LISTENING -> "Listening & Ready..."
                                zoyaState == ZoyaState.THINKING -> "Thinking..."
                                zoyaState == ZoyaState.SPEAKING -> "Zoya Speaking..."
                                else -> "AI Connected"
                            },
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Central AI Orb Card
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ZoyaOrb(state = if (is24hListening && zoyaState == ZoyaState.IDLE) ZoyaState.LISTENING else zoyaState)

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (is24hListening) {
                                "24h Mic ON: Speak anytime, voice is auto-sent to AI"
                            } else if (serviceStarted) {
                                "Talk to Zoya or tap buttons below"
                            } else {
                                "Tap 'Open AI Assistant' to start"
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Quick Live Speech Transcription Subtitle on Home Screen
                if (partialText.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF00B0FF).copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = "Hearing Voice",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Hearing: \"$partialText\"",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Quick Prompt Suggestion Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    val suggestions = listOf(
                        "💬 Open Chatbox" to "Open Chatbox",
                        "⚡ Open YouTube" to "Open YouTube app",
                        "📞 Call Contact" to "Call Shivank",
                        "🔦 Toggle Flashlight" to "Turn on torch",
                        "🔊 Volume Up" to "Increase volume",
                        "💬 Ask Zoya" to "Introduce yourself briefly"
                    )
                    items(suggestions) { (label, command) ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier.clickable {
                                if (label == "💬 Open Chatbox") {
                                    onNavigateToChat()
                                } else if (serviceStarted) {
                                    ZoyaForegroundService.activeService?.sendTextMessage(command)
                                } else {
                                    quickTextInput = command
                                }
                            }
                        ) {
                            Text(
                                text = label,
                                color = Color(0xFF80D8FF),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Primary AI Controls
                if (!serviceStarted) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_zoya_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00B0FF),
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(26.dp),
                        onClick = {
                            if (apiKey.isEmpty()) {
                                showApiKeyDialog = true
                            } else {
                                val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasContacts = ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                val hasPhone = ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED

                                if (hasMic && hasContacts && hasPhone) {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    serviceStarted = true
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.RECORD_AUDIO,
                                            android.Manifest.permission.READ_CONTACTS,
                                            android.Manifest.permission.CALL_PHONE
                                        )
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Open AI",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Open AI Assistant",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                } else {
                    // Controls when AI is active
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("reconnect_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            onClick = {
                                val service = ZoyaForegroundService.activeService
                                if (service != null) {
                                    service.reconnectSession()
                                } else {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                }
                            }
                        ) {
                            Text("Reconnect AI", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        Button(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("stop_zoya_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE53935).copy(alpha = 0.25f),
                                contentColor = Color(0xFFFF8A80)
                            ),
                            border = BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(24.dp),
                            onClick = {
                                speechRecognizer.stopListening()
                                val intent = Intent(context, ZoyaForegroundService::class.java)
                                context.stopService(intent)
                                serviceStarted = false
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Stop,
                                contentDescription = "Stop AI",
                                tint = Color(0xFFFF8A80),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop AI", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Direct Text & 24h Mic Interaction Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(24.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 24H Mic Quick Button on Home
                    IconButton(
                        onClick = {
                            val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasMic) {
                                speechRecognizer.toggle24HourListening()
                                if (!serviceStarted) {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    serviceStarted = true
                                }
                            } else {
                                permissionLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (is24hListening) Color(0xFF00E676).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (is24hListening) Color(0xFF00E676) else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                            .testTag("home_24h_mic_button")
                    ) {
                        Icon(
                            imageVector = if (is24hListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = "24-Hour Continuous Mic",
                            tint = if (is24hListening) Color(0xFF00E676) else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    TextField(
                        value = quickTextInput,
                        onValueChange = { quickTextInput = it },
                        placeholder = { Text("Ask or command Zoya...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = {
                            if (quickTextInput.isNotBlank()) {
                                if (serviceStarted) {
                                    ZoyaForegroundService.activeService?.sendTextMessage(quickTextInput)
                                    quickTextInput = ""
                                } else {
                                    val intent = Intent(context, ZoyaForegroundService::class.java)
                                    ContextCompat.startForegroundService(context, intent)
                                    serviceStarted = true
                                    quickTextInput = ""
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color(0xFF00B0FF), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send Command",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        var tempKey by remember { mutableStateOf(apiKey) }
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("Gemini API Key") },
            text = {
                Column {
                    Text("Enter your Gemini API key to power Zoya's AI Live intelligence.")
                    Spacer(modifier = Modifier.height(10.dp))
                    TextField(
                        value = tempKey,
                        onValueChange = { tempKey = it },
                        placeholder = { Text("AIza...") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        trailingIcon = {
                            if (tempKey.isNotEmpty()) {
                                IconButton(onClick = { tempKey = "" }) {
                                    Icon(
                                        imageVector = Icons.Filled.Clear,
                                        contentDescription = "Clear text"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Get your API key on Google AI Studio",
                        color = Color(0xFF00B0FF),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                            context.startActivity(intent)
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        prefs.edit().putString("api_key", tempKey).apply()
                        apiKey = tempKey
                        showApiKeyDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Full Chatbox Screen featuring a 24-Hour Continuous Mic Option.
 * The mic remains open 24/7 without turning off, captures the user's voice
 * with high accuracy, and automatically forwards the spoken query directly to the AI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val liveSessionManager = ZoyaForegroundService.activeService?.liveSessionManager
    val messages by ZoyaForegroundService.messages.collectAsState()
    val zoyaState = ZoyaForegroundService.currentState

    var chatTextInput by remember { mutableStateOf("") }

    // Helper to ensure service is alive and forward message to AI
    val forwardToAI: (String) -> Unit = { query ->
        if (query.isNotBlank()) {
            val service = ZoyaForegroundService.activeService
            if (service != null) {
                service.sendTextMessage(query)
            } else {
                val intent = Intent(context, ZoyaForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
                // Trigger send when ready
                coroutineScope.launch {
                    delay(500)
                    ZoyaForegroundService.activeService?.sendTextMessage(query)
                }
            }
        }
    }

    // 24-Hour Continuous Voice Engine
    val speechRecognizer = remember {
        ContinuousSpeechRecognizer(context) { recognizedSpeech ->
            forwardToAI(recognizedSpeech)
        }
    }

    val is24hListening by speechRecognizer.is24HourModeActive.collectAsState()
    val isRecording by speechRecognizer.isListening.collectAsState()
    val partialSpeechText by speechRecognizer.partialText.collectAsState()
    val lastForwardedText by speechRecognizer.lastForwardedText.collectAsState()
    val soundLevelDb by speechRecognizer.rmsDb.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer.destroy()
        }
    }

    // Permission launcher
    val audioPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechRecognizer.start24HourListening()
            if (ZoyaForegroundService.activeService == null) {
                val intent = Intent(context, ZoyaForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            }
        } else {
            android.widget.Toast.makeText(context, "Microphone permission is required for 24h voice listening!", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Auto-scroll to bottom whenever a new message arrives
    LaunchedEffect(messages.size, partialSpeechText) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Infinite breathing glow animation for active mic
    val infiniteTransition = rememberInfiniteTransition(label = "24hMicPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    Scaffold(
        containerColor = Color(0xFF10101C),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF))),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⚡", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Zoya AI Chatbox",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (is24hListening) "🟢 24h Mic Active (Always Listening)" else "State: ${zoyaState.name}",
                                color = if (is24hListening) Color(0xFF00E676) else Color(0xFF80D8FF),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            ZoyaForegroundService.activeService?.clearMessages()
                        },
                        modifier = Modifier.testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = "Clear Chat",
                            tint = Color.White.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = {
                            val service = ZoyaForegroundService.activeService
                            if (service != null) {
                                service.reconnectSession()
                            } else {
                                val intent = Intent(context, ZoyaForegroundService::class.java)
                                ContextCompat.startForegroundService(context, intent)
                            }
                        },
                        modifier = Modifier.testTag("chat_reconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Reconnect AI",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF161626)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF161626), Color(0xFF0F0F1A), Color(0xFF0A0A12))
                    )
                )
        ) {
            // 24-Hour Continuous Mic Option Banner
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (is24hListening) Color(0xFF00E676).copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f),
                border = BorderStroke(
                    1.dp,
                    if (is24hListening) Color(0xFF00E676).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (is24hListening) Color(0xFF00E676).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (is24hListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                            contentDescription = "24-Hour Continuous Mic",
                            tint = if (is24hListening) Color(0xFF00E676) else Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (is24hListening) "24h Mic is OPEN & Listening" else "24-Hour Continuous Mic",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (is24hListening) "Voice is automatically captured & forwarded to AI" else "Turn ON to keep mic active 24/7 without stopping",
                            color = if (is24hListening) Color(0xFFB9F6CA) else Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = is24hListening,
                        onCheckedChange = { enable ->
                            if (enable) {
                                val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    speechRecognizer.start24HourListening()
                                    if (ZoyaForegroundService.activeService == null) {
                                        val intent = Intent(context, ZoyaForegroundService::class.java)
                                        ContextCompat.startForegroundService(context, intent)
                                    }
                                } else {
                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            } else {
                                speechRecognizer.stopListening()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF00E676),
                            uncheckedThumbColor = Color.Gray,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        ),
                        modifier = Modifier.testTag("24h_mic_switch")
                    )
                }
            }

            // Message Stream
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    // Welcoming empty state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color.White.copy(alpha = 0.05f), CircleShape)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💬", fontSize = 28.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Welcome to Zoya AI Chat",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Turn ON the 24-Hour Mic below or type any message. Voice will be accurately recognized and forwarded directly to AI.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Quick action suggestion chips
                        val suggestionList = listOf(
                            "📞 Call Rohit",
                            "🎵 Play latest Bollywood hits",
                            "🔦 Turn on Flashlight",
                            "⚡ Open WhatsApp"
                        )
                        suggestionList.forEach { suggestion ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        forwardToAI(suggestion)
                                    }
                            ) {
                                Text(
                                    text = suggestion,
                                    color = Color(0xFF80D8FF),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        items(messages) { msg ->
                            ChatMessageBubble(msg)
                        }
                    }
                }
            }

            // Real-Time Live Speech Subtitle & Soundwave Visualizer (Pill above input dock)
            AnimatedVisibility(
                visible = partialSpeechText.isNotEmpty() || (is24hListening && soundLevelDb > 1f),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF00B0FF).copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color(0xFF00B0FF).copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = "Listening Voice Wave",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (partialSpeechText.isNotEmpty()) {
                                "🎙️ Listening: \"$partialSpeechText\""
                            } else {
                                "🎙️ Hearing your voice... Speak now"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        // Sound wave amplitude bars
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bars = 4
                            for (i in 0 until bars) {
                                val heightMultiplier = ((soundLevelDb + i * 1.5f) % 8f) / 8f
                                val barHeight = (6 + (heightMultiplier * 14)).dp
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(barHeight)
                                        .background(Color(0xFF00E5FF), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Forwarded confirmation snack
            if (lastForwardedText.isNotEmpty() && partialSpeechText.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF00E676).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Forwarded to AI: \"$lastForwardedText\"",
                            color = Color(0xFFB9F6CA),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Chat Input Bar with 24-Hour Continuous Mic Button
            Surface(
                color = Color(0xFF161626),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 24-Hour Continuous Mic Button with Pulsing Glow Animation
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(52.dp)
                    ) {
                        if (is24hListening) {
                            // Pulsing ambient glow
                            Box(
                                modifier = Modifier
                                    .size(48.dp * pulseScale)
                                    .background(
                                        Color(0xFF00E676).copy(alpha = glowAlpha * 0.4f),
                                        CircleShape
                                    )
                            )
                        }

                        IconButton(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    speechRecognizer.toggle24HourListening()
                                    if (!is24hListening && ZoyaForegroundService.activeService == null) {
                                        val intent = Intent(context, ZoyaForegroundService::class.java)
                                        ContextCompat.startForegroundService(context, intent)
                                    }
                                } else {
                                    audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .background(
                                    if (is24hListening) Color(0xFF00E676) else Color.White.copy(alpha = 0.1f),
                                    CircleShape
                                )
                                .border(
                                    1.dp,
                                    if (is24hListening) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.2f),
                                    CircleShape
                                )
                                .testTag("continuous_mic_button")
                        ) {
                            Icon(
                                imageVector = if (is24hListening) Icons.Filled.Mic else Icons.Filled.MicOff,
                                contentDescription = "24-Hour Mic Button",
                                tint = if (is24hListening) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Text input field
                    TextField(
                        value = chatTextInput,
                        onValueChange = { chatTextInput = it },
                        placeholder = {
                            Text(
                                text = if (is24hListening) "Mic is listening... or type here" else "Type a message or speak...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("chat_input_field")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Send Button
                    IconButton(
                        onClick = {
                            if (chatTextInput.isNotBlank()) {
                                forwardToAI(chatTextInput.trim())
                                chatTextInput = ""
                            }
                        },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color(0xFF00B0FF), CircleShape)
                            .testTag("chat_send_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Message bubble with distinct styling for User messages, AI turns, and system events.
 */
@Composable
fun ChatMessageBubble(message: String) {
    val isUser = message.startsWith("You: ")
    val isZoya = message.startsWith("Zoya: ")
    val isServerInfo = message.startsWith("Server says:") || message.startsWith("WebSocket") || message.startsWith("Session stopped")

    val cleanContent = when {
        isUser -> message.removePrefix("You: ").trim()
        isZoya -> message.removePrefix("Zoya: ").trim()
        else -> message.trim()
    }

    if (isServerInfo) {
        // System status pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Text(
                    text = cleanContent,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    } else if (isUser) {
        // User message bubble (Right aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Surface(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFF0091EA), Color(0xFF6200EA))
                        ),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = cleanContent,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    } else {
        // Zoya AI message bubble (Left aligned)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xFF00E5FF).copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, Color(0xFF00E5FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⚡", fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                color = Color(0xFF1E1E2E),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.25f)),
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = cleanContent,
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
fun ZoyaOrb(state: ZoyaState) {
    val radiusScale = remember { Animatable(1f) }
    val glowAlpha = remember { Animatable(0.5f) }
    
    // Ring rotations
    val ring1Angle = remember { Animatable(0f) }
    val ring2Angle = remember { Animatable(120f) }
    val ring3Angle = remember { Animatable(240f) }
    val ring4Angle = remember { Animatable(45f) }

    LaunchedEffect(state) {
        when (state) {
            ZoyaState.IDLE -> {
                radiusScale.animateTo(1f, animationSpec = tween(1000))
                glowAlpha.animateTo(
                    targetValue = 0.4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.LISTENING -> {
                radiusScale.animateTo(1.1f, animationSpec = tween(500))
                glowAlpha.animateTo(
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.THINKING -> {
                radiusScale.animateTo(1.05f, animationSpec = tween(400))
                glowAlpha.animateTo(
                    targetValue = 0.6f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1200, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
            ZoyaState.SPEAKING -> {
                radiusScale.animateTo(1.2f, animationSpec = tween(200))
                glowAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(300, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    )
                )
            }
        }
    }

    // Continuous rotation for rings
    LaunchedEffect(Unit) {
        launch {
            ring1Angle.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(6000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring2Angle.animateTo(
                targetValue = 360f + 120f,
                animationSpec = infiniteRepeatable(
                    animation = tween(7000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring3Angle.animateTo(
                targetValue = 360f + 240f,
                animationSpec = infiniteRepeatable(
                    animation = tween(5500, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
        launch {
            ring4Angle.animateTo(
                targetValue = -360f + 45f,
                animationSpec = infiniteRepeatable(
                    animation = tween(8000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }
    }

    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val baseRadius = size.minDimension / 4f
            val currentRadius = baseRadius * radiusScale.value
            
            // Core colors based on state
            val coreInnerColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF80D8FF)
                ZoyaState.LISTENING -> Color(0xFFB388FF)
                ZoyaState.THINKING -> Color(0xFFFFD180)
                ZoyaState.SPEAKING -> Color(0xFF69F0AE)
                else -> Color.LightGray
            }
            
            val coreOuterColor = when (state) {
                ZoyaState.IDLE -> Color(0xFF00B0FF)
                ZoyaState.LISTENING -> Color(0xFF651FFF)
                ZoyaState.THINKING -> Color(0xFFFF9100)
                ZoyaState.SPEAKING -> Color(0xFF00E676)
                else -> Color.Gray
            }

            // 1. Ambient Background Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreOuterColor.copy(alpha = glowAlpha.value * 0.5f), Color.Transparent),
                    center = center,
                    radius = currentRadius * 2.5f
                ),
                radius = currentRadius * 2.5f
            )

            // 2. The Glass Sphere (Core)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        coreInnerColor.copy(alpha = 0.8f),
                        coreOuterColor.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.5f)
                    ),
                    center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.3f, center.y - currentRadius * 0.3f),
                    radius = currentRadius * 1.2f
                ),
                radius = currentRadius
            )
            
            // Inner Core Highlight for 3D effect
            drawCircle(
                color = Color.White.copy(alpha = 0.4f),
                center = androidx.compose.ui.geometry.Offset(center.x - currentRadius * 0.4f, center.y - currentRadius * 0.4f),
                radius = currentRadius * 0.3f
            )

            // 3. Neon Orbital Rings
            val ringRadiusX = currentRadius * 1.8f
            val ringRadiusY = currentRadius * 0.6f
            
            // Helper function to draw a 3D ring
            fun drawNeonRing(angle: Float, startColor: Color, endColor: Color, strokeWidth: Float) {
                rotate(angle, center) {
                    drawOval(
                        brush = Brush.sweepGradient(
                            colors = listOf(startColor, endColor, startColor, Color.Transparent, startColor),
                            center = center
                        ),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth)
                    )
                    drawOval(
                        color = startColor.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - ringRadiusX, center.y - ringRadiusY),
                        size = androidx.compose.ui.geometry.Size(ringRadiusX * 2, ringRadiusY * 2),
                        style = Stroke(width = strokeWidth * 3)
                    )
                }
            }

            // Draw Rings
            val speedMultiplier = if (state == ZoyaState.THINKING || state == ZoyaState.SPEAKING) 2f else 1f
            
            drawNeonRing(ring1Angle.value * speedMultiplier, Color(0xFFFF1744), Color(0xFFD50000), 4f)
            drawNeonRing(ring2Angle.value * speedMultiplier, Color(0xFF00E676), Color(0xFF76FF03), 4f)
            drawNeonRing(ring3Angle.value * speedMultiplier, Color(0xFF00E5FF), Color(0xFF2979FF), 4f)
            drawNeonRing(ring4Angle.value * speedMultiplier, Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.1f), 2f)
            
            // 4. Outer Glass Dome Reflection
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.3f)),
                    center = center,
                    radius = currentRadius * 2.2f
                ),
                radius = currentRadius * 2.2f,
                style = Stroke(width = 2f)
            )
        }
    }
}
