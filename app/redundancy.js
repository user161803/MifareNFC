/*
 * ============================================================================
 *  ESQUEMA DE REDUNDANCIA  —  RELLENA ESTE FICHERO PARA TUS TARJETAS
 * ============================================================================
 *
 * El resto de la app (interfaz, lectura de volcados, NFC) es GENÉRICO y no sabe
 * nada de cómo se codifica el contador. Toda esa parte específica de tu tarjeta
 * vive aquí, en dos funciones:
 *
 *   decode(hex)            -> { value } si `hex` es un bloque contador de tu
 *                             esquema, o null si no lo es.
 *   encode(hex, newValue)  -> nuevo bloque (32 hex) con `newValue` escrito y la
 *                             redundancia recalculada.
 *
 * `hex` es SIEMPRE un bloque de 16 bytes en hexadecimal (32 caracteres).
 *
 * Mientras no las implementes, la app detecta 0 bloques contador y la escritura
 * lanza un aviso. Tienes un ejemplo completo y funcional (el estándar NXP) en
 * `app/redundancy.example-standard-nxp.js`, y la guía paso a paso en
 * `REDUNDANCY.md`.
 *
 * Esta misma lógica hay que replicarla en la app Android, en
 * `android/app/src/main/java/com/mifarenfc/counter/Redundancy.kt`.
 * ----------------------------------------------------------------------------
 */
(function (root) {
  'use strict';

  /* ---- Helpers de bytes (opcionales, por si te vienen bien) ---------------- */

  function hexToBytes(hex) {
    const out = [];
    for (let i = 0; i < hex.length; i += 2) out.push(parseInt(hex.substr(i, 2), 16));
    return out;
  }
  function bytesToHex(bytes, upper) {
    const s = bytes.map(b => (b & 0xFF).toString(16).padStart(2, '0')).join('');
    return upper ? s.toUpperCase() : s;
  }
  function readLE32(b, o) {        // 4 bytes little-endian -> entero con signo
    return (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) | 0;
  }
  function writeLE32(v) {          // entero -> 4 bytes little-endian
    const u = v >>> 0;
    return [u & 0xFF, (u >>> 8) & 0xFF, (u >>> 16) & 0xFF, (u >>> 24) & 0xFF];
  }

  /* ------------------------------------------------------------------------- */

  const RedundancyScheme = {
    /** Nombre que se muestra en la interfaz. */
    name: 'sin implementar',

    /**
     * ¿Es `hex` (32 caracteres) un bloque contador de TU esquema?
     *   - Si lo es, devuelve { value: <entero actual del contador> }.
     *   - Si no lo es (bloque de datos normal, formato distinto…), devuelve null.
     *
     * Debe ser estricto: si devuelves un objeto para bloques que no son
     * contadores, la app intentará reescribirlos.
     *
     * @param {string} hex
     * @returns {{value:number}|null}
     */
    decode: function (hex) {
      // TODO: implementa la detección y lectura del contador de tus tarjetas.
      return null;
    },

    /**
     * Construye el nuevo bloque con `newValue` y la redundancia recalculada.
     * Recibe el bloque original (`hex`) por si necesitas conservar bytes de él
     * (dirección, flags, bytes reservados…). Casa las mayúsculas/minúsculas con
     * la entrada si quieres que el volcado quede idéntico salvo el contador.
     *
     * @param {string} hex       bloque original (32 hex)
     * @param {number} newValue  nuevo valor del contador (entero)
     * @returns {string}         nuevo bloque (32 hex)
     */
    encode: function (hex, newValue) {
      // TODO: implementa la escritura del contador + redundancia de tus tarjetas.
      throw new Error(
        'Esquema de redundancia sin implementar: edita app/redundancy.js ' +
        '(funciones decode y encode). Ver REDUNDANCY.md.'
      );
    },

    /** Helpers expuestos por si los quieres usar desde fuera. */
    lib: { hexToBytes, bytesToHex, readLE32, writeLE32 },
  };

  if (typeof module !== 'undefined' && module.exports) module.exports = RedundancyScheme;
  else root.RedundancyScheme = RedundancyScheme;
})(typeof self !== 'undefined' ? self : this);
