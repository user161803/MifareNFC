package com.mifarenfc.counter

/*
 * ============================================================================
 *  ESQUEMA DE REDUNDANCIA  —  RELLENA ESTE FICHERO PARA TUS TARJETAS
 * ============================================================================
 *
 * El resto de la app (UI, lectura de volcados, NFC) es GENÉRICO y no sabe nada
 * de cómo se codifica el contador. Todo lo específico de tu tarjeta vive aquí,
 * en dos funciones:
 *
 *   decode(hex)            -> Decoded(value) si `hex` es un bloque contador de
 *                             tu esquema, o null si no lo es.
 *   encode(hex, newValue)  -> nuevo bloque (32 hex) con `newValue` escrito y la
 *                             redundancia recalculada.
 *
 * `hex` es SIEMPRE un bloque de 16 bytes en hexadecimal (32 caracteres).
 *
 * Esta lógica debe coincidir con la de la web (app/redundancy.js). Hay un
 * ejemplo completo y funcional (estándar NXP) en RedundancyExampleStandardNxp.kt
 * y la guía paso a paso en REDUNDANCY.md.
 * ----------------------------------------------------------------------------
 */

/** Resultado de decodificar un bloque contador. */
data class Decoded(val value: Int)

/** Contrato de un esquema de redundancia. */
interface RedundancyScheme {
    /** Nombre mostrado en la interfaz. */
    val name: String

    /**
     * ¿Es `hex` (32 caracteres) un bloque contador de TU esquema?
     * Devuelve Decoded(valorActual) si lo es, o null si no.
     * Debe ser estricto: null para cualquier bloque que no sea un contador.
     */
    fun decode(hex: String): Decoded?

    /**
     * Construye el nuevo bloque con `newValue` y la redundancia recalculada.
     * Recibe el bloque original por si necesitas conservar bytes de él.
     */
    fun encode(hex: String, newValue: Int): String
}

/* ---- Helpers de bytes (opcionales) --------------------------------------- */

internal fun hexToBytes(hex: String): IntArray {
    val out = IntArray(hex.length / 2)
    for (i in out.indices) out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16)
    return out
}
internal fun bytesToHex(bytes: IntArray, upper: Boolean): String {
    val sb = StringBuilder()
    for (b in bytes) sb.append((b and 0xFF).toString(16).padStart(2, '0'))
    return if (upper) sb.toString().uppercase() else sb.toString()
}
internal fun readLE32(b: IntArray, o: Int): Int =
    (b[o] or (b[o + 1] shl 8) or (b[o + 2] shl 16) or (b[o + 3] shl 24))
internal fun writeLE32(v: Int): IntArray =
    intArrayOf(v and 0xFF, (v ushr 8) and 0xFF, (v ushr 16) and 0xFF, (v ushr 24) and 0xFF)

/* -------------------------------------------------------------------------- */

/** Esquema VACÍO. Es el que usa la app por defecto: rellénalo. */
object EmptyScheme : RedundancyScheme {
    override val name = "sin implementar"

    override fun decode(hex: String): Decoded? {
        // TODO: detecta y lee el contador de tus tarjetas.
        return null
    }

    override fun encode(hex: String, newValue: Int): String {
        // TODO: escribe el contador + redundancia de tus tarjetas.
        throw NotImplementedError(
            "Esquema de redundancia sin implementar: edita Redundancy.kt " +
                "(funciones decode y encode). Ver REDUNDANCY.md."
        )
    }
}
