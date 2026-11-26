package com.mifarenfc.counter

import org.json.JSONObject

/**
 * Motor GENÉRICO sobre volcados de MifareClassicTool (.json o .mct/.txt).
 *
 * No contiene nada específico del formato del contador: delega toda la
 * redundancia en `scheme` (ver Redundancy.kt). Para adaptar la app a otras
 * tarjetas se edita SOLO Redundancy.kt; este fichero no cambia. Ver REDUNDANCY.md.
 */
object ValueBlock {

    /** Esquema de redundancia activo. La app usa el vacío hasta que lo rellenes. */
    var scheme: RedundancyScheme = EmptyScheme

    private val HEX32 = Regex("^[0-9A-Fa-f]{32}$")

    data class Dump(val format: String, val blocks: LinkedHashMap<Int, String>)
    data class Found(val block: Int, val hex: String, val value: Int)
    data class Change(
        val block: Int, val oldHex: String, val newHex: String,
        val oldValue: Int, val newValue: Int
    )
    data class Result(
        val text: String, val format: String,
        val changed: List<Change>, val counterBlocks: List<Found>
    )

    /** ¿Es el bloque un trailer de sector? (1K/Mini/2K/4K) */
    fun isTrailer(block: Int): Boolean =
        if (block < 128) block % 4 == 3 else (block - 128) % 16 == 15

    /** Índice de sector de un bloque. */
    fun sectorOf(block: Int): Int =
        if (block < 128) block / 4 else 32 + (block - 128) / 16

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

    /** Bloques que el esquema activo reconoce como contadores (excluye bloque 0 y trailers). */
    fun findCounterBlocks(blocks: Map<Int, String>): List<Found> {
        val res = ArrayList<Found>()
        for (k in blocks.keys.sorted()) {
            if (k == 0 || isTrailer(k)) continue
            val hex = blocks[k] ?: continue
            if (!HEX32.matches(hex)) continue
            val d = scheme.decode(hex) ?: continue
            res.add(Found(k, hex, d.value))
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
     * selected = bloques a modificar; null = todos los contadores detectados.
     */
    fun setCounter(text: String, newValue: Int, selected: List<Int>? = null): Result {
        val dump = parseDump(text)
        val cbs = findCounterBlocks(dump.blocks)
        val targets = if (selected == null) cbs else cbs.filter { selected.contains(it.block) }
        val changes = LinkedHashMap<Int, String>()
        val changed = ArrayList<Change>()
        for (v in targets) {
            val newHex = scheme.encode(v.hex, newValue)
            if (!HEX32.matches(newHex)) throw IllegalArgumentException(
                "encode() devolvió un bloque inválido ($newHex) para el bloque ${v.block}."
            )
            changes[v.block] = newHex
            changed.add(Change(v.block, v.hex, newHex, v.value, newValue))
        }
        return Result(rewriteDump(text, dump.format, changes), dump.format, changed, cbs)
    }
}
