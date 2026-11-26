# Editor de contador MIFARE Classic

App web estática (sin dependencias ni servidor) que toma volcados de
MifareClassicTool y genera una copia **idéntica** con el contador del value block
cambiado (por defecto `10`) y la redundancia recalculada.

## Uso

1. Abre `app/index.html` en el navegador (doble clic; funciona en local).
2. Indica el nuevo valor (por defecto `10`).
3. Arrastra uno o varios volcados (`.json` de MCT o `.mct`/`.txt` con `+Sector:`).
4. Revisa el antes/después y pulsa **Descargar** → `<nombre>_contador10.json`.

## Qué hace exactamente

- Detecta los value blocks coherentes (excluye bloque 0 y trailers).
- Para cada uno seleccionado escribe: valor (LE), `~valor`, valor, y conserva
  `adr ~adr adr ~adr`.
- Sustituye solo esos 32 caracteres en el texto original: UID, claves, access
  bits, orden, espaciado y mayúsculas quedan byte a byte iguales.
- Re-lee la salida para verificar la redundancia antes de ofrecer la descarga.

Ejemplo con `test-cards/cargada_prueba.json`, bloque 4:

```
antes:   01000000 FEFFFFFF 01000000 00 FF 00 FF   (valor 1)
después: 0A000000 F5FFFFFF 0A000000 00 FF 00 FF   (valor 10)
```

## Tests

```
node app/test.js
```
