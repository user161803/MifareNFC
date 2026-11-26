// Ejecutar: node app/test.js
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const VB = require('./valueblock.js');

const dir = path.join(__dirname, '..', 'test-cards');
const EXPECTED_10 = '0A000000F5FFFFFF0A00000000FF00FF';

for (const f of ['cargada_prueba.json', 'descargada_prueba.json']) {
  const src = fs.readFileSync(path.join(dir, f), 'utf8');
  const r = VB.setCounter(src, 10);
  assert.strictEqual(r.changed.length, 1, f + ': un value block');
  assert.strictEqual(r.changed[0].block, 4);
  assert.strictEqual(JSON.parse(r.text).blocks['4'], EXPECTED_10);
  assert.strictEqual(r.text, src.replace(r.changed[0].oldHex, EXPECTED_10), f + ': resto idéntico');
}

// Redundancia: valores límite y negativos
for (const v of [0, 1, 10, 255, 256, 65535, 2147483647, -1, -2147483648]) {
  const hex = VB.buildValueBlock(v, 4, true);
  assert.deepStrictEqual(VB.parseValueBlock(hex), { value: v, adr: 4 }, 'valor ' + v);
}
assert.strictEqual(VB.buildValueBlock(1, 0, true), '01000000FEFFFFFF0100000000FF00FF');
assert.strictEqual(VB.parseValueBlock('00000000000000000000000000000000'), null);
assert.strictEqual(VB.parseValueBlock('01000000FEFFFFFF0200000000FF00FF'), null);

// Formato texto .mct
const json = JSON.parse(fs.readFileSync(path.join(dir, 'cargada_prueba.json'), 'utf8'));
let mct = '';
for (let s = 0; s < 16; s++) {
  mct += '+Sector: ' + s + '\n';
  for (let b = 0; b < 4; b++) mct += json.blocks[s * 4 + b] + '\n';
}
const rm = VB.setCounter(mct, 10);
assert.strictEqual(rm.format, 'mct');
assert.strictEqual(rm.text, mct.replace('01000000FEFFFFFF0100000000FF00FF', EXPECTED_10));

console.log('OK: todos los tests pasan');
