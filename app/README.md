# Editor de contador MIFARE Classic (web)

App web estática (sin dependencias ni servidor) que toma volcados de
MifareClassicTool y genera una copia **idéntica** con el contador cambiado
(por defecto `10`) y la redundancia recalculada.

## ⚠️ El cálculo de redundancia está vacío

El motor es genérico y **no** sabe cómo tus tarjetas codifican el contador. Esa
parte la rellenas tú en [`redundancy.js`](redundancy.js) (funciones `decode` y
`encode`). Hasta entonces la app detecta 0 bloques contador y avisa.

- Guía paso a paso: [`../REDUNDANCY.md`](../REDUNDANCY.md)
- Ejemplo completo y funcional (estándar NXP): [`redundancy.example-standard-nxp.js`](redundancy.example-standard-nxp.js)

## Uso

1. Abre `app/index.html` en el navegador (doble clic; funciona en local).
2. Indica el nuevo valor (por defecto `10`).
3. Arrastra uno o varios volcados (`.json` de MCT o `.mct`/`.txt` con `+Sector:`).
4. Revisa el antes/después y pulsa **Descargar** → `<nombre>_contador10.json`.

## Qué hace el motor (genérico)

- Lee el volcado y localiza los bloques (excluye bloque 0 y trailers).
- Pregunta a tu esquema (`decode`) cuáles son contadores y su valor.
- Para los seleccionados, pide a tu esquema (`encode`) el nuevo bloque.
- Sustituye solo esos 32 caracteres en el texto original: UID, claves, orden,
  espaciado y mayúsculas quedan byte a byte iguales.
- Re-lee la salida y comprueba con `decode` que el valor quedó bien.

## Ficheros

- `index.html` — interfaz.
- `valueblock.js` — motor genérico (no se toca por tarjeta).
- `redundancy.js` — **esquema vacío a rellenar**.
- `redundancy.example-standard-nxp.js` — esquema de ejemplo / referencia.
- `test.js` — tests del motor con el esquema de ejemplo.

## Tests

```
node app/test.js
```
