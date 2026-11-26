package com.mifarenfc.counter

import org.json.JSONObject

/**
 * Lógica de redundancia de los value blocks de MIFARE Classic.
 *
 * -----------------------------------------------------------------------------
 *  ESTE ES EL ÚNICO MÓDULO A CAMBIAR SI LAS TARJETAS USAN OTRO ESQUEMA.
 * -----------------------------------------------------------------------------
 *
 * Formato estándar NXP (el de los volcados de ejemplo del repo):
 *
 *   bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
 *          [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
 *
 *  - Valor  : entero de 4 bytes little-endian.
 *  - ~Valor : complemento a uno del valor.
 *  - Valor  : copia.
 *  - adr/~adr: byte de dirección y su complemento, repetidos.
 *
 * Es un puerto 1:1 de app/valueblock.js para que web y Android calculen igual.
 */
object ValueBlock {

    private val HEX32 = Regex("^[0-9A-Fa-f]{32}$")

    data class Parsed(val value: Int, val adr: Int)
    data class Dump(val format: String, val blocks: LinkedHashMap<Int, String>)
    data class Found(val block: Int, val hex: String, val value: Int, val adr: Int)
    data class Change(
        val block: Int, val oldHex: String, val newHex: String,
        val oldValue: Int, val newValue: Int, val adr: Int
    )
    data class Result(
        val text: String, val format: String,
        val changed: List<Change>, val valueBlocks: List<Found>
    )

