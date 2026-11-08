package com.nicky.rincewinder.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/** Tři obrazovky z UX_WIREFRAMES §8: složka → oprávnění → QR. Odmítnutí nezacyklí, Domů pak ukáže kartu. */
@Composable
fun Onboarding(vm: AppViewModel, onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(if (vm.prefs.treeUri == null) 0 else 1) }
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        when (step) {
            0 -> {
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                    if (uri != null) {
                        ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        vm.prefs.treeUri = uri.toString()
                        step = 1
                    }
                }
                Text("Kam ukládat RAWy", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Každý přenos dostane podsložku s datem. Vyber složku Pictures/Rincewinder (nebo ji vytvoř) — je to jediná věc, kterou tu budeš psát.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = { pick.launch(null) }, Modifier.fillMaxWidth().height(64.dp)) { Text("Vybrat složku") }
            }
            1 -> {
                val ask = rememberPermissionRequester { step = 2 }
                Text("Tělo je v místní síti", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Android se zeptá na přístup k místní síti a na notifikace. Bez sítě appka tělo nikdy nenajde; bez notifikací neuvidíš postup přenosu.")
                Spacer(Modifier.height(24.dp))
                Button(onClick = ask, Modifier.fillMaxWidth().height(64.dp)) { Text("Povolit obojí") }
            }
            else -> {
                var manual by remember { mutableStateOf(false) }
                var ip by remember { mutableStateOf(vm.prefs.lastIp ?: "") }
                val scanner = remember { GmsBarcodeScanning.getClient(ctx) }
                Text("Naskenuj QR z displeje těla", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                Text("Na těle: MENU → Aplikace → Rincewinder Agent. Na displeji je QR s adresou a klíčem.")
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = {
                        scanner.startScan().addOnSuccessListener { c ->
                            if (vm.pairFromQr(c.rawValue ?: "")) onDone() else vm.say("To není QR z displeje Agenta")
                        }.addOnFailureListener { vm.say("Sken selhal: ${it.message}") }
                    },
                    Modifier.fillMaxWidth().height(64.dp),
                ) { Text("Skenovat QR") }
                Spacer(Modifier.height(8.dp))
                if (manual) {
                    OutlinedTextField(ip, { ip = it }, label = { Text("IP těla") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { vm.setManualIp(ip); onDone() }) { Text("Použít IP") }
                } else {
                    TextButton(onClick = { manual = true }) { Text("Zadat IP ručně") }
                }
                TextButton(onClick = onDone) { Text("Přeskočit (najdu tělo skenem sítě)") }
            }
        }
    }
}
