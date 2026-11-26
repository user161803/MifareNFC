# Tarjetas de prueba (MIFARE Classic 1K)

Volcados de ejemplo en formato MifareClassicTool (MCT) para **aprender y practicar**
sobre tarjetas en blanco propias. **No** corresponden a ningún sistema real:

- Todas las claves de sector son las **por defecto** (`FFFFFFFFFFFF`) con access
  bits de transporte (`FF078069`).
- Usan el **value block estándar** de MIFARE Classic (formato público del datasheet
  de NXP), no un formato propietario.

## Ficheros

| Fichero | Estado | Bloque 4 (value block) |
|---|---|---|
| `descargada_prueba.json` | valor `0` | `00000000 FFFFFFFF 00000000 00 FF 00 FF` |
| `cargada_prueba.json` | valor `1` | `01000000 FEFFFFFF 01000000 00 FF 00 FF` |

Misma UID (`DEADBEEF`) en ambos: representan la **misma tarjeta** en dos estados.

## Formato del value block estándar

```
 bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
        [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
```

- **Valor**: entero de 4 bytes en little-endian.
- **~Valor**: complemento a uno (NOT) del valor.
- **Valor**: misma copia, repetida.
- **adr/~adr**: byte de dirección y su complemento, repetidos.

La redundancia del formato es esa triple copia + complemento: el lector rechaza el
bloque si las copias no son coherentes (`Valor XOR ~Valor == 0xFFFFFFFF`).

## Uso

Escríbelos con MifareClassicTool sobre una tarjeta MIFARE Classic 1K en blanco (claves
por defecto) para experimentar con lectura/escritura de value blocks.