    fun hexToBytes(hex: String): IntArray {
        val out = IntArray(hex.length / 2)
        for (i in out.indices) out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16)
        return out
    }

    fun bytesToHex(bytes: IntArray, upper: Boolean): String {
        val sb = StringBuilder()
        for (b in bytes) sb.append((b and 0xFF).toString(16).padStart(2, '0'))
        return if (upper) sb.toString().uppercase() else sb.toString()
    }

    private fun readLE32(b: IntArray, o: Int): Int =
        (b[o] or (b[o + 1] shl 8) or (b[o + 2] shl 16) or (b[o + 3] shl 24))

    private fun writeLE32(v: Int): IntArray =
        intArrayOf(v and 0xFF, (v ushr 8) and 0xFF, (v ushr 16) and 0xFF, (v ushr 24) and 0xFF)

    /** ¿Es el bloque un trailer de sector? (1K/Mini/2K/4K) */
    fun isTrailer(block: Int): Boolean =
        if (block < 128) block % 4 == 3 else (block - 128) % 16 == 15

    /** Índice de sector de un bloque. */
    fun sectorOf(block: Int): Int =
        if (block < 128) block / 4 else 32 + (block - 128) / 16

    /** Devuelve {value, adr} si el bloque es un value block coherente; si no, null. */
    fun parseValueBlock(hex: String): Parsed? {
        if (!HEX32.matches(hex)) return null
        val b = hexToBytes(hex)
        for (i in 0 until 4) {
            if (b[i] != b[i + 8]) return null
            if ((b[i] xor b[i + 4]) != 0xFF) return null
        }
        if (b[12] != b[14] || b[13] != b[15]) return null
        if ((b[12] xor b[13]) != 0xFF) return null
        return Parsed(readLE32(b, 0), b[12])
    }

    /** Construye un value block con el valor dado y la redundancia recalculada. */
    fun buildValueBlock(value: Int, adr: Int, upper: Boolean): String {
        val v = writeLE32(value)
        val nv = IntArray(4) { (v[it].inv()) and 0xFF }
        val a = adr and 0xFF
        val na = a.inv() and 0xFF
        val all = intArrayOf(
            v[0], v[1], v[2], v[3], nv[0], nv[1], nv[2], nv[3],
            v[0], v[1], v[2], v[3], a, na, a, na
        )
        return bytesToHex(all, upper)
    }

    /** Lee un volcado JSON (MifareClassicTool) o texto .mct y devuelve sus bloques. */
    fun parseDump(text: String): Dump {
        val trimmed = text.trimStart('﻿').trim()
        val blocks = LinkedHashMap<Int, String>()
        if (trimmed.startsWith("{")) {
            val data = JSONObject(trimmed)
            if (!data.has("blocks")) throw IllegalArgumentException(
                "El JSON no tiene la sección \"blocks\" de MifareClassicTool."
            )
            val b = data.getJSONObject("blocks")
            for (k in b.keys()) blocks[k.toInt()] = b.getString(k)
            return Dump("json", blocks)
        }
        var sector = -1
        var inSector = 0
        var found = false
        for (raw in text.split(Regex("\r?\n"))) {
            val line = raw.trim()
            val m = Regex("^\\+Sector:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(line)
            if (m != null) { sector = m.groupValues[1].toInt(); inSector = 0; found = true; continue }
            if (sector < 0 || line.isEmpty()) continue
            val first = if (sector < 32) sector * 4 else 128 + (sector - 32) * 16
            blocks[first + inSector] = line
            inSector++
        }
        if (!found) throw IllegalArgumentException(
            "Formato no reconocido: ni JSON de MCT ni volcado .mct con \"+Sector:\"."
        )
        return Dump("mct", blocks)
    }

    /** Bloques que son value blocks válidos (excluye bloque 0 y trailers). */
    fun findValueBlocks(blocks: Map<Int, String>): List<Found> {
        val res = ArrayList<Found>()
        for (k in blocks.keys.sorted()) {
            if (k == 0 || isTrailer(k)) continue
            val hex = blocks[k] ?: continue
            val vb = parseValueBlock(hex) ?: continue
            res.add(Found(k, hex, vb.value, vb.adr))
        }
        return res
    }

    /** Reescribe el texto original cambiando SOLO los bloques indicados, byte a byte. */
    fun rewriteDump(text: String, format: String, changes: Map<Int, String>): String {
        if (format == "json") {
            var out = text
            for ((k, newHex) in changes) {
                val re = Regex("(\"$k\"\\s*:\\s*\")([0-9A-Fa-f]{32})(\")")
                if (!re.containsMatchIn(out)) throw IllegalArgumentException(
                    "No se encontró el bloque $k en el JSON."
                )
                out = re.replace(out) { mr -> mr.groupValues[1] + newHex + mr.groupValues[3] }
            }
            return out
        }
        // mct: reconstruir conservando saltos de línea
        val parts = text.split(Regex("(?<=\n)|(?=\n)")).toMutableList()
        var sector = -1
        var inSector = 0
        for (i in parts.indices) {
            val line = parts[i].trim()
            if (line.isEmpty() || line == "\n") continue
            val m = Regex("^\\+Sector:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(line)
            if (m != null) { sector = m.groupValues[1].toInt(); inSector = 0; continue }
            if (sector < 0) continue
            val first = if (sector < 32) sector * 4 else 128 + (sector - 32) * 16
            val idx = first + inSector
            if (changes.containsKey(idx)) parts[i] = parts[i].replace(line, changes[idx]!!)
            inSector++
        }
        return parts.joinToString("")
    }

    /**
     * Operación completa sobre un volcado de texto.
     * selected = bloques a modificar; null = todos los value blocks detectados.
     */
    fun setCounter(text: String, newValue: Int, selected: List<Int>? = null): Result {
        val dump = parseDump(text)
        val vbs = findValueBlocks(dump.blocks)
        val targets = if (selected == null) vbs else vbs.filter { selected.contains(it.block) }
        val changes = LinkedHashMap<Int, String>()
        val changed = ArrayList<Change>()
        for (v in targets) {
            val upper = v.hex == v.hex.uppercase()
            val newHex = buildValueBlock(newValue, v.adr, upper)
            changes[v.block] = newHex
            changed.add(Change(v.block, v.hex, newHex, v.value, newValue, v.adr))
        }
        return Result(rewriteDump(text, dump.format, changes), dump.format, changed, vbs)
    }
}
