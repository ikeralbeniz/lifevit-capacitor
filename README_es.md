# lifevit-capacitor

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

> 🇬🇧 English version: [README.md](./README.md). La referencia completa de la API
> (autogenerada) está en el README en inglés.

Plugin de [Capacitor](https://capacitorjs.com/) que envuelve el
[SDK BLE oficial de Lifevit](https://github.com/lifevit) (Android + iOS) con una
única API de JavaScript multiplataforma: **escanear, conectar y tomar una
medida** de los dispositivos de salud Lifevit.

Desarrollado y probado con un tensiómetro **BPM‑200 Wireless** y un pulsioxímetro
**OL‑750**, pero la API es genérica para las familias de dispositivos que soporta
el SDK.

## Características

- Una sola API JS para **Android e iOS** (las diferencias se resuelven en nativo).
- **Tensiómetro**: sistólica / diastólica / pulso, **arritmia (IHB)**¹ y códigos
  de error de la medida.
- **Pulsioxímetro**: SpO₂, pulso, PI, RPM y el valor de pletismografía en vivo.
- Eventos de **termómetro** y **báscula** por la misma API.
- Eventos de conexión/errores y un inspector BLE en crudo (solo Android).

¹ La arritmia (IHB) solo la reporta el SDK de Android.

## Soporte por plataforma

| Capacidad                          | Android | iOS |
| ---------------------------------- | :-----: | :-: |
| escanear / conectar / desconectar  |   ✅    | ✅  |
| eventos de medida y progreso       |   ✅    | ✅  |
| `getDeviceAddress` (MAC / UUID)    |   ✅    | ✅  |
| arritmia (IHB) en tensiómetro      |   ✅    | —   |
| inspector BLE en crudo (`rawScan`) |   ✅    | —   |
| `sendThermometerCommand`           |   ✅    | —   |

En iOS el dispositivo se identifica por su **UUID de CoreBluetooth** (no hay MAC);
el campo `address` transporta ese UUID de forma transparente.

## Instalación

```bash
npm install lifevit-capacitor
npx cap sync
```

### El SDK de Lifevit NO se incluye

El SDK de Lifevit es propietario y **no** se almacena en este repositorio. Se
obtiene por plataforma:

- **Android** — lo descarga Gradle automáticamente del Maven de Lifevit. Añade el
  repositorio al `android/build.gradle` **de nivel proyecto** de tu app:

  ```gradle
  allprojects {
      repositories {
          google()
          mavenCentral()
          maven { url "https://raw.githubusercontent.com/lifevit/android-sdk/master" }
      }
  }
  ```

- **iOS** — se enlaza como **git submodule** (`ios/vendor/lifevit-ios-sdk`,
  pinneado a un commit concreto de [lifevit/ios-sdk](https://github.com/lifevit/ios-sdk)).
  Clona con submódulos:

  ```bash
  git clone --recurse-submodules <url-de-tu-repo>
  # o, en un clon existente:
  git submodule update --init --recursive
  ```

  > **Nota npm:** los submódulos de git **no** viajan en el tarball de `npm
  > install`. Consumir el plugin desde un paquete npm publicado en iOS requeriría
  > vendorizar el `LifevitSDK.xcframework` (o un paso de postinstall). Para clones
  > de GitHub, el submódulo funciona sin más.

Añade la cadena de uso de Bluetooth al `Info.plist` de tu app iOS:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Conectar con tus dispositivos de salud Lifevit por Bluetooth.</string>
```

## Uso

```ts
import { Lifevit } from 'lifevit-capacitor';

await Lifevit.requestPermissions();

Lifevit.addListener('connectionChanged', (e) => console.log('estado', e.status));
Lifevit.addListener('measurement', (e) => {
  // tensiómetro: { systolic, diastolic, pulse, ihb?, errorCode, errorMessage }
  // oxímetro:    { spo2, pulse: lpm, pi, rpm }
  console.log('medida', e);
});
Lifevit.addListener('measurementProgress', (e) => console.log('progreso', e.progress));

// Conecta con el primer tensiómetro que encuentre (el SDK escanea internamente).
await Lifevit.connect({ deviceType: 'tensiometer', timeout: 15000 });

// Cuando el estado sea 'connected', lanza la medida (el brazalete se hincha).
await Lifevit.startMeasurement();
```

El oxímetro, el termómetro y la báscula **miden en streaming** al usarlos: no hace
falta `startMeasurement()`; el resultado llega por el evento `measurement`.

### Modelo de emparejamiento

Los dispositivos Lifevit solo se anuncian cuando están **despiertos** (encendidos
/ midiendo), y el anuncio solo lleva su identidad, no el resultado. El flujo real
es: encender el dispositivo → `connect({ deviceType })` → leer la dirección
descubierta con `getDeviceAddress()` → guardarla → reutilizarla para
reconectar la próxima vez. La app de ejemplo implementa un emparejamiento +
medida completo, con reintento y timeout.

## App de ejemplo

En [`example/`](./example) hay una app lista para probar (Capacitor sin
framework). Implementa el flujo completo: permisos al arrancar, lista de
dispositivos, emparejar‑midiendo, y una ventana de medida con valores en vivo,
gráfica de pulso, timeout y cancelar. Ver [`example/README.md`](./example/README.md).

## Licencia

[MIT](./LICENSE) — libre para uso comercial. La licencia cubre **solo el código de
este plugin**; el SDK de Lifevit es propietario y se rige por la licencia de
Lifevit. Ver la nota en [LICENSE](./LICENSE).
