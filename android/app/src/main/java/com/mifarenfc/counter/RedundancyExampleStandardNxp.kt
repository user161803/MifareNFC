package com.mifarenfc.counter

/*
 * EJEMPLO de esquema: value block ESTÁNDAR de MIFARE Classic (NXP).
 *
 * La app NO lo usa (usa EmptyScheme). Está aquí como referencia completa y
 * funcional para implementar el tuyo. Para activarlo, copia la lógica de
 * decode/encode a EmptyScheme en Redundancy.kt (o cambia el esquema activo en
 * ValueBlock.scheme).
 *
 *   bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
 *          [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
 */
object StandardNxpScheme : RedundancyScheme {
    override val name = "estándar NXP (value block)"

    private val HEX32 = Regex("^[0-9A-Fa-f]{32}$")

    override fun decode(hex: String): Decoded? {
        if (!HEX32.matches(hex)) return null
        val b = hexToBytes(hex)
        for (i in 0 until 4) {
            if (b[i] != b[i + 8]) return null            // Valor == copia
            if ((b[i] xor b[i + 4]) != 0xFF) return null // Valor XOR ~Valor == FF
        }
        if (b[12] != b[14] || b[13] != b[15]) return null
        if ((b[12] xor b[13]) != 0xFF) return null       // adr XOR ~adr == FF
        return Decoded(readLE32(b, 0))
    }

    override fun encode(hex: String, newValue: Int): String {
        val upper = hex == hex.uppercase()
        val adr = if (HEX32.matches(hex)) hexToBytes(hex)[12] else 0 // conserva dirección
        val v = writeLE32(newValue)
        val nv = IntArray(4) { v[it].inv() and 0xFF }
        val a = adr and 0xFF
        val na = adr.inv() and 0xFF
        return bytesToHex(
            intArrayOf(
                v[0], v[1], v[2], v[3], nv[0], nv[1], nv[2], nv[3],
                v[0], v[1], v[2], v[3], a, na, a, na
            ),
            upper
        )
    }
}
