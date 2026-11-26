/*
 * Motor GENÉRICO sobre volcados de MifareClassicTool (.json o .mct/.txt).
 *
 * No contiene nada específico del formato del contador: delega toda la
 * redundancia en el esquema de `redundancy.js` (funciones decode/encode).
 * Para adaptar la app a otras tarjetas se edita SOLO redundancy.js; este
 * fichero no cambia. Ver REDUNDANCY.md.
 *
 * Funciona en navegador (window.ValueBlock) y en Node (module.exports).
 */
(function (root) {
  'use strict';

  const HEX32 = /^[0-9A-Fa-f]{32}$/;

  // Esquema de redundancia activo (editable/inyectable para tests).
  let scheme = (typeof module !== 'undefined' && module.exports)
    ? require('./redundancy.js')
    : root.RedundancyScheme;

  function useScheme(s) { scheme = s; }
  function getScheme() { return scheme; }

  /** Índice de bloque -> ¿es trailer de sector? (1K/Mini/2K/4K) */
  function isTrailer(block) {
    if (block < 128) return block % 4 === 3;
    return (block - 128) % 16 === 15;
  }
  /** Índice de sector de un bloque. */
  function sectorOf(block) {
    return block < 128 ? (block >> 2) : 32 + ((block - 128) >> 4);
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

  /**
   * Bloques que el esquema activo reconoce como contadores (excluye bloque 0 y
   * trailers). Cada uno: {block, hex, value, info}.
   */
  function findCounterBlocks(blocks) {
    const res = [];
    for (const k of Object.keys(blocks).map(Number).sort((a, b) => a - b)) {
      if (k === 0 || isTrailer(k)) continue;
      const hex = blocks[k];
      if (!HEX32.test(hex)) continue;
      const d = scheme.decode(hex);
      if (d && typeof d.value === 'number') res.push({ block: k, hex, value: d.value, info: d });
    }
    return res;
  }

  /** Reescribe el texto original cambiando SOLO los bloques indicados, byte a byte. */
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
   * Operación completa: devuelve {text, format, changed, counterBlocks}.
   * selected: bloques a modificar (por defecto, todos los contadores detectados).
   */
  function setCounter(text, newValue, selected) {
    const { format, blocks } = parseDump(text);
    const cbs = findCounterBlocks(blocks);
    const targets = selected ? cbs.filter(v => selected.includes(v.block)) : cbs;
    const changes = {};
    const changed = [];
    for (const v of targets) {
      const newHex = scheme.encode(v.hex, newValue);
      if (!HEX32.test(newHex)) {
        throw new Error('encode() devolvió un bloque inválido (' + newHex + ') para el bloque ' + v.block + '.');
      }
      changes[v.block] = newHex;
      changed.push({ block: v.block, oldHex: v.hex, newHex, oldValue: v.value, newValue });
    }
    return { text: rewriteDump(text, format, changes), format, changed, counterBlocks: cbs };
  }

  const api = {
    useScheme, getScheme, isTrailer, sectorOf,
    parseDump, findCounterBlocks, rewriteDump, setCounter,
  };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.ValueBlock = api;
})(typeof self !== 'undefined' ? self : this);
