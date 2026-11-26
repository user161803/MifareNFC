# Cómo adaptar el cálculo de redundancia a tus tarjetas

Este proyecto (app web + app Android) sabe hacer de todo **menos** una cosa:
cómo tu tarjeta codifica el contador y su redundancia. Esa parte está
**vacía a propósito** y la rellenas tú en un único sitio.

- **Web:** [`app/redundancy.js`](app/redundancy.js)
- **Android:** [`android/app/src/main/java/com/mifarenfc/counter/Redundancy.kt`](android/app/src/main/java/com/mifarenfc/counter/Redundancy.kt)

Todo lo demás —leer `.json`/`.mct`, localizar los bloques, NFC, la interfaz,
reescribir el volcado byte a byte— es **genérico** y no hay que tocarlo.

---

## Las dos funciones que tienes que escribir

Ambas trabajan sobre **un bloque de 16 bytes en hexadecimal** (32 caracteres).

### 1. `decode(hex)` — ¿es este bloque un contador, y cuánto vale?

- Si `hex` **es** un bloque contador de tu esquema → devuelve el valor actual.
- Si **no** lo es (bloque de datos normal, otro formato…) → devuelve "nada".

Debe ser **estricto**: si das por bueno un bloque que no es un contador, la app
intentará reescribirlo. Normalmente se comprueba aquí la propia redundancia
(copias, complementos, CRC, checksum…): si no cuadra, no es tu contador.

| | Devuelve si es contador | Devuelve si no lo es |
|---|---|---|
| **JS** | `{ value: <entero> }` | `null` |
| **Kotlin** | `Decoded(<entero>)` | `null` |

### 2. `encode(hex, newValue)` — escribe el nuevo contador

Construye y devuelve el **nuevo bloque de 32 hex** con `newValue` dentro y la
redundancia recalculada. Recibe el bloque original (`hex`) por si necesitas
conservar bytes de él (dirección, flags, bytes reservados, etc.).

Debe cumplirse siempre: `decode(encode(hex, v)).value === v`.

---

## Cómo descubrir el esquema de tus tarjetas

Si no tienes la especificación, se deduce comparando volcados:

1. Haz un volcado de la **misma tarjeta** con el contador en dos valores
   conocidos (p. ej. N y N+1), con MifareClassicTool.
2. Compara byte a byte (en este repo: `diff <(python3 -m json.tool a.json) <(python3 -m json.tool b.json)`).
   Mira **qué bloque** cambia y **qué bytes** dentro de él.
3. Deduce:
   - **Dónde** está el valor y en qué orden (little-endian / big-endian).
   - **Cuántas copias** hay y si alguna es el complemento (`~`) o un XOR.
   - Si hay un **CRC/checksum** sobre parte del bloque (cambia al cambiar el valor).
   - Qué bytes **no cambian** (dirección, relleno): consérvalos en `encode`.
4. Escribe `decode`/`encode` con esa lógica y comprueba que, al reconstruir el
   volcado con el valor N+1, te sale **idéntico** al volcado real de N+1.

Si me pasas un par de volcados así, te relleno estas funciones.

---

## Ejemplo completo y funcional (value block estándar NXP)

Incluido como referencia (la app **no** lo usa por defecto):

- Web: [`app/redundancy.example-standard-nxp.js`](app/redundancy.example-standard-nxp.js)
- Kotlin: [`RedundancyExampleStandardNxp.kt`](android/app/src/main/java/com/mifarenfc/counter/RedundancyExampleStandardNxp.kt)

Formato:

```
 bytes:  0  1  2  3   4  5  6  7   8  9 10 11   12 13 14 15
        [  Valor  ] [  ~Valor  ] [  Valor  ]  adr ~adr adr ~adr
```

- **Valor**: entero de 4 bytes little-endian.
- **~Valor**: complemento a uno del valor.
- **Valor**: copia.
- **adr/~adr**: byte de dirección y su complemento, repetidos.

`decode` acepta el bloque solo si las tres copias y los complementos cuadran;
`encode` reescribe las tres copias y conserva el byte de dirección.

### Activarlo (o usarlo de plantilla)

**Web** — copia el cuerpo de `decode`/`encode` del ejemplo a `app/redundancy.js`
y pon un `name` descriptivo. (`index.html` siempre carga `redundancy.js`.)

**Android** — copia la lógica de `StandardNxpScheme` dentro de `EmptyScheme` en
`Redundancy.kt`, **o** cambia el esquema activo en una línea:

```kotlin
// en ValueBlock.kt
var scheme: RedundancyScheme = StandardNxpScheme   // en vez de EmptyScheme
```

---

## Reglas que el motor espera de tu esquema

- `decode` y `encode` trabajan con **hex de 32 caracteres** (un bloque).
- `encode` debe devolver **exactamente 32 hex**; si no, el motor lo rechaza.
- El motor **nunca** llama a tu esquema con el bloque 0 (fabricante) ni con los
  trailers de sector: no te preocupes por ellos.
- Mantén **web y Android sincronizados**: misma lógica en `redundancy.js` y en
  `Redundancy.kt`, o los dos cálculos divergirán.
- Mayúsculas/minúsculas: si quieres que el volcado quede idéntico salvo el
  contador, haz que `encode` respete el "case" del `hex` de entrada (el ejemplo
  lo hace).

---

## Probar tu implementación

**Web:**

```bash
node app/test.js
```

`test.js` prueba el **motor** contra el esquema de ejemplo e incluye casos
límite. Para probar *tu* esquema, duplica esos casos apuntando a tu
`decode`/`encode`, o cambia el `require` del ejemplo por `../app/redundancy.js`.

**Android:** la lógica de `Redundancy.kt`/`ValueBlock.kt` es Kotlin puro (solo
depende de `org.json`), así que puedes probarla en un test JVM normal sin el
emulador: instancia tu esquema, asígnalo a `ValueBlock.scheme` y comprueba
`setCounter` sobre un volcado conocido.
