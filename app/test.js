// Ejecutar: node app/test.js
// Prueba el motor genérico inyectando el esquema de ejemplo (estándar NXP).
const assert = require('assert');
const fs = require('fs');
const path = require('path');
const VB = require('./valueblock.js');
const example = require('./redundancy.example-standard-nxp.js');

VB.useScheme(example);

const dir = path.join(__dirname, '..', 'test-cards');
const EXPECTED_10 = '0A000000F5FFFFFF0A00000000FF00FF';

for (const f of ['cargada_prueba.json', 'descargada_prueba.json']) {
  const src = fs.readFileSync(path.join(dir, f), 'utf8');
  const r = VB.setCounter(src, 10);
  assert.strictEqual(r.changed.length, 1, f + ': un bloque contador');
  assert.strictEqual(r.changed[0].block, 4);
  assert.strictEqual(JSON.parse(r.text).blocks['4'], EXPECTED_10);
  assert.strictEqual(r.text, src.replace(r.changed[0].oldHex, EXPECTED_10), f + ': resto idéntico');
}

// decode/encode del esquema de ejemplo: valores límite y negativos
for (const v of [0, 1, 10, 255, 256, 65535, 2147483647, -1, -2147483648]) {
  const hex = example.encode('00000000000000000000000000000000'.replace(/^.{24}/, '000000000000'), v);
  const d = example.decode(hex);
  assert.ok(d && d.value === v, 'roundtrip valor ' + v);
}
assert.strictEqual(example.encode('01000000FEFFFFFF0100000000FF00FF', 1), '01000000FEFFFFFF0100000000FF00FF');
assert.strictEqual(example.decode('00000000000000000000000000000000'), null);
assert.strictEqual(example.decode('01000000FEFFFFFF0200000000FF00FF'), null);

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

// El esquema VACÍO por defecto no detecta nada y encode lanza aviso
const empty = require('./redundancy.js');
assert.strictEqual(empty.decode('01000000FEFFFFFF0100000000FF00FF'), null, 'esquema vacío no detecta');
assert.throws(() => empty.encode('01000000FEFFFFFF0100000000FF00FF', 10), /sin implementar/);

console.log('OK: motor genérico + esquema de ejemplo + esquema vacío correctos');
