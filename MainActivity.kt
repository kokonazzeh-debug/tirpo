package com.nazzeh.turbo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

private val Red = Color(0xFFFF2D3D)
private val Bg = Color(0xFF0B0B10)
private val Card = Color(0xFF16161F)
private val Muted = Color(0xFF8A8A99)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Red, background = Bg, surface = Card,
                    onSurface = Color.White, onBackground = Color.White,
                )
            ) { App() }
        }
    }
}

/** Re-reads permission-dependent state whenever the user returns from a system settings screen. */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

@Composable
fun App() {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(
        containerColor = Bg,
        bottomBar = {
            NavigationBar(containerColor = Card) {
                listOf(
                    Triple("Home", Icons.Filled.Home, 0),
                    Triple("Games", Icons.Filled.PlayArrow, 1),
                    Triple("Tools", Icons.Filled.Settings, 2),
                ).forEach { (label, icon, i) ->
                    NavigationBarItem(
                        selected = tab == i, onClick = { tab = i },
                        icon = { Icon(icon, label) }, label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White, selectedTextColor = Red,
                            indicatorColor = Red, unselectedIconColor = Muted, unselectedTextColor = Muted,
                        )
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen()
                1 -> GamesScreen()
                else -> ToolsScreen()
            }
        }
    }
}

// ───────────────────────── Home ─────────────────────────

@Composable
fun HomeScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var stats by remember { mutableStateOf(StatsReader.read(ctx)) }
    var session by remember { mutableStateOf(store.sessionActive) }
    val tick = rememberResumeTick()

    LaunchedEffect(Unit) {
        while (true) { stats = StatsReader.read(ctx); session = store.sessionActive; delay(2000) }
    }
    LaunchedEffect(tick) { session = store.sessionActive }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("nazzeh", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.width(8.dp))
                Text("TURBO", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Red)
            }
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip20().background(
                    Brush.verticalGradient(listOf(Color(0xFF241018), Card))
                ).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Gauge(stats.ramUsedPct)
                Spacer(Modifier.height(8.dp))
                Text("${stats.ramUsedMb} / ${stats.ramTotalMb} MB in use", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = {
                        val n = Turbo.cleanRam(ctx)
                        stats = StatsReader.read(ctx)
                        Toast.makeText(ctx, "Boost sent to $n apps", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(50),
                ) { Text("  ⚡  Boost now  ", fontWeight = FontWeight.Bold) }
            }
        }
        if (session) item {
            Row(
                Modifier.fillMaxWidth().clip20().background(Card).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Game session active", fontWeight = FontWeight.Bold)
                    Text("Restore notifications & close the panel", color = Muted, fontSize = 12.sp)
                }
                OutlinedButton(onClick = { Turbo.endSession(ctx); session = false }) { Text("End") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Temp", "${"%.1f".format(stats.batteryTempC)}°C", Modifier.weight(1f))
                StatTile("Thermal", stats.thermal, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    "Battery", "${stats.batteryPct}%" + if (stats.charging) " ⚡" else "",
                    Modifier.weight(1f),
                )
                StatTile("Storage", "${stats.storageUsedPct}%", Modifier.weight(1f))
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun Modifier.clip20() = this.clip(RoundedCornerShape(20.dp))

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip20().background(Card).padding(16.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Gauge(pct: Int) {
    Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = 16.dp.toPx()
            val inset = w / 2
            val s = Size(size.width - w, size.height - w)
            drawArc(
                Color(0xFF2A2A36), 135f, 270f, false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = s,
                style = Stroke(w, cap = StrokeCap.Round),
            )
            drawArc(
                Brush.sweepGradient(listOf(Color(0xFFFF8A00), Red, Color(0xFFFF2D3D))),
                135f, 270f * pct.coerceIn(0, 100) / 100f, false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset), size = s,
                style = Stroke(w, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", fontSize = 40.sp, fontWeight = FontWeight.Black)
            Text("RAM", color = Muted, fontSize = 12.sp)
        }
    }
}

// ───────────────────────── Games ─────────────────────────

data class AppItem(val pkg: String, val label: String)

private fun launchableApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
        .filter { it.pkg != ctx.packageName }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

@Composable
fun AppIcon(pkg: String, size: Int = 56) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmap(128, 128).asImageBitmap() }.getOrNull()
    }
    if (bmp != null) Image(bmp, null, Modifier.size(size.dp).clip(RoundedCornerShape(14.dp)))
    else Box(Modifier.size(size.dp).background(Card, CircleShape))
}

