# lifevit-capacitor

[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](./LICENSE)

> 🇪🇸 ¿Prefieres español? Lee el [README en castellano](./README_es.md).

A [Capacitor](https://capacitorjs.com/) plugin that wraps the official
[Lifevit BLE SDK](https://github.com/lifevit) (Android + iOS) with a single,
cross‑platform JavaScript API: **scan, connect and take a measurement** from
Lifevit health devices.

Built and tested with a **BPM‑200 Wireless** blood‑pressure monitor and an
**OL‑750** pulse oximeter, but the API is generic for the device families the
SDK supports.

## Features

- One JS API for **Android and iOS** (platform differences handled natively).
- **Blood‑pressure monitor** (tensiometer): systolic / diastolic / pulse,
  irregular‑heartbeat (IHB) flag¹, and measurement error codes.
- **Pulse oximeter**: SpO₂, pulse, PI, RPM and the live plethysmography value.
- **Thermometer** and **scale** events wired through the same API.
- Connection lifecycle + error events, and a generic raw‑BLE inspector (Android).

¹ IHB/arrhythmia is only reported by the Android SDK.

## Platform support

| Capability                         | Android | iOS |
| ---------------------------------- | :-----: | :-: |
| scan / connect / disconnect        |   ✅    | ✅  |
| measurement + progress events      |   ✅    | ✅  |
| `getDeviceAddress` (MAC / UUID)    |   ✅    | ✅  |
| IHB (arrhythmia) on tensiometer    |   ✅    | —   |
| raw BLE inspector (`rawScan` …)    |   ✅    | —   |
| `sendThermometerCommand`           |   ✅    | —   |

On iOS a device is identified by its **CoreBluetooth UUID** (there is no MAC);
the `address` field carries that UUID transparently.

## Install

This package is not published to the npm registry. Install it from GitHub —
either a release tarball (self‑contained for JS + Android) or the git repo:

```bash
# From a GitHub release asset (recommended; includes the built dist/)
npm install https://github.com/ikeralbeniz/lifevit-capacitor/releases/download/v0.0.1/lifevit-capacitor-0.0.1.tgz

# Or straight from git, pinned to a tag (runs the build via the `prepare` script)
npm install github:ikeralbeniz/lifevit-capacitor#v0.0.1
```

Then:

```bash
npx cap sync
```

> On **iOS**, neither install method ships the `LifevitSDK.xcframework` (it lives
> in a git submodule). For iOS, consume the plugin from a `--recurse-submodules`
> git clone, or place the xcframework at
> `ios/vendor/lifevit-ios-sdk/LifevitSDK.xcframework` yourself. Android and web
> work from the tarball/git install directly.

### The Lifevit SDK is NOT bundled

The Lifevit SDK is proprietary and is **not** stored in this repository. It is
pulled in per platform:

- **Android** — downloaded automatically by Gradle from Lifevit's Maven repo.
  Add the repository to your app's **project‑level** `android/build.gradle`:

  ```gradle
  allprojects {
      repositories {
          google()
          mavenCentral()
          maven { url "https://raw.githubusercontent.com/lifevit/android-sdk/master" }
      }
  }
  ```

- **iOS** — vendored as a **git submodule** (`ios/vendor/lifevit-ios-sdk`,
  pinned to a specific commit of [lifevit/ios-sdk](https://github.com/lifevit/ios-sdk)).
  Clone with submodules:

  ```bash
  git clone --recurse-submodules <your-repo-url>
  # or, in an existing clone:
  git submodule update --init --recursive
  ```

  > **npm note:** git submodules are not included in an `npm install` tarball.
  > Consuming this plugin from a published npm package on iOS therefore requires
  > vendoring the `LifevitSDK.xcframework` (or adding a postinstall step). For
  > GitHub clones the submodule approach works out of the box.

Add the Bluetooth usage string to your iOS app's `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Connect to your Lifevit health devices over Bluetooth.</string>
```

## Usage

```ts
import { Lifevit } from 'lifevit-capacitor';

await Lifevit.requestPermissions();

Lifevit.addListener('connectionChanged', (e) => console.log('status', e.status));
Lifevit.addListener('measurement', (e) => {
  // tensiometer: { systolic, diastolic, pulse, ihb?, errorCode, errorMessage }
  // oximeter:    { spo2, pulse: lpm, pi, rpm }
  console.log('measurement', e);
});
Lifevit.addListener('measurementProgress', (e) => console.log('progress', e.progress));

// Connect to the first tensiometer found (the SDK scans internally).
await Lifevit.connect({ deviceType: 'tensiometer', timeout: 15000 });

// When status is 'connected', start the measurement (the cuff inflates).
await Lifevit.startMeasurement();
```

Oximeter, thermometer and scale **stream automatically** while in use — no
`startMeasurement()` needed; the result arrives via the `measurement` event.

### Pairing model

Lifevit devices only advertise while **awake** (powered on / measuring), and the
advertisement carries only the device identity — not the result. So the real
flow is: power on the device → `connect({ deviceType })` → read the discovered
address with `getDeviceAddress()` → store it → reuse it to auto‑connect next
time. See the example app for a complete pairing + measurement UI with retry and
timeout handling.

## Example app

A ready‑to‑run test app lives in [`example/`](./example) (vanilla Capacitor, no
framework). It implements the full flow: permissions on launch, device list,
pair‑by‑measurement, and a measurement sheet with live values, a pulse
waveform, timeout and cancel. See [`example/README.md`](./example/README.md).

## API

<docgen-index>

* [`requestPermissions()`](#requestpermissions)
* [`scan(...)`](#scan)
* [`stopScan()`](#stopscan)
* [`connect(...)`](#connect)
* [`disconnect(...)`](#disconnect)
* [`isConnected(...)`](#isconnected)
* [`getDeviceAddress(...)`](#getdeviceaddress)
* [`startMeasurement()`](#startmeasurement)
* [`sendThermometerCommand(...)`](#sendthermometercommand)
* [`rawScan(...)`](#rawscan)
* [`rawStopScan()`](#rawstopscan)
* [`rawConnect(...)`](#rawconnect)
* [`rawDisconnect()`](#rawdisconnect)
* [`addListener('rawScanResult', ...)`](#addlistenerrawscanresult-)
* [`addListener('rawConnection', ...)`](#addlistenerrawconnection-)
* [`addListener('rawServices', ...)`](#addlistenerrawservices-)
* [`addListener('rawNotify', ...)`](#addlistenerrawnotify-)
* [`addListener('scanResult', ...)`](#addlistenerscanresult-)
* [`addListener('connectionChanged', ...)`](#addlistenerconnectionchanged-)
* [`addListener('connectionError', ...)`](#addlistenerconnectionerror-)
* [`addListener('measurement', ...)`](#addlistenermeasurement-)
* [`addListener('measurementProgress', ...)`](#addlistenermeasurementprogress-)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### requestPermissions()

```typescript
requestPermissions() => Promise<PermissionStatus>
```

Solicita permisos de Bluetooth/ubicación (Android).

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### scan(...)

```typescript
scan(options?: { timeout?: number | undefined; } | undefined) => Promise<void>
```

Escanea todos los dispositivos Lifevit cercanos. Cada dispositivo
encontrado se emite por el evento `scanResult`.

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ timeout?: number; }</code> |

--------------------


### stopScan()

```typescript
stopScan() => Promise<void>
```

Detiene el escaneo iniciado con `scan()`.

--------------------


### connect(...)

```typescript
connect(options: { deviceType: LifevitDeviceType; address?: string; timeout?: number; }) => Promise<void>
```

Conecta con un dispositivo. Si se indica `address`, conecta con ese
dispositivo concreto; si no, con el primero de ese tipo que encuentre.
El SDK escanea internamente durante `timeout` ms (por defecto 10000).

| Param         | Type                                                                                                                 |
| ------------- | -------------------------------------------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceType: <a href="#lifevitdevicetype">LifevitDeviceType</a>; address?: string; timeout?: number; }</code> |

--------------------


### disconnect(...)

```typescript
disconnect(options: { deviceType: LifevitDeviceType; }) => Promise<void>
```

Desconecta el dispositivo del tipo indicado.

| Param         | Type                                                                             |
| ------------- | -------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceType: <a href="#lifevitdevicetype">LifevitDeviceType</a>; }</code> |

--------------------


### isConnected(...)

```typescript
isConnected(options: { deviceType: LifevitDeviceType; }) => Promise<{ connected: boolean; }>
```

Indica si hay un dispositivo de ese tipo conectado.

| Param         | Type                                                                             |
| ------------- | -------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceType: <a href="#lifevitdevicetype">LifevitDeviceType</a>; }</code> |

**Returns:** <code>Promise&lt;{ connected: boolean; }&gt;</code>

--------------------


### getDeviceAddress(...)

```typescript
getDeviceAddress(options: { deviceType: LifevitDeviceType; }) => Promise<{ address: string; }>
```

MAC del dispositivo de ese tipo actualmente conectado (vacía si ninguno).

| Param         | Type                                                                             |
| ------------- | -------------------------------------------------------------------------------- |
| **`options`** | <code>{ deviceType: <a href="#lifevitdevicetype">LifevitDeviceType</a>; }</code> |

**Returns:** <code>Promise&lt;{ address: string; }&gt;</code>

--------------------


### startMeasurement()

```typescript
startMeasurement() => Promise<void>
```

Inicia una medida en el tensiómetro conectado. (El oxímetro y el
termómetro empiezan a medir solos al conectar / al usar el aparato.)

--------------------


### sendThermometerCommand(...)

```typescript
sendThermometerCommand(options: { command: number; }) => Promise<void>
```

Envía un comando al termómetro (p. ej. cambiar unidad o pedir la última
medida). Ver constantes THERMOMETERV2_COMMAND_* del SDK.

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ command: number; }</code> |

--------------------


### rawScan(...)

```typescript
rawScan(options?: { timeout?: number | undefined; } | undefined) => Promise<void>
```

Escaneo BLE en crudo: emite `rawScanResult` con datos de anuncio (manufacturerData).

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ timeout?: number; }</code> |

--------------------


### rawStopScan()

```typescript
rawStopScan() => Promise<void>
```

--------------------


### rawConnect(...)

```typescript
rawConnect(options: { address: string; }) => Promise<void>
```

Conecta por GATT a una MAC y vuelca servicios/características (`rawServices`) y notificaciones (`rawNotify`).

| Param         | Type                              |
| ------------- | --------------------------------- |
| **`options`** | <code>{ address: string; }</code> |

--------------------


### rawDisconnect()

```typescript
rawDisconnect() => Promise<void>
```

--------------------


### addListener('rawScanResult', ...)

```typescript
addListener(eventName: 'rawScanResult', listenerFunc: (e: RawScanResultEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                              |
| ------------------ | --------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'rawScanResult'</code>                                                      |
| **`listenerFunc`** | <code>(e: <a href="#rawscanresultevent">RawScanResultEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('rawConnection', ...)

```typescript
addListener(eventName: 'rawConnection', listenerFunc: (e: { state: string; }) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                            |
| ------------------ | ----------------------------------------------- |
| **`eventName`**    | <code>'rawConnection'</code>                    |
| **`listenerFunc`** | <code>(e: { state: string; }) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('rawServices', ...)

```typescript
addListener(eventName: 'rawServices', listenerFunc: (e: RawServicesEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                          |
| ------------------ | ----------------------------------------------------------------------------- |
| **`eventName`**    | <code>'rawServices'</code>                                                    |
| **`listenerFunc`** | <code>(e: <a href="#rawservicesevent">RawServicesEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('rawNotify', ...)

```typescript
addListener(eventName: 'rawNotify', listenerFunc: (e: RawNotifyEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                      |
| ------------------ | ------------------------------------------------------------------------- |
| **`eventName`**    | <code>'rawNotify'</code>                                                  |
| **`listenerFunc`** | <code>(e: <a href="#rawnotifyevent">RawNotifyEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('scanResult', ...)

```typescript
addListener(eventName: 'scanResult', listenerFunc: (e: ScanResultEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                        |
| ------------------ | --------------------------------------------------------------------------- |
| **`eventName`**    | <code>'scanResult'</code>                                                   |
| **`listenerFunc`** | <code>(e: <a href="#scanresultevent">ScanResultEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('connectionChanged', ...)

```typescript
addListener(eventName: 'connectionChanged', listenerFunc: (e: ConnectionEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                        |
| ------------------ | --------------------------------------------------------------------------- |
| **`eventName`**    | <code>'connectionChanged'</code>                                            |
| **`listenerFunc`** | <code>(e: <a href="#connectionevent">ConnectionEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('connectionError', ...)

```typescript
addListener(eventName: 'connectionError', listenerFunc: (e: ConnectionErrorEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'connectionError'</code>                                                        |
| **`listenerFunc`** | <code>(e: <a href="#connectionerrorevent">ConnectionErrorEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('measurement', ...)

```typescript
addListener(eventName: 'measurement', listenerFunc: (e: MeasurementEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                          |
| ------------------ | ----------------------------------------------------------------------------- |
| **`eventName`**    | <code>'measurement'</code>                                                    |
| **`listenerFunc`** | <code>(e: <a href="#measurementevent">MeasurementEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### addListener('measurementProgress', ...)

```typescript
addListener(eventName: 'measurementProgress', listenerFunc: (e: MeasurementProgressEvent) => void) => Promise<PluginListenerHandle>
```

| Param              | Type                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'measurementProgress'</code>                                                            |
| **`listenerFunc`** | <code>(e: <a href="#measurementprogressevent">MeasurementProgressEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt;</code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### PermissionStatus

| Prop          | Type                 |
| ------------- | -------------------- |
| **`granted`** | <code>boolean</code> |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### RawScanResultEvent

Un anuncio BLE en crudo (evento `rawScanResult`).

| Prop                   | Type                                                                              | Description                                                               |
| ---------------------- | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **`address`**          | <code>string</code>                                                               |                                                                           |
| **`name`**             | <code>string</code>                                                               |                                                                           |
| **`rssi`**             | <code>number</code>                                                               |                                                                           |
| **`manufacturerData`** | <code><a href="#array">Array</a>&lt;{ companyId: number; hex: string }&gt;</code> | Datos de fabricante en el anuncio (clave para detectar emisión "pasiva"). |
| **`raw`**              | <code>string</code>                                                               | Advertisement completo en hex.                                            |


#### Array

| Prop         | Type                | Description                                                                                            |
| ------------ | ------------------- | ------------------------------------------------------------------------------------------------------ |
| **`length`** | <code>number</code> | Gets or sets the length of the array. This is a number one higher than the highest index in the array. |

| Method             | Signature                                                                                                                     | Description                                                                                                                                                                                                                                 |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **toString**       | () =&gt; string                                                                                                               | Returns a string representation of an array.                                                                                                                                                                                                |
| **toLocaleString** | () =&gt; string                                                                                                               | Returns a string representation of an array. The elements are converted to string using their toLocalString methods.                                                                                                                        |
| **pop**            | () =&gt; T \| undefined                                                                                                       | Removes the last element from an array and returns it. If the array is empty, undefined is returned and the array is not modified.                                                                                                          |
| **push**           | (...items: T[]) =&gt; number                                                                                                  | Appends new elements to the end of an array, and returns the new length of the array.                                                                                                                                                       |
| **concat**         | (...items: <a href="#concatarray">ConcatArray</a>&lt;T&gt;[]) =&gt; T[]                                                       | Combines two or more arrays. This method returns a new array without modifying any existing arrays.                                                                                                                                         |
| **concat**         | (...items: (T \| <a href="#concatarray">ConcatArray</a>&lt;T&gt;)[]) =&gt; T[]                                                | Combines two or more arrays. This method returns a new array without modifying any existing arrays.                                                                                                                                         |
| **join**           | (separator?: string \| undefined) =&gt; string                                                                                | Adds all the elements of an array into a string, separated by the specified separator string.                                                                                                                                               |
| **reverse**        | () =&gt; T[]                                                                                                                  | Reverses the elements in an array in place. This method mutates the array and returns a reference to the same array.                                                                                                                        |
| **shift**          | () =&gt; T \| undefined                                                                                                       | Removes the first element from an array and returns it. If the array is empty, undefined is returned and the array is not modified.                                                                                                         |
| **slice**          | (start?: number \| undefined, end?: number \| undefined) =&gt; T[]                                                            | Returns a copy of a section of an array. For both start and end, a negative index can be used to indicate an offset from the end of the array. For example, -2 refers to the second to last element of the array.                           |
| **sort**           | (compareFn?: ((a: T, b: T) =&gt; number) \| undefined) =&gt; this                                                             | Sorts an array in place. This method mutates the array and returns a reference to the same array.                                                                                                                                           |
| **splice**         | (start: number, deleteCount?: number \| undefined) =&gt; T[]                                                                  | Removes elements from an array and, if necessary, inserts new elements in their place, returning the deleted elements.                                                                                                                      |
| **splice**         | (start: number, deleteCount: number, ...items: T[]) =&gt; T[]                                                                 | Removes elements from an array and, if necessary, inserts new elements in their place, returning the deleted elements.                                                                                                                      |
| **unshift**        | (...items: T[]) =&gt; number                                                                                                  | Inserts new elements at the start of an array, and returns the new length of the array.                                                                                                                                                     |
| **indexOf**        | (searchElement: T, fromIndex?: number \| undefined) =&gt; number                                                              | Returns the index of the first occurrence of a value in an array, or -1 if it is not present.                                                                                                                                               |
| **lastIndexOf**    | (searchElement: T, fromIndex?: number \| undefined) =&gt; number                                                              | Returns the index of the last occurrence of a specified value in an array, or -1 if it is not present.                                                                                                                                      |
| **every**          | &lt;S extends T&gt;(predicate: (value: T, index: number, array: T[]) =&gt; value is S, thisArg?: any) =&gt; this is S[]       | Determines whether all the members of an array satisfy the specified test.                                                                                                                                                                  |
| **every**          | (predicate: (value: T, index: number, array: T[]) =&gt; unknown, thisArg?: any) =&gt; boolean                                 | Determines whether all the members of an array satisfy the specified test.                                                                                                                                                                  |
| **some**           | (predicate: (value: T, index: number, array: T[]) =&gt; unknown, thisArg?: any) =&gt; boolean                                 | Determines whether the specified callback function returns true for any element of an array.                                                                                                                                                |
| **forEach**        | (callbackfn: (value: T, index: number, array: T[]) =&gt; void, thisArg?: any) =&gt; void                                      | Performs the specified action for each element in an array.                                                                                                                                                                                 |
| **map**            | &lt;U&gt;(callbackfn: (value: T, index: number, array: T[]) =&gt; U, thisArg?: any) =&gt; U[]                                 | Calls a defined callback function on each element of an array, and returns an array that contains the results.                                                                                                                              |
| **filter**         | &lt;S extends T&gt;(predicate: (value: T, index: number, array: T[]) =&gt; value is S, thisArg?: any) =&gt; S[]               | Returns the elements of an array that meet the condition specified in a callback function.                                                                                                                                                  |
| **filter**         | (predicate: (value: T, index: number, array: T[]) =&gt; unknown, thisArg?: any) =&gt; T[]                                     | Returns the elements of an array that meet the condition specified in a callback function.                                                                                                                                                  |
| **reduce**         | (callbackfn: (previousValue: T, currentValue: T, currentIndex: number, array: T[]) =&gt; T) =&gt; T                           | Calls the specified callback function for all the elements in an array. The return value of the callback function is the accumulated result, and is provided as an argument in the next call to the callback function.                      |
| **reduce**         | (callbackfn: (previousValue: T, currentValue: T, currentIndex: number, array: T[]) =&gt; T, initialValue: T) =&gt; T          |                                                                                                                                                                                                                                             |
| **reduce**         | &lt;U&gt;(callbackfn: (previousValue: U, currentValue: T, currentIndex: number, array: T[]) =&gt; U, initialValue: U) =&gt; U | Calls the specified callback function for all the elements in an array. The return value of the callback function is the accumulated result, and is provided as an argument in the next call to the callback function.                      |
| **reduceRight**    | (callbackfn: (previousValue: T, currentValue: T, currentIndex: number, array: T[]) =&gt; T) =&gt; T                           | Calls the specified callback function for all the elements in an array, in descending order. The return value of the callback function is the accumulated result, and is provided as an argument in the next call to the callback function. |
| **reduceRight**    | (callbackfn: (previousValue: T, currentValue: T, currentIndex: number, array: T[]) =&gt; T, initialValue: T) =&gt; T          |                                                                                                                                                                                                                                             |
| **reduceRight**    | &lt;U&gt;(callbackfn: (previousValue: U, currentValue: T, currentIndex: number, array: T[]) =&gt; U, initialValue: U) =&gt; U | Calls the specified callback function for all the elements in an array, in descending order. The return value of the callback function is the accumulated result, and is provided as an argument in the next call to the callback function. |


#### ConcatArray

| Prop         | Type                |
| ------------ | ------------------- |
| **`length`** | <code>number</code> |

| Method    | Signature                                                          |
| --------- | ------------------------------------------------------------------ |
| **join**  | (separator?: string \| undefined) =&gt; string                     |
| **slice** | (start?: number \| undefined, end?: number \| undefined) =&gt; T[] |


#### RawServicesEvent

| Prop           | Type                                                                                                                                                                                                      |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`services`** | <code><a href="#array">Array</a>&lt;{ uuid: string; characteristics: <a href="#array">Array</a>&lt;{ uuid: string; notify: boolean; indicate: boolean; read: boolean; write: boolean; }&gt;; }&gt;</code> |


#### RawNotifyEvent

Una notificación GATT en crudo (evento `rawNotify`).

| Prop                 | Type                |
| -------------------- | ------------------- |
| **`service`**        | <code>string</code> |
| **`characteristic`** | <code>string</code> |
| **`hex`**            | <code>string</code> |
| **`ascii`**          | <code>string</code> |


#### ScanResultEvent

Un dispositivo encontrado durante el escaneo (evento `scanResult`).

| Prop             | Type                | Description                                          |
| ---------------- | ------------------- | ---------------------------------------------------- |
| **`deviceType`** | <code>string</code> | Tipo de dispositivo detectado.                       |
| **`address`**    | <code>string</code> | Dirección MAC (Android).                             |
| **`rssi`**       | <code>number</code> | Intensidad de señal.                                 |
| **`distance`**   | <code>number</code> | Distancia aproximada en metros calculada por el SDK. |


#### ConnectionEvent

Cambio de estado de conexión (evento `connectionChanged`).

| Prop             | Type                                                                        |
| ---------------- | --------------------------------------------------------------------------- |
| **`deviceType`** | <code>string</code>                                                         |
| **`status`**     | <code><a href="#lifevitconnectionstatus">LifevitConnectionStatus</a></code> |


#### ConnectionErrorEvent

Error de conexión (evento `connectionError`).

| Prop             | Type                |
| ---------------- | ------------------- |
| **`deviceType`** | <code>string</code> |
| **`errorCode`**  | <code>number</code> |
| **`message`**    | <code>string</code> |


#### MeasurementEvent

Resultado de una medida (evento `measurement`). Los campos presentes
dependen del tipo de dispositivo que la emite.

| Prop                | Type                 | Description                                         |
| ------------------- | -------------------- | --------------------------------------------------- |
| **`deviceType`**    | <code>string</code>  |                                                     |
| **`date`**          | <code>number</code>  | Marca de tiempo (ms) si el dispositivo la aporta.   |
| **`systolic`**      | <code>number</code>  |                                                     |
| **`diastolic`**     | <code>number</code>  |                                                     |
| **`pulse`**         | <code>number</code>  |                                                     |
| **`ihb`**           | <code>boolean</code> | Latido irregular detectado (arritmia / IHB).        |
| **`errorCode`**     | <code>number</code>  | Código de error de la medida (0 = OK).              |
| **`errorMessage`**  | <code>string</code>  | Mensaje legible del error de medida ('' si OK).     |
| **`spo2`**          | <code>number</code>  |                                                     |
| **`pi`**            | <code>number</code>  |                                                     |
| **`rpm`**           | <code>number</code>  |                                                     |
| **`lpm`**           | <code>number</code>  |                                                     |
| **`mode`**          | <code>number</code>  | 0 ambiente, 1 cuerpo, 2 oído, 3 frente.             |
| **`unit`**          | <code>number</code>  | Unidad devuelta por el termómetro (código del SDK). |
| **`value`**         | <code>number</code>  |                                                     |
| **`weight`**        | <code>number</code>  |                                                     |
| **`imc`**           | <code>number</code>  |                                                     |
| **`fatPercentage`** | <code>number</code>  |                                                     |


#### MeasurementProgressEvent

Progreso de medida en tiempo real (evento `measurementProgress`).

| Prop             | Type                | Description                                                 |
| ---------------- | ------------------- | ----------------------------------------------------------- |
| **`deviceType`** | <code>string</code> |                                                             |
| **`progress`**   | <code>number</code> | Valor crudo de progreso (pletismografía en oxímetro, etc.). |


### Type Aliases


#### LifevitDeviceType

Tipos de dispositivo soportados por este wrapper.
(Subconjunto del SDK de Lifevit centrado en scan/connect/medida.)

<code>'tensiometer' | 'oximeter' | 'thermometer' | 'weightscale'</code>


#### LifevitConnectionStatus

Estado de la conexión de un dispositivo.

<code>'disconnected' | 'scanning' | 'connecting' | 'connected'</code>

</docgen-api>

## License

[MIT](./LICENSE) — free for commercial use. The license covers **this plugin's
code only**; the Lifevit SDK is proprietary and governed by Lifevit's own
license. See the note in [LICENSE](./LICENSE).
