/*
 * Lógica de value blocks estándar de MIFARE Classic (formato público del
 * datasheet de NXP) sobre volcados MifareClassicTool (.json o .mct/.txt).
 *
 *  bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
 *         [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
 *
 * Funciona en navegador (window.ValueBlock) y en Node (module.exports).
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
    const s = bytes.map(b => b.toString(16).padStart(2, '0')).join('');
    return upper ? s.toUpperCase() : s;
  }

  function readLE32(b, o) {
    return (b[o] | (b[o + 1] << 8) | (b[o + 2] << 16) | (b[o + 3] << 24)) | 0; // con signo
  }

  function writeLE32(v) {
    const u = v >>> 0;
    return [u & 0xFF, (u >>> 8) & 0xFF, (u >>> 16) & 0xFF, (u >>> 24) & 0xFF];
  }

  /** Índice de bloque -> ¿es trailer de sector? (1K/Mini/2K/4K) */
  function isTrailer(block) {
    if (block < 128) return block % 4 === 3;
    return (block - 128) % 16 === 15;
  }

  /** Devuelve {value, adr} si el bloque es un value block coherente; si no, null. */
  function parseValueBlock(hex) {
    if (!HEX32.test(hex)) return null;
    const b = hexToBytes(hex);
    for (let i = 0; i < 4; i++) {
      if (b[i] !== b[i + 8]) return null;           // Valor == copia
      if ((b[i] ^ b[i + 4]) !== 0xFF) return null;  // Valor XOR ~Valor == FF
    }
    if (b[12] !== b[14] || b[13] !== b[15]) return null;
    if ((b[12] ^ b[13]) !== 0xFF) return null;      // adr XOR ~adr == FF
    return { value: readLE32(b, 0), adr: b[12] };
  }

  /** Construye un value block con el valor dado y la redundancia recalculada. */
  function buildValueBlock(value, adr, upper) {
    const v = writeLE32(value);
    const nv = v.map(x => (~x) & 0xFF);
    const a = adr & 0xFF;
    const na = (~a) & 0xFF;
    return bytesToHex([...v, ...nv, ...v, a, na, a, na], upper);
  }

  /* ---------------- Parsers de volcado ---------------- */

  /** Lee un volcado y devuelve {format, blocks: {idx: hex}}. */
  function parseDump(text) {
    const trimmed = text.replace(/^﻿/, '').trim();
    if (trimmed.startsWith('{')) {
      const data = JSON.parse(trimmed);
      if (!data.blocks || typeof data.blocks !== 'object') {
        throw new Error('El JSON no tiene la sección "blocks" de MifareClassicTool.');
      }
      const blocks = {};
      for (const [k, v] of Object.entries(data.blocks)) blocks[parseInt(k, 10)] = String(v);
      return { format: 'json', blocks };
    }
    // Formato texto MCT: "+Sector: N" seguido de 4 (o 16) líneas de 32 hex ('-' = desconocido)
    const blocks = {};
    let sector = -1, inSector = 0, found = false;
    for (const raw of text.split(/\r?\n/)) {
      const line = raw.trim();
      const m = /^\+Sector:\s*(\d+)/i.exec(line);
      if (m) { sector = parseInt(m[1], 10); inSector = 0; found = true; continue; }
      if (sector < 0 || line === '') continue;
      const first = sector < 32 ? sector * 4 : 128 + (sector - 32) * 16;
      blocks[first + inSector] = line;
      inSector++;
    }
    if (!found) throw new Error('Formato no reconocido: ni JSON de MCT ni volcado .mct con "+Sector:".');
    return { format: 'mct', blocks };
  }

  /** Bloques que son value blocks válidos (excluye bloque 0 y trailers). */
  function findValueBlocks(blocks) {
    const res = [];
    for (const k of Object.keys(blocks).map(Number).sort((a, b) => a - b)) {
      if (k === 0 || isTrailer(k)) continue;
      const vb = parseValueBlock(blocks[k]);
      if (vb) res.push({ block: k, hex: blocks[k], value: vb.value, adr: vb.adr });
    }
    return res;
  }

  /**
   * Reescribe el texto original cambiando SOLO los bloques indicados, preservando
   * todo lo demás byte a byte (formato, espaciado, orden, mayúsculas).
   * changes: {blockIdx: newHex}
   */
  function rewriteDump(text, format, changes) {
    if (format === 'json') {
      let out = text;
      for (const [k, newHex] of Object.entries(changes)) {
        const re = new RegExp('("' + k + '"\\s*:\\s*")([0-9A-Fa-f]{32})(")');
        if (!re.test(out)) throw new Error('No se encontró el bloque ' + k + ' en el JSON.');
        out = out.replace(re, (_, a, _old, c) => a + newHex + c);
      }
      return out;
    }
    // mct: reconstruir línea a línea conservando los saltos de línea originales
    const parts = text.split(/(\r?\n)/);
    let sector = -1, inSector = 0;
    for (let i = 0; i < parts.length; i += 2) {
      const line = parts[i].trim();
      const m = /^\+Sector:\s*(\d+)/i.exec(line);
      if (m) { sector = parseInt(m[1], 10); inSector = 0; continue; }
      if (sector < 0 || line === '') continue;
      const first = sector < 32 ? sector * 4 : 128 + (sector - 32) * 16;
      const idx = first + inSector;
      if (Object.prototype.hasOwnProperty.call(changes, idx)) {
        parts[i] = parts[i].replace(line, changes[idx]);
      }
      inSector++;
    }
    return parts.join('');
  }

  /**
   * Operación completa: devuelve {text, changed:[{block, oldHex, newHex, oldValue, newValue}]}.
   * selected: lista de bloques a modificar (por defecto todos los value blocks detectados).
   */
  function setCounter(text, newValue, selected) {
    if (!Number.isInteger(newValue) || newValue < -2147483648 || newValue > 2147483647) {
      throw new Error('El valor debe ser un entero de 32 bits con signo.');
    }
    const { format, blocks } = parseDump(text);
    const vbs = findValueBlocks(blocks);
    const targets = selected ? vbs.filter(v => selected.includes(v.block)) : vbs;
    const changes = {};
    const changed = [];
    for (const v of targets) {
      const upper = v.hex === v.hex.toUpperCase();
      const newHex = buildValueBlock(newValue, v.adr, upper);
      changes[v.block] = newHex;
      changed.push({ block: v.block, oldHex: v.hex, newHex, oldValue: v.value, newValue, adr: v.adr });
    }
    return { text: rewriteDump(text, format, changes), format, changed, valueBlocks: vbs };
  }

  const api = {
    parseValueBlock, buildValueBlock, parseDump, findValueBlocks,
    rewriteDump, setCounter, isTrailer,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ValueBlock = api;
})(typeof self !== 'undefined' ? self : this);
