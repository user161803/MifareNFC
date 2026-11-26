package com.mifarenfc.counter

import android.nfc.Tag
import android.nfc.tech.MifareClassic

/**
 * Lectura y escritura de tarjetas MIFARE Classic físicas por NFC.
 *
 * La lectura intenta autenticar cada sector con una lista de claves conocidas
 * (clave A y luego clave B). La escritura reautentica y reescribe SOLO los
 * bloques indicados con writeBlock (16 bytes directos); nunca toca el bloque 0
 * ni los trailers.
 */
object NfcCardIo {

    /** Claves por defecto más habituales de MIFARE Classic. */
    val DEFAULT_KEYS: List<ByteArray> = listOf(
        "FFFFFFFFFFFF", "000000000000", "A0A1A2A3A4A5", "D3F7D3F7D3F7",
        "B0B1B2B3B4B5", "4D3A99C351DD", "1A982C7E459A", "AABBCCDDEEFF",
        "714C5C886E97", "587EE5F9350F", "A0478CC39091", "533CB6C723F6",
        "8FD0A4F256E9"
    ).map { hex -> ByteArray(6) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() } }

    data class ReadResult(
        /** Bloque global -> hex (32 chars) para los sectores legibles. */
        val blocks: LinkedHashMap<Int, String>,
        /** Sector -> clave que funcionó (para reutilizarla al escribir). */
        val sectorKeys: HashMap<Int, ByteArray>,
        val uid: String,
        val sectorCount: Int,
        val unreadableSectors: List<Int>
    )

    private fun bytesToHex(b: ByteArray): String {
        val sb = StringBuilder()
        for (x in b) sb.append(((x.toInt() and 0xFF).toString(16)).padStart(2, '0'))
        return sb.toString().uppercase()
    }

    /** Autentica un sector probando clave A y luego clave B; devuelve la clave usada o null. */
    private fun authenticate(mc: MifareClassic, sector: Int, keys: List<ByteArray>): ByteArray? {
        for (k in keys) if (mc.authenticateSectorWithKeyA(sector, k)) return k
        for (k in keys) if (mc.authenticateSectorWithKeyB(sector, k)) return k
        return null
    }

    fun read(tag: Tag, extraKeys: List<ByteArray> = emptyList()): ReadResult {
        val mc = MifareClassic.get(tag)
            ?: throw IllegalArgumentException("La tarjeta no es MIFARE Classic.")
        val keys = extraKeys + DEFAULT_KEYS
        val blocks = LinkedHashMap<Int, String>()
        val sectorKeys = HashMap<Int, ByteArray>()
        val unreadable = ArrayList<Int>()
        mc.connect()
        try {
            mc.timeout = 2000
            for (s in 0 until mc.sectorCount) {
                val key = authenticate(mc, s, keys)
                val first = mc.sectorToBlock(s)
                val count = mc.getBlockCountInSector(s)
                if (key == null) { unreadable.add(s); continue }
                sectorKeys[s] = key
                for (b in 0 until count) {
                    val idx = first + b
                    try {
                        blocks[idx] = bytesToHex(mc.readBlock(idx))
                    } catch (e: Exception) {
                        // bloque ilegible (p. ej. trailer con acceso restringido): se omite
                    }
                }
            }
        } finally {
            mc.close()
        }
        return ReadResult(blocks, sectorKeys, bytesToHex(tag.id), mc.sectorCount, unreadable)
    }

    data class WriteOutcome(val written: List<Int>, val failed: Map<Int, String>)

    /**
     * Escribe los bloques indicados (idx -> hex de 32 chars).
     * sectorKeys: claves obtenidas en la lectura; se prueban primero.
     */
    fun write(
        tag: Tag,
        changes: Map<Int, String>,
        sectorKeys: Map<Int, ByteArray>,
        extraKeys: List<ByteArray> = emptyList()
    ): WriteOutcome {
        val mc = MifareClassic.get(tag)
            ?: throw IllegalArgumentException("La tarjeta no es MIFARE Classic.")
        val written = ArrayList<Int>()
        val failed = LinkedHashMap<Int, String>()
        mc.connect()
        try {
            mc.timeout = 2000
            for ((idx, hex) in changes.toSortedMap()) {
                val sector = mc.blockToSector(idx)
                if (idx == mc.sectorToBlock(sector) + mc.getBlockCountInSector(sector) - 1) {
                    failed[idx] = "es un trailer: no se escribe"; continue
                }
                if (idx == 0) { failed[idx] = "bloque de fabricante: no se escribe"; continue }
                val tryKeys = (listOfNotNull(sectorKeys[sector]) + extraKeys + NfcCardIo.DEFAULT_KEYS)
                val key = authenticate(mc, sector, tryKeys)
                if (key == null) { failed[idx] = "sin clave válida para el sector $sector"; continue }
                try {
                    val data = ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
                    mc.writeBlock(idx, data)
                    written.add(idx)
                } catch (e: Exception) {
                    failed[idx] = e.message ?: "error de escritura"
                }
            }
        } finally {
            mc.close()
        }
        return WriteOutcome(written, failed)
    }
}