@Composable
fun GamesScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var games by remember { mutableStateOf(store.games) }
    var picker by remember { mutableStateOf(false) }
    val all = remember { launchableApps(ctx) }
    val labels = remember(all) { all.associate { it.pkg to it.label } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Game Space", fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            FilledIconButton(
                onClick = { picker = true },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Red),
            ) { Icon(Icons.Filled.Add, "Add games") }
        }
        Spacer(Modifier.height(6.dp))
        Text("Tap a game to launch it with Turbo", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(16.dp))

        if (games.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No games yet.\nPress + to add your games.", color = Muted, textAlign = TextAlign.Center)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(games) { pkg ->
                    Column(
                        Modifier.clip20().background(Card)
                            .clickable {
                                if (!Turbo.startSession(ctx, pkg))
                                    Toast.makeText(ctx, "Can't open this app", Toast.LENGTH_SHORT).show()
                            }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(pkg)
                        Spacer(Modifier.height(8.dp))
                        Text(labels[pkg] ?: pkg, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    if (picker) {
        var selected by remember { mutableStateOf(games.toSet()) }
        AlertDialog(
            onDismissRequest = { picker = false },
            containerColor = Card,
            title = { Text("Choose games") },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(all) { a ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (a.pkg in selected) selected - a.pkg else selected + a.pkg
                            }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(a.pkg, 40)
                            Spacer(Modifier.width(12.dp))
                            Text(a.label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Checkbox(a.pkg in selected, { selected = if (it) selected + a.pkg else selected - a.pkg })
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.games = selected.toList(); games = store.games; picker = false
                }) { Text("Save", color = Red) }
            },
            dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancel") } },
        )
    }
}

// ───────────────────────── Tools ─────────────────────────

@Composable
fun ToolsScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val tick = rememberResumeTick()

    var dnd by remember { mutableStateOf(store.dnd && Turbo.hasDndAccess(ctx)) }
    var overlay by remember { mutableStateOf(store.overlay && Turbo.hasOverlayPermission(ctx)) }
    var clean by remember { mutableStateOf(store.cleanFirst) }
    LaunchedEffect(tick) {
        dnd = store.dnd && Turbo.hasDndAccess(ctx)
        overlay = store.overlay && Turbo.hasOverlayPermission(ctx)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(6.dp))
        Text("Turbo tools", fontSize = 24.sp, fontWeight = FontWeight.Black)

        ToolRow(
            "Do Not Disturb", "Silence calls & notifications while you play (alarms still ring)", dnd,
        ) { on ->
            if (on && !Turbo.hasDndAccess(ctx)) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                store.dnd = true
            } else { store.dnd = on; dnd = on }
        }
        ToolRow(
            "Floating panel", "RAM, temperature and battery on top of your game", overlay,
        ) { on ->
            if (on && !Turbo.hasOverlayPermission(ctx)) {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
                store.overlay = true
            } else { store.overlay = on; overlay = on }
        }
        ToolRow("Clean RAM before launch", "Ends background apps right before the game opens", clean) {
            store.cleanFirst = it; clean = it
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Note: without root, Android doesn't let apps raise CPU/GPU speed or touch rate. " +
                "Turbo frees memory, silences interruptions and shows live temperature.",
            color = Muted, fontSize = 12.sp,
        )
    }
}

@Composable
fun ToolRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip20().background(Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold)
            Text(desc, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Red, checkedThumbColor = Color.White),
        )
    }
}
