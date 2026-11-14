package com.nicky.rincewinder.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.nicky.rincewinder.body.BodyFinder

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) vm.handleIntent(intent)
        setContent { RincewinderTheme { Root(vm) } }
    }

    /** Sdílet / Otevřít v do už běžící appky (launchMode singleTask). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (vm.prefs.onboarded) vm.refresh()
    }
}

enum class Tab(val label: String) { DOMU("Domů"), SOUBORY("Soubory"), APKY("Apky"), DIAG("Diagnostika") }

@Composable
private fun Root(vm: AppViewModel) {
    var onboarded by rememberSaveable { mutableStateOf(vm.prefs.onboarded) }
    if (!onboarded) {
        Onboarding(vm) { vm.prefs.onboarded = true; onboarded = true; vm.refresh() }
        return
    }
    val snack = remember { SnackbarHostState() }
    val toast by vm.toast.collectAsState()
    LaunchedEffect(toast) { toast?.let { snack.showSnackbar(it); vm.toastShown() } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val goTo by vm.goToTab.collectAsState()
    LaunchedEffect(goTo) { goTo?.let { tab = it; vm.tabShown() } }
    val ctx = LocalContext.current

    val scanner = remember { GmsBarcodeScanning.getClient(ctx) }
    val scanQr: () -> Unit = {
        scanner.startScan().addOnSuccessListener { code ->
            val txt = code.rawValue ?: ""
            if (!vm.pairFromQr(txt)) vm.say("To není QR z displeje Agenta")
        }.addOnFailureListener { vm.say("Sken selhal: ${it.message}") }
    }

    // pozadí „Dvě světla“: modrá hodina nahoře, sodík dole, jemné zrno
    Box(Modifier.fillMaxSize().background(Palette.Sky).grain()) { Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            val body by vm.body.collectAsState()
            StatusStrip(body, Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 6.dp))
        },
        bottomBar = {
            Column {
                Joint()
                NavigationBar(containerColor = Color(0xCC0E0B08), tonalElevation = 0.dp) {
                    Tab.entries.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = {
                                Icon(
                                    when (t) { Tab.DOMU -> Icons.Default.Home; Tab.SOUBORY -> Icons.Default.GridView; Tab.APKY -> Icons.Default.Apps; Tab.DIAG -> Icons.Default.MonitorHeart },
                                    contentDescription = t.label,
                                )
                            },
                            label = { Text(t.label.uppercase(), letterSpacing = 0.12.em, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Palette.SodiumLight, selectedTextColor = Palette.SodiumLight,
                                indicatorColor = Palette.Sodium.copy(alpha = 0.16f),
                                unselectedIconColor = Palette.MuteWarm, unselectedTextColor = Palette.MuteWarm,
                            ),
                        )
                    }
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (Tab.entries[tab]) {
            Tab.DOMU -> HomeScreen(vm, m, onPickManually = { tab = 1 }, onScanQr = scanQr, onOpenSettings = { openAppSettings(ctx) })
            Tab.SOUBORY -> FilesScreen(vm, m)
            Tab.APKY -> AppsScreen(vm, m)
            Tab.DIAG -> DiagScreen(vm, m)
        }
    } }
}

fun openAppSettings(ctx: android.content.Context) {
    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Oprávnění z onboardingu 2/3: místní síť + notifikace (dva systémové dialogy za sebou). */
@Composable
fun rememberPermissionRequester(onDone: (granted: Boolean) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        onDone(res[BodyFinder.PERMISSION] != false)
    }
    return { launcher.launch(arrayOf(BodyFinder.PERMISSION, Manifest.permission.POST_NOTIFICATIONS)) }
}
