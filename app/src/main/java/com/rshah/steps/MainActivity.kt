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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

private val HEIGHT_RANGE = 100..250
private val GOAL_RANGE = 1000..50000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val savedHeight by StepRepo.heightCm.collectAsState()
    val savedGoal by StepRepo.goal.collectAsState()
    val savedSens by StepRepo.sensitivity.collectAsState()
    val savedLevel = (savedSens * 9f + 1f).roundToInt()

    var hText by rememberSaveable { mutableStateOf(savedHeight.toString()) }
    var gText by rememberSaveable { mutableStateOf(savedGoal.toString()) }
    var level by rememberSaveable { mutableFloatStateOf(savedLevel.toFloat()) }

    val h = hText.toIntOrNull()?.takeIf { it in HEIGHT_RANGE }
    val g = gText.toIntOrNull()?.takeIf { it in GOAL_RANGE }
    val lvl = level.roundToInt()

    val valid = h != null && g != null
    val dirty = hText != savedHeight.toString() || gText != savedGoal.toString() || lvl != savedLevel

    var confirmDiscard by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current

    fun handleBack() {
        if (dirty) {
            confirmDiscard = true
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingField("Height (cm)", hText, HEIGHT_RANGE) { hText = it }
            Text(
                "Stride length: %.0f cm. Used for distance.".format((h ?: savedHeight) * 0.415f),
                style = MaterialTheme.typography.bodySmall
            )

            SettingField("Daily goal (steps)", gText, GOAL_RANGE) { gText = it }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sensitivity",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text("$lvl / 10", style = MaterialTheme.typography.headlineSmall)
            }

            Slider(
                value = level,
                onValueChange = { v ->
                    val newLvl = v.roundToInt()
                    if (newLvl != level.roundToInt()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                    level = v
                },
                valueRange = 1f..10f,
                steps = 8
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Less sensitive", style = MaterialTheme.typography.bodySmall)
                Text("More sensitive", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "Calibrate: walk 100 steps and compare. Undercounting? Raise it. Counting while still? Lower it.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                onClick = {
                    if (valid) {
                        val sensFloat = (lvl - 1) / 9f
                        StepRepo.applySettings(h!!, g!!, sensFloat)
                        focusManager.clearFocus()
                        scope.launch {
                            snackbarHostState.showSnackbar("Saved")
                        }
                    }
                },
                enabled = dirty && valid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply settings")
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your changes haven't been applied yet.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDiscard = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            }
        )
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
fun SettingField(label: String, text: String, range: IntRange, onText: (String) -> Unit) {
    OutlinedTextField(
        value = text,
        onValueChange = { onText(it.filter { c -> c.isDigit() }.take(5)) },
        label = { Text(label) },
        singleLine = true,
        isError = text.toIntOrNull()?.let { it !in range } ?: true,
        supportingText = { Text("${range.first} to ${range.last}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth()
    )
}