package com.mifarenfc.counter

import android.app.Activity
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado compartido observable por Compose. */
class CardState {
    var counter by mutableStateOf("10")

    // Pestaña archivo
    var fileName by mutableStateOf<String?>(null)
    var fileText by mutableStateOf<String?>(null)
    var fileResult by mutableStateOf<ValueBlock.Result?>(null)
    var fileError by mutableStateOf<String?>(null)

    // Pestaña NFC
    var nfcStatus by mutableStateOf("Acerca una tarjeta MIFARE Classic al móvil.")
    var nfcBlocks by mutableStateOf<LinkedHashMap<Int, String>?>(null)
    var nfcValueBlocks by mutableStateOf<List<ValueBlock.Found>>(emptyList())
    var nfcUid by mutableStateOf<String?>(null)
    var lastTag: Tag? = null
    var sectorKeys: Map<Int, ByteArray> = emptyMap()
    var writing by mutableStateOf(false)
}

class MainActivity : ComponentActivity(), NfcAdapter.ReaderCallback {

    private var nfcAdapter: NfcAdapter? = null
    private val state = CardState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        setContent { MaterialTheme(colorScheme = lightColorScheme()) { App(state, ::writeTag) } }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this, this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    /** Llamado por el sistema en un hilo de fondo cuando se detecta una tarjeta. */
    override fun onTagDiscovered(tag: Tag) {
        lifecycleScope.launch {
            runOnUiThread { state.nfcStatus = "Leyendo tarjeta…" }
            try {
                val r = withContext(Dispatchers.IO) { NfcCardIo.read(tag) }
                val vbs = ValueBlock.findValueBlocks(r.blocks)
                runOnUiThread {
                    state.lastTag = tag
                    state.sectorKeys = r.sectorKeys
                    state.nfcBlocks = r.blocks
                    state.nfcValueBlocks = vbs
                    state.nfcUid = r.uid
                    val extra = if (r.unreadableSectors.isEmpty()) ""
                        else " · sectores sin clave: ${r.unreadableSectors.joinToString(",")}"
                    state.nfcStatus = "UID ${r.uid} · ${r.sectorCount} sectores · " +
                        "${vbs.size} value block(s)$extra"
                }
            } catch (e: Exception) {
                runOnUiThread { state.nfcStatus = "Error: ${e.message}" }
            }
        }
    }

    /** Escribe el contador en los value blocks de la última tarjeta leída. */
    private fun writeTag() {
        val tag = state.lastTag ?: return
        val value = state.counter.trim().toIntOrNull() ?: return
        val vbs = state.nfcValueBlocks
        if (vbs.isEmpty()) return
        val changes = LinkedHashMap<Int, String>()
        for (v in vbs) changes[v.block] = ValueBlock.buildValueBlock(value, v.adr, true)
        state.writing = true
        state.nfcStatus = "Escribiendo… mantén la tarjeta pegada al móvil."
        lifecycleScope.launch {
            try {
                val out = withContext(Dispatchers.IO) {
                    NfcCardIo.write(tag, changes, state.sectorKeys)
                }
                // Releer para confirmar
                val reread = withContext(Dispatchers.IO) { NfcCardIo.read(tag) }
                val vbs2 = ValueBlock.findValueBlocks(reread.blocks)
                runOnUiThread {
                    state.nfcBlocks = reread.blocks
                    state.nfcValueBlocks = vbs2
                    state.writing = false
                    state.nfcStatus = if (out.failed.isEmpty())
                        "✓ Escritos ${out.written.size} bloque(s) con valor $value."
                    else "Escritos ${out.written.size}; fallos: " +
                        out.failed.entries.joinToString(", ") { "b${it.key} (${it.value})" }
                }
            } catch (e: Exception) {
                runOnUiThread { state.writing = false; state.nfcStatus = "Error al escribir: ${e.message}" }
            }
        }
    }
}

