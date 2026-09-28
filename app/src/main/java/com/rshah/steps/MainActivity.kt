package com.rshah.steps

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StepRepo.init(this)
        enableEdgeToEdge()
        setContent { StepTheme { StepApp() } }
    }
}

@Composable
fun StepTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

private fun Context.granted(p: String) =
    ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

@Composable
fun StepApp() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) SettingsScreen { showSettings = false }
    else HomeScreen { showSettings = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val steps by StepRepo.steps.collectAsState()
    val goal by StepRepo.goal.collectAsState()
    val running by StepRepo.running.collectAsState()
    var confirmStop by remember { mutableStateOf(false) }

    fun start() = ContextCompat.startForegroundService(ctx, Intent(ctx, StepService::class.java))
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { if (ctx.granted(Manifest.permission.ACTIVITY_RECOGNITION)) start() }

    fun begin() {
        val need = buildList {
            add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot { ctx.granted(it) }
        if (need.isEmpty()) start() else launcher.launch(need.toTypedArray())
    }

    LaunchedEffect(Unit) {
        if (!running && ctx.granted(Manifest.permission.ACTIVITY_RECOGNITION)) start()
    }

    val km = steps * StepRepo.strideMeters() / 1000f
    val kcal = steps * 0.04f

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("Steps") },
            actions = {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (steps / goal.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.size(260.dp),
                    strokeWidth = 18.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$steps", style = MaterialTheme.typography.displayLarge)
                    Text("of $goal steps", style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Distance", "%.2f km".format(km), Modifier.weight(1f))
                StatCard("Calories", "%.0f kcal".format(kcal), Modifier.weight(1f))
            }

            Button(
                onClick = { if (running) confirmStop = true else begin() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (running) "Stop tracking" else "Start tracking") }
        }
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("Stop tracking?") },
            text = { Text("Steps won't be counted until you start again.") },
            confirmButton = {
                TextButton(onClick = {
                    ctx.stopService(Intent(ctx, StepService::class.java))
                    confirmStop = false
                }) { Text("Stop") }
            },
            dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val height by StepRepo.heightCm.collectAsState()
    val goal by StepRepo.goal.collectAsState()
    val sens by StepRepo.sensitivity.collectAsState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            NumField("Height (cm)", height, 100..250) { StepRepo.setHeight(it) }
            Text(
                "Stride length: %.0f cm. Used for distance.".format(StepRepo.strideMeters() * 100),
                style = MaterialTheme.typography.bodySmall
            )
            NumField("Daily goal (steps)", goal, 1000..50000) { StepRepo.setGoal(it) }

            Text("Sensitivity", style = MaterialTheme.typography.titleMedium)
            Slider(value = sens, onValueChange = { StepRepo.setSensitivity(it) })
            Text(
                "Calibrate: walk 100 steps and compare. Undercounting? Raise it. " +
                    "Counting while still? Lower it.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun NumField(label: String, value: Int, range: IntRange, onValid: (Int) -> Unit) {
    var t by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = t,
        onValueChange = { s ->
            t = s.filter { it.isDigit() }.take(5)
            t.toIntOrNull()?.takeIf { it in range }?.let(onValid)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}
