# Contador MIFARE Classic — app Android

App Android (Kotlin + Jetpack Compose) que hace lo mismo que la web y, además,
lee y escribe tarjetas **físicas** por NFC.

- **Archivo**: abres un volcado de MifareClassicTool (`.json` o `.mct`/`.txt`),
  genera una copia idéntica con el contador de los value blocks a 10 (o el valor
  que pongas) y la redundancia recalculada, y la guardas.
- **NFC**: acercas una tarjeta MIFARE Classic, la app autentica los sectores con
  claves por defecto, muestra los value blocks y escribe el nuevo contador en la
  tarjeta (y la relee para confirmar).

## ⚠️ El cálculo de la redundancia está vacío (a propósito)

El motor es genérico. Cómo tus tarjetas codifican el contador lo rellenas tú en
`app/src/main/java/com/mifarenfc/counter/Redundancy.kt` (funciones `decode` y
`encode` de `EmptyScheme`). Hasta entonces la app avisa y no escribe.

- Guía paso a paso: [`../REDUNDANCY.md`](../REDUNDANCY.md)
- Ejemplo completo y funcional (estándar NXP): `RedundancyExampleStandardNxp.kt`
  (para activarlo, copia su lógica a `EmptyScheme`, o pon
  `var scheme = StandardNxpScheme` en `ValueBlock.kt`).

Mantén esta lógica **sincronizada** con la web (`app/redundancy.js`).

## Compilar

Necesitas Android Studio (o el SDK de Android + JDK 17).

```bash
cd android
./gradlew assembleDebug        # genera app/build/outputs/apk/debug/app-debug.apk
```

O abre la carpeta `android/` en Android Studio y pulsa Run. La primera vez
Android Studio descarga el Gradle wrapper y las dependencias.

Requisitos: `minSdk 24`, `compileSdk 34`. La escritura NFC necesita un móvil con
NFC y tarjetas cuyas claves/access bits permitan escribir (la configuración de
transporte por defecto `FFFFFFFFFFFF` / `FF078069` lo permite).

## Estructura

```
android/
  app/src/main/java/com/mifarenfc/counter/
    Redundancy.kt                    ← esquema VACÍO a rellenar (decode/encode)
    RedundancyExampleStandardNxp.kt  ← esquema de ejemplo / referencia
    ValueBlock.kt                    ← motor genérico (no se toca por tarjeta)
    NfcCardIo.kt                     ← lectura/escritura MIFARE Classic por NFC
    MainActivity.kt                  ← UI Compose (pestañas Archivo / NFC)
```