private val MONO = FontFamily.Monospace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(state: CardState, onWrite: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val ctx = LocalContext.current

    // Recalcular resultado de archivo cuando cambia texto o contador
    LaunchedEffect(state.fileText, state.counter) {
        val text = state.fileText
        state.fileError = null
        state.fileResult = null
        if (text == null) return@LaunchedEffect
        val v = state.counter.trim().toIntOrNull()
        if (v == null) { state.fileError = "Introduce un valor entero válido."; return@LaunchedEffect }
        try {
            val r = ValueBlock.setCounter(text, v)
            if (r.valueBlocks.isEmpty()) state.fileError = "No se encontró ningún value block válido."
            else state.fileResult = r
        } catch (e: Exception) { state.fileError = e.message }
    }

    val openFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().use { it.readText() }
                state.fileName = queryName(ctx, uri)
                state.fileText = text
            } catch (e: Exception) { state.fileError = "No se pudo leer: ${e.message}" }
        }
    }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val r = state.fileResult
        if (uri != null && r != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(r.text.toByteArray()) }
                state.fileError = "Guardado."
            } catch (e: Exception) { state.fileError = "No se pudo guardar: ${e.message}" }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Contador MIFARE Classic") }) }) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            OutlinedTextField(
                value = state.counter,
                onValueChange = { state.counter = it },
                label = { Text("Nuevo valor del contador") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Archivo") })
                Tab(tab == 1, { tab = 1 }, text = { Text("NFC") })
            }
            Spacer(Modifier.height(16.dp))
            if (tab == 0) FileTab(state, { openFile.launch(arrayOf("*/*")) },
                { state.fileResult?.let { saveFile.launch(outName(state.fileName, state.counter)) } })
            else NfcTab(state, onWrite)
        }
    }
}

@Composable
private fun FileTab(state: CardState, onOpen: () -> Unit, onSave: () -> Unit) {
    Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Abrir volcado (.json / .mct / .txt)") }
    state.fileName?.let {
        Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.titleMedium)
    }
    state.fileError?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }
    val r = state.fileResult
    if (r != null) {
        Spacer(Modifier.height(8.dp))
        Text("Formato ${if (r.format == "json") "JSON MCT" else "texto .mct"} · ${r.valueBlocks.size} value block(s)",
            style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        for (v in r.valueBlocks) {
            val c = r.changed.find { it.block == v.block }
            BlockCard(v, c)
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onSave, enabled = r.changed.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Guardar copia con contador ${state.counter}")
        }
    }
}

@Composable
private fun NfcTab(state: CardState, onWrite: () -> Unit) {
    Text(state.nfcStatus)
    val vbs = state.nfcValueBlocks
    if (vbs.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        val value = state.counter.trim().toIntOrNull()
        for (v in vbs) {
            val c = value?.let {
                ValueBlock.Change(v.block, v.hex, ValueBlock.buildValueBlock(it, v.adr, true), v.value, it, v.adr)
            }
            BlockCard(v, c)
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onWrite,
            enabled = !state.writing && value != null,
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (state.writing) "Escribiendo…" else "Escribir contador en la tarjeta") }
        Spacer(Modifier.height(8.dp))
        Text("La escritura necesita que la tarjeta permita escribir con sus claves actuales (config. de transporte por defecto lo permite).",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun BlockCard(v: ValueBlock.Found, c: ValueBlock.Change?) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bloque ${v.block}", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text("(sector ${ValueBlock.sectorOf(v.block)})",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                Text(if (c != null) "${v.value} → ${c.newValue}" else "${v.value}")
            }
            Spacer(Modifier.height(6.dp))
            Text(fmtHex(v.hex), fontFamily = MONO, fontSize = 13.sp, overflow = TextOverflow.Visible)
            if (c != null) {
                Spacer(Modifier.height(4.dp))
                Text(fmtHex(c.newHex), fontFamily = MONO, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun fmtHex(h: String): String {
    if (h.length != 32) return h
    return "${h.substring(0,8)} ${h.substring(8,16)} ${h.substring(16,24)} " +
        "${h.substring(24,26)} ${h.substring(26,28)} ${h.substring(28,30)} ${h.substring(30,32)}"
}

private fun outName(name: String?, value: String): String {
    val base = (name ?: "tarjeta")
    val dot = base.lastIndexOf('.')
    return if (dot > 0) "${base.substring(0, dot)}_contador$value${base.substring(dot)}"
    else "${base}_contador$value.json"
}

private fun queryName(ctx: android.content.Context, uri: Uri): String {
    var result = "volcado"
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && c.moveToFirst()) result = c.getString(idx)
    }
    return result
}
