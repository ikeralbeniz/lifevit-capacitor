import type { PluginListenerHandle } from '@capacitor/core';

/**
 * Tipos de dispositivo soportados por este wrapper.
 * (Subconjunto del SDK de Lifevit centrado en scan/connect/medida.)
 */
export type LifevitDeviceType = 'tensiometer' | 'oximeter' | 'thermometer' | 'weightscale';

/** Estado de la conexión de un dispositivo. */
export type LifevitConnectionStatus = 'disconnected' | 'scanning' | 'connecting' | 'connected';

export interface PermissionStatus {
  granted: boolean;
}

/** Un dispositivo encontrado durante el escaneo (evento `scanResult`). */
export interface ScanResultEvent {
  /** Tipo de dispositivo detectado. */
  deviceType: LifevitDeviceType | string;
  /** Dirección MAC (Android). */
  address: string;
  /** Intensidad de señal. */
  rssi: number;
  /** Distancia aproximada en metros calculada por el SDK. */
  distance: number;
}

/** Cambio de estado de conexión (evento `connectionChanged`). */
export interface ConnectionEvent {
  deviceType: LifevitDeviceType | string;
  status: LifevitConnectionStatus;
}

/** Error de conexión (evento `connectionError`). */
export interface ConnectionErrorEvent {
  deviceType: LifevitDeviceType | string;
  errorCode: number;
  message: string;
}

/**
 * Resultado de una medida (evento `measurement`). Los campos presentes
 * dependen del tipo de dispositivo que la emite.
 */
export interface MeasurementEvent {
  deviceType: LifevitDeviceType | string;
  /** Marca de tiempo (ms) si el dispositivo la aporta. */
  date?: number;

  // Tensiómetro
  systolic?: number;
  diastolic?: number;
  pulse?: number;
  /** Latido irregular detectado (arritmia / IHB). */
  ihb?: boolean;
  /** Código de error de la medida (0 = OK). */
  errorCode?: number;
  /** Mensaje legible del error de medida ('' si OK). */
  errorMessage?: string;

  // Pulsioxímetro
  spo2?: number;
  pi?: number;
  rpm?: number;
  lpm?: number;

  // Termómetro
  /** 0 ambiente, 1 cuerpo, 2 oído, 3 frente. */
  mode?: number;
  /** Unidad devuelta por el termómetro (código del SDK). */
  unit?: number;
  value?: number;

  // Báscula
  weight?: number;
  imc?: number;
  fatPercentage?: number;
}

/** Progreso de medida en tiempo real (evento `measurementProgress`). */
export interface MeasurementProgressEvent {
  deviceType: LifevitDeviceType | string;
  /** Valor crudo de progreso (pletismografía en oxímetro, etc.). */
  progress: number;
}

/** Un anuncio BLE en crudo (evento `rawScanResult`). */
export interface RawScanResultEvent {
  address: string;
  name: string;
  rssi: number;
  /** Datos de fabricante en el anuncio (clave para detectar emisión "pasiva"). */
  manufacturerData: Array<{ companyId: number; hex: string }>;
  /** Advertisement completo en hex. */
  raw?: string;
}
export interface RawServicesEvent {
  services: Array<{
    uuid: string;
    characteristics: Array<{
      uuid: string;
      notify: boolean;
      indicate: boolean;
      read: boolean;
      write: boolean;
    }>;
  }>;
}
/** Una notificación GATT en crudo (evento `rawNotify`). */
export interface RawNotifyEvent {
  service: string;
  characteristic: string;
  hex: string;
  ascii: string;
}

export interface LifevitPlugin {
  /** Solicita permisos de Bluetooth/ubicación (Android). */
  requestPermissions(): Promise<PermissionStatus>;

  /**
   * Escanea todos los dispositivos Lifevit cercanos. Cada dispositivo
   * encontrado se emite por el evento `scanResult`.
   */
  scan(options?: { timeout?: number }): Promise<void>;

  /** Detiene el escaneo iniciado con `scan()`. */
  stopScan(): Promise<void>;

  /**
   * Conecta con un dispositivo. Si se indica `address`, conecta con ese
   * dispositivo concreto; si no, con el primero de ese tipo que encuentre.
   * El SDK escanea internamente durante `timeout` ms (por defecto 10000).
   */
  connect(options: { deviceType: LifevitDeviceType; address?: string; timeout?: number }): Promise<void>;

  /** Desconecta el dispositivo del tipo indicado. */
  disconnect(options: { deviceType: LifevitDeviceType }): Promise<void>;

  /** Indica si hay un dispositivo de ese tipo conectado. */
  isConnected(options: { deviceType: LifevitDeviceType }): Promise<{ connected: boolean }>;

  /** MAC del dispositivo de ese tipo actualmente conectado (vacía si ninguno). */
  getDeviceAddress(options: { deviceType: LifevitDeviceType }): Promise<{ address: string }>;

  /**
   * Inicia una medida en el tensiómetro conectado. (El oxímetro y el
   * termómetro empiezan a medir solos al conectar / al usar el aparato.)
   */
  startMeasurement(): Promise<void>;

  /**
   * Envía un comando al termómetro (p. ej. cambiar unidad o pedir la última
   * medida). Ver constantes THERMOMETERV2_COMMAND_* del SDK.
   */
  sendThermometerCommand(options: { command: number }): Promise<void>;

  // ---- Inspector BLE genérico (independiente del SDK de Lifevit) ----
  /** Escaneo BLE en crudo: emite `rawScanResult` con datos de anuncio (manufacturerData). */
  rawScan(options?: { timeout?: number }): Promise<void>;
  rawStopScan(): Promise<void>;
  /** Conecta por GATT a una MAC y vuelca servicios/características (`rawServices`) y notificaciones (`rawNotify`). */
  rawConnect(options: { address: string }): Promise<void>;
  rawDisconnect(): Promise<void>;

  addListener(eventName: 'rawScanResult', listenerFunc: (e: RawScanResultEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'rawConnection', listenerFunc: (e: { state: string }) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'rawServices', listenerFunc: (e: RawServicesEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'rawNotify', listenerFunc: (e: RawNotifyEvent) => void): Promise<PluginListenerHandle>;

  addListener(eventName: 'scanResult', listenerFunc: (e: ScanResultEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'connectionChanged', listenerFunc: (e: ConnectionEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'connectionError', listenerFunc: (e: ConnectionErrorEvent) => void): Promise<PluginListenerHandle>;
  addListener(eventName: 'measurement', listenerFunc: (e: MeasurementEvent) => void): Promise<PluginListenerHandle>;
  addListener(
    eventName: 'measurementProgress',
    listenerFunc: (e: MeasurementProgressEvent) => void,
  ): Promise<PluginListenerHandle>;

  removeAllListeners(): Promise<void>;
}
