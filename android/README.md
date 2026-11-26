# Contador MIFARE Classic — app Android

App Android (Kotlin + Jetpack Compose) que hace lo mismo que la web y, además,
lee y escribe tarjetas **físicas** por NFC.

- **Archivo**: abres un volcado de MifareClassicTool (`.json` o `.mct`/`.txt`),
  genera una copia idéntica con el contador de los value blocks a 10 (o el valor
  que pongas) y la redundancia recalculada, y la guardas.
- **NFC**: acercas una tarjeta MIFARE Classic, la app autentica los sectores con
  claves por defecto, muestra los value blocks y escribe el nuevo contador en la
  tarjeta (y la relee para confirmar).

## El cálculo de la redundancia está en un solo sitio

`app/src/main/java/com/mifarenfc/counter/ValueBlock.kt` es un puerto 1:1 de
`app/valueblock.js`. Si tus tarjetas usan otro esquema (CRC, checksum propio,
otro orden de bytes…), **solo hay que cambiar ese fichero**; la UI, la E/S de
ficheros y el NFC no cambian. Mándame tus tarjetas de ejemplo y lo adapto.

Verificado: produce exactamente los mismos bytes que la versión web ya probada.

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
    ValueBlock.kt   ← lógica de redundancia (único módulo a tocar por esquema)
    NfcCardIo.kt    ← lectura/escritura MIFARE Classic por NFC
    MainActivity.kt ← UI Compose (pestañas Archivo / NFC)
```
