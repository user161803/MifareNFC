/*
 * EJEMPLO de esquema de redundancia: value block ESTÁNDAR de MIFARE Classic (NXP).
 *
 * Este fichero NO lo carga la app (index.html carga redundancy.js). Está aquí
 * como:
 *   1. referencia completa y funcional para implementar el tuyo, y
 *   2. esquema contra el que corren los tests (app/test.js).
 *
 * Para USARLO en la app en lugar del esquema vacío, copia las funciones
 * `decode` y `encode` de aquí a app/redundancy.js (y su equivalente Kotlin).
 *
 * Formato:
 *   bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
 *          [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
 *   - Valor : entero de 4 bytes little-endian.
 *   - ~Valor: complemento a uno del valor.
 *   - Valor : copia.
 *   - adr/~adr: byte de dirección y su complemento, repetidos.
 */
(function (root) {
  'use strict';

  const HEX32 = /^[0-9A-Fa-f]{32}$/;

  function hexToBytes(hex) {
    const out = [];
    for (let i = 0; i < hex.length; i += 2) out.push(parseInt(hex.substr(i, 2), 16));
    return out;
  }
  function bytesToHex(bytes, upper) {
    const s = bytes.map(b => (b & 0xFF).toString(16).padStart(2, '0')).join('');
    return upper ? s.toUpperCase() : s;
  }
  function readLE32(b, o) {
    return (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) | 0;
  }
  function writeLE32(v) {
    const u = v >>> 0;
    return [u & 0xFF, (u >>> 8) & 0xFF, (u >>> 16) & 0xFF, (u >>> 24) & 0xFF];
  }

  const RedundancyScheme = {
    name: 'estándar NXP (value block)',

    decode: function (hex) {
      if (!HEX32.test(hex)) return null;
      const b = hexToBytes(hex);
      for (let i = 0; i < 4; i++) {
        if (b[i] !== b[i + 8]) return null;           // Valor == copia
        if ((b[i] ^ b[i + 4]) !== 0xFF) return null;  // Valor XOR ~Valor == FF
      }
      if (b[12] !== b[14] || b[13] !== b[15]) return null;
      if ((b[12] ^ b[13]) !== 0xFF) return null;      // adr XOR ~adr == FF
      return { value: readLE32(b, 0), adr: b[12] };
    },

    encode: function (hex, newValue) {
      const upper = hex === hex.toUpperCase();
      const adr = HEX32.test(hex) ? hexToBytes(hex)[12] : 0; // conserva la dirección
      const v = writeLE32(newValue);
      const nv = v.map(x => (~x) & 0xFF);
      const a = adr & 0xFF, na = (~adr) & 0xFF;
      return bytesToHex([...v, ...nv, ...v, a, na, a, na], upper);
    },

    lib: { hexToBytes, bytesToHex, readLE32, writeLE32 },
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = RedundancyScheme;
  else root.RedundancyScheme = RedundancyScheme;
})(typeof self !== 'undefined' ? self : this);
