package com.savaudio.separator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.savaudio.core.OutputDevice
import com.savaudio.core.RoutePlanner
import com.savaudio.core.RouteRule
import com.savaudio.service.AudioRouterService
import com.savaudio.service.DeviceMonitor
import com.savaudio.service.RouterState
import com.savaudio.service.RuleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppEntry(val packageName: String, val label: String, val icon: ImageBitmap?)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = SavColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SeparatorScreen()
                }
            }
        }
    }
}

private val SavColors = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF04111C),
    secondary = Color(0xFFF59E0B),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF0B1220),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF162033),
    onSurfaceVariant = Color(0xFF9FB0C7),
    error = Color(0xFFF87171),
)

internal fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { it.activityInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { info ->
            val icon = try { info.loadIcon(pm).toBitmap(96, 96).asImageBitmap() } catch (_: Exception) { null }
            AppEntry(info.packageName, info.loadLabel(pm).toString(), icon)
        }
        .sortedBy { it.label.lowercase() }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    else arrayOf(Manifest.permission.RECORD_AUDIO)

@Composable
fun SeparatorScreen() {
    val context = LocalContext.current
    val repository = remember { RuleRepository.get(context) }
    val monitor = remember { DeviceMonitor(context) }
    DisposableEffect(Unit) {
        monitor.start()
        onDispose { monitor.stop() }
    }

    val rules by repository.rules.collectAsState()
    val devices by monitor.devices.collectAsState()
    val status by RouterState.status.collectAsState()

    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf<AppEntry?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { loadApps(context) } }

    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            notice = null
            AudioRouterService.start(context, result.resultCode, data)
        } else {
            notice = "Routing needs the capture consent. Nothing is recorded or saved."
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            notice = "Audio permission is required to pick up another app's sound."
        }
    }

    fun startRouting() {
        val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            captureLauncher.launch(manager.createScreenCaptureIntent())
        } else {
            permissionLauncher.launch(requiredPermissions())
        }
    }

    val ruleByPackage = rules.associateBy { it.packageName }
    val visible = (apps ?: emptyList()).filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { ruleByPackage.containsKey(it.packageName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("SavAudio Separator", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Send each app's sound to its own output.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (status.running) "Routing is on" else "Routing is off",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (status.running) "${status.active.size} of ${rules.size} routes playing"
                        else "${rules.size} routes saved. ${devices.size} outputs connected.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (status.running) {
                        OutlinedButton(onClick = { AudioRouterService.stop(context) }) { Text("Stop routing") }
                    } else {
                        Button(onClick = { startRouting() }) { Text("Start routing") }
                    }
                    notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        "How it works: the routed app's sound is captured and replayed on the output you pick. " +
                            "Android still plays the original on the default output, so lower the media volume there " +
                            "if you hear it twice. Calls and apps that block capture cannot be routed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search apps") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (apps == null) {
            item { Text("Loading apps", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(visible, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                rule = ruleByPackage[app.packageName],
                playingOn = status.active[app.packageName],
                error = status.errors[app.packageName],
                connected = ruleByPackage[app.packageName]?.let { RoutePlanner.resolve(it, devices) != null } ?: false,
                onClick = { picking = app },
            )
        }
    }

    picking?.let { app ->
        RoutePicker(
            app = app,
            devices = devices,
            current = ruleByPackage[app.packageName],
            onPick = { device ->
                if (device == null) repository.remove(app.packageName)
                else repository.put(RouteRule(app.packageName, device.kind, device.name))
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    rule: RouteRule?,
    playingOn: String?,
    error: String?,
    connected: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (app.icon != null) {
                Image(bitmap = app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Spacer(Modifier.size(40.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val (line, color) = when {
                    error != null -> error to MaterialTheme.colorScheme.error
                    playingOn != null -> "Playing on $playingOn" to MaterialTheme.colorScheme.primary
                    rule != null && connected -> "Routes to ${rule.deviceName}" to MaterialTheme.colorScheme.secondary
                    rule != null -> "${rule.deviceName} is not connected" to MaterialTheme.colorScheme.onSurfaceVariant
                    else -> "Default output" to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 2)
            }
        }
    }
}

@Composable
private fun RoutePicker(
    app: AppEntry,
    devices: List<OutputDevice>,
    current: RouteRule?,
    onPick: (OutputDevice?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Play ${app.label} on") },
        text = {
            Column {
                PickerRow("Default output (no routing)", current == null) { onPick(null) }
                devices.forEach { device ->
                    val selected = current != null && current.kind == device.kind && current.deviceName == device.name
                    PickerRow(device.displayName, selected) { onPick(device) }
                }
                if (devices.size <= 1) {
                    Text(
                        "Connect Bluetooth, USB or wired audio to see more outputs.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label)
    }
}
