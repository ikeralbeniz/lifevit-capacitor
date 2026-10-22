package io.naiha.lifevit;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;

import com.getcapacitor.JSArray;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import es.lifevit.sdk.LifevitSDKConstants;
import es.lifevit.sdk.LifevitSDKDeviceScanData;
import es.lifevit.sdk.LifevitSDKHeartData;
import es.lifevit.sdk.LifevitSDKManager;
import es.lifevit.sdk.LifevitSDKOximeterData;
import es.lifevit.sdk.listeners.LifevitSDKAllDevicesListener;
import es.lifevit.sdk.listeners.LifevitSDKDeviceListener;
import es.lifevit.sdk.listeners.LifevitSDKHeartListener;
import es.lifevit.sdk.listeners.LifevitSDKOximeterListener;
import es.lifevit.sdk.listeners.LifevitSDKThermometerListener;
import es.lifevit.sdk.listeners.LifevitSDKWeightScaleListener;
import es.lifevit.sdk.weightscale.LifevitSDKWeightScaleData;

/**
 * Wrapper Capacitor del SDK de Lifevit, centrado en lo mínimo para probar un
 * dispositivo: escanear, conectar y tomar una medida.
 *
 * Eventos emitidos a JS:
 *  - scanResult          (cada dispositivo encontrado)
 *  - connectionChanged   (estado: disconnected/scanning/connecting/connected)
 *  - connectionError     (errorCode + mensaje)
 *  - measurement         (resultado de la medida)
 *  - measurementProgress (progreso en tiempo real)
 */
@CapacitorPlugin(
    name = "Lifevit",
    permissions = {
        @Permission(alias = "bluetooth", strings = { Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT }),
        @Permission(alias = "location", strings = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION })
    }
)
public class LifevitPlugin extends Plugin {

    private static final String TAG = "LifevitPlugin";

    private LifevitSDKManager manager;

    // ---------------------------------------------------------- Tipos
    private int deviceTypeToInt(String type) {
        if (type == null) return LifevitSDKConstants.DEVICE_OTHERS;
        switch (type) {
            case "tensiometer": return LifevitSDKConstants.DEVICE_TENSIOMETER;
            case "oximeter": return LifevitSDKConstants.DEVICE_OXIMETER;
            case "thermometer": return LifevitSDKConstants.DEVICE_THERMOMETER;
            case "weightscale": return LifevitSDKConstants.DEVICE_WEIGHT_SCALE;
            default: return LifevitSDKConstants.DEVICE_OTHERS;
        }
    }

    private String intToDeviceType(int type) {
        switch (type) {
            case LifevitSDKConstants.DEVICE_TENSIOMETER: return "tensiometer";
            case LifevitSDKConstants.DEVICE_OXIMETER: return "oximeter";
            case LifevitSDKConstants.DEVICE_THERMOMETER: return "thermometer";
            case LifevitSDKConstants.DEVICE_WEIGHT_SCALE: return "weightscale";
            default: return "unknown";
        }
    }

    private String statusToString(int status) {
        switch (status) {
            case LifevitSDKConstants.STATUS_SCANNING: return "scanning";
            case LifevitSDKConstants.STATUS_CONNECTING: return "connecting";
            case LifevitSDKConstants.STATUS_CONNECTED: return "connected";
            case LifevitSDKConstants.STATUS_DISCONNECTED:
            default: return "disconnected";
        }
    }

    private String errorToMessage(int errorCode) {
        switch (errorCode) {
            case LifevitSDKConstants.CODE_LOCATION_DISABLED: return "Permisos de ubicación desactivados";
            case LifevitSDKConstants.CODE_BLUETOOTH_DISABLED: return "El Bluetooth no está activado";
            case LifevitSDKConstants.CODE_LOCATION_TURN_OFF: return "La ubicación está apagada";
            default: return "Error desconocido (" + errorCode + ")";
        }
    }

    /** Mensaje del código de error que acompaña a una medida (HeartData.getErrorCode()). */
    private String measureErrorMessage(int code) {
        switch (code) {
            case LifevitSDKConstants.CODE_OK: return "";
            case LifevitSDKConstants.CODE_LOW_SIGNAL: return "Señal baja";
            case LifevitSDKConstants.CODE_NOISE: return "Ruido / movimiento durante la medida";
            case LifevitSDKConstants.CODE_INFLATION_TIME: return "Problema al inflar el brazalete";
            case LifevitSDKConstants.CODE_ABNORMAL_RESULT: return "Resultado anómalo";
            case LifevitSDKConstants.CODE_RETRY: return "Reintenta la medida";
            case LifevitSDKConstants.CODE_LOW_BATTERY: return "Batería baja del dispositivo";
            case LifevitSDKConstants.CODE_NO_RESULTS: return "Sin resultados";
            case LifevitSDKConstants.CODE_TOO_MUCH_INTERFERENCE: return "Demasiada interferencia";
            case LifevitSDKConstants.CODE_WRONG_PARAMETERS: return "Parámetros incorrectos";
            default: return code == 0 ? "" : "Error de medida (" + code + ")";
        }
    }

    // ---------------------------------------------------------- Ciclo de vida
    @Override
    public void load() {
        try {
            Context app = getContext().getApplicationContext();
            manager = new LifevitSDKManager(app);
            manager.setLogLevel(Log.DEBUG);

            manager.addDeviceListener(deviceListener);
            manager.setAllDevicesListener(allDevicesListener);
            manager.setHeartListener(heartListener);
            manager.setOximeterListener(oximeterListener);
            manager.setThermometerListener(thermometerListener);
            manager.setWeightScaleListener(weightScaleListener);
        } catch (Throwable t) {
            Log.e(TAG, "Error inicializando LifevitSDKManager", t);
        }
    }

    // ---------------------------------------------------------- Permisos
    @PluginMethod
    @Override
    public void requestPermissions(PluginCall call) {
        String[] aliases = Build.VERSION.SDK_INT >= 31
            ? new String[] { "bluetooth", "location" }
            : new String[] { "location" };
        requestPermissionForAliases(aliases, call, "permsCallback");
    }

    @PermissionCallback
    private void permsCallback(PluginCall call) {
        boolean granted = getPermissionState("location") == PermissionState.GRANTED;
        if (Build.VERSION.SDK_INT >= 31) {
            granted = granted && getPermissionState("bluetooth") == PermissionState.GRANTED;
        }
        JSObject r = new JSObject();
        r.put("granted", granted);
        call.resolve(r);
    }

    // ---------------------------------------------------------- Scan
    @PluginMethod
    public void scan(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        int timeout = call.getInt("timeout", 10000);
        manager.connectDevice(LifevitSDKConstants.DEVICE_OTHERS, timeout);
        call.resolve();
    }

    @PluginMethod
    public void stopScan(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        manager.disconnectDevice(LifevitSDKConstants.DEVICE_OTHERS);
        call.resolve();
    }

    // ---------------------------------------------------------- Conexión
    @PluginMethod
    public void connect(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        String type = call.getString("deviceType");
        if (type == null) { call.reject("deviceType requerido"); return; }
        int timeout = call.getInt("timeout", 10000);
        String address = call.getString("address");
        int typeInt = deviceTypeToInt(type);
        if (address != null && !address.isEmpty()) {
            manager.connectDevice(typeInt, timeout, address);
        } else {
            manager.connectDevice(typeInt, timeout);
        }
        call.resolve();
    }

    @PluginMethod
    public void disconnect(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        String type = call.getString("deviceType");
        if (type == null) { call.reject("deviceType requerido"); return; }
        manager.disconnectDevice(deviceTypeToInt(type));
        call.resolve();
    }

    @PluginMethod
    public void getDeviceAddress(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        String type = call.getString("deviceType");
        if (type == null) { call.reject("deviceType requerido"); return; }
        String addr = manager.getDeviceAddress(deviceTypeToInt(type));
        JSObject r = new JSObject();
        r.put("address", addr != null ? addr : "");
        call.resolve(r);
    }

    @PluginMethod
    public void isConnected(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        String type = call.getString("deviceType");
        if (type == null) { call.reject("deviceType requerido"); return; }
        JSObject r = new JSObject();
        r.put("connected", manager.isDeviceConnected(deviceTypeToInt(type)));
        call.resolve(r);
    }

    // ---------------------------------------------------------- Medida
    @PluginMethod
    public void startMeasurement(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        manager.startMeasurement();
        call.resolve();
    }

    @PluginMethod
    public void sendThermometerCommand(PluginCall call) {
        if (manager == null) { call.reject("SDK no inicializado"); return; }
        Integer command = call.getInt("command");
        if (command == null) { call.reject("command requerido"); return; }
        manager.sendThermometerCommand(command);
        call.resolve();
    }

    // ---------------------------------------------------------- Listeners SDK
    private final LifevitSDKDeviceListener deviceListener = new LifevitSDKDeviceListener() {
        @Override
        public void deviceOnConnectionError(int deviceType, int errorCode) {
            JSObject o = new JSObject();
            o.put("deviceType", intToDeviceType(deviceType));
            o.put("errorCode", errorCode);
            o.put("message", errorToMessage(errorCode));
            notifyListeners("connectionError", o);
        }

        @Override
        public void deviceOnConnectionChanged(int deviceType, int status) {
            JSObject o = new JSObject();
            o.put("deviceType", intToDeviceType(deviceType));
            o.put("status", statusToString(status));
            notifyListeners("connectionChanged", o);
        }
    };

    private final LifevitSDKAllDevicesListener allDevicesListener = new LifevitSDKAllDevicesListener() {
        @Override
        public void allDevicesDetected(ConcurrentHashMap<Integer, List<LifevitSDKDeviceScanData>> allResults) {
            for (Map.Entry<Integer, List<LifevitSDKDeviceScanData>> entry : allResults.entrySet()) {
                String type = intToDeviceType(entry.getKey());
                for (LifevitSDKDeviceScanData d : entry.getValue()) {
                    JSObject o = new JSObject();
                    o.put("deviceType", type);
                    o.put("address", d.getAddress());
                    o.put("rssi", d.getRssi());
                    o.put("distance", d.getDistanceMetersCalculated());
                    notifyListeners("scanResult", o);
                }
            }
        }
    };

    // Tensiómetro (BPM-xxx): resultados vía "heart".
    private final LifevitSDKHeartListener heartListener = new LifevitSDKHeartListener() {
        @Override
        public void heartDeviceOnProgressMeasurement(int progress) {
            emitProgress("tensiometer", progress);
        }

        @Override
        public void heartDeviceOnBatteryResult(int battery) {
            JSObject o = new JSObject();
            o.put("deviceType", "tensiometer");
            o.put("battery", battery);
            notifyListeners("measurement", o);
        }

        @Override
        public void heartDeviceOnResult(LifevitSDKHeartData result) {
            JSObject o = new JSObject();
            o.put("deviceType", "tensiometer");
            o.put("systolic", result.getSystolic());
            o.put("diastolic", result.getDiastolic());
            o.put("pulse", result.getPulse());
            o.put("ihb", result.isIHB());        // latido irregular / arritmia
            o.put("date", result.getDate());
            int errCode = result.getErrorCode();
            o.put("errorCode", errCode);
            o.put("errorMessage", measureErrorMessage(errCode));
            notifyListeners("measurement", o);
        }
    };

    private final LifevitSDKOximeterListener oximeterListener = new LifevitSDKOximeterListener() {
        @Override
        public void oximeterDeviceOnProgressMeasurement(int pleth) {
            emitProgress("oximeter", pleth);
        }

        @Override
        public void oximeterDeviceOnResult(LifevitSDKOximeterData data) {
            JSObject o = new JSObject();
            o.put("deviceType", "oximeter");
            o.put("spo2", data.getSpO2());
            o.put("pi", data.getPi());
            o.put("rpm", data.getRpm());
            o.put("lpm", data.getLpm());
            o.put("date", data.getDate());
            notifyListeners("measurement", o);
        }
    };

    private final LifevitSDKThermometerListener thermometerListener = new LifevitSDKThermometerListener() {
        @Override
        public void onThermometerDeviceResult(int mode, int unit, double value) {
            JSObject o = new JSObject();
            o.put("deviceType", "thermometer");
            o.put("mode", mode);
            o.put("unit", unit);
            o.put("value", value);
            notifyListeners("measurement", o);
        }

        @Override
        public void onThermometerCommandSuccess(int command, int extra) {
            // No lo propagamos como medida; disponible si se necesita en el futuro.
        }

        @Override
        public void onThermometerDeviceError(int errorCode) {
            JSObject o = new JSObject();
            o.put("deviceType", "thermometer");
            o.put("errorCode", errorCode);
            o.put("message", errorToMessage(errorCode));
            notifyListeners("connectionError", o);
        }
    };

    private final LifevitSDKWeightScaleListener weightScaleListener = new LifevitSDKWeightScaleListener() {
        @Override
        public void onScaleMeasurementOnlyWeight(double weight, int unit) {
            JSObject o = new JSObject();
            o.put("deviceType", "weightscale");
            o.put("weight", weight);
            o.put("unit", unit);
            notifyListeners("measurement", o);
        }

        @Override
        public void onScaleTypeDetected(int type) {
            // Información de tipo de báscula; no se propaga como medida.
        }

        @Override
        public void onScaleMeasurementAllValues(LifevitSDKWeightScaleData data) {
            JSObject o = new JSObject();
            o.put("deviceType", "weightscale");
            o.put("weight", data.getWeight());
            o.put("imc", data.getImc());
            o.put("fatPercentage", data.getFatPercentage());
            notifyListeners("measurement", o);
        }
    };

    private void emitProgress(String deviceType, int progress) {
        JSObject o = new JSObject();
        o.put("deviceType", deviceType);
        o.put("progress", progress);
        notifyListeners("measurementProgress", o);
    }

    // =====================================================================
    //  INSPECTOR BLE genérico (independiente del SDK de Lifevit)
    //  Permite ver en crudo qué anuncia y qué notifica el dispositivo.
    //  Eventos: rawScanResult, rawConnection, rawServices, rawNotify
    // =====================================================================

    private BluetoothLeScanner bleScanner;
    private ScanCallback bleScanCallback;
    private BluetoothGatt rawGatt;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Queue<BluetoothGattCharacteristic> notifyQueue = new ArrayDeque<>();
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private static String toHex(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private static String toAscii(byte[] b) {
        if (b == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append((x >= 32 && x < 127) ? (char) x : '.');
        return sb.toString();
    }

    private BluetoothAdapter adapter() {
        BluetoothManager bm = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        return bm != null ? bm.getAdapter() : null;
    }

    @PluginMethod
    public void rawScan(PluginCall call) {
        BluetoothAdapter ad = adapter();
        if (ad == null) { call.reject("Bluetooth no disponible"); return; }
        bleScanner = ad.getBluetoothLeScanner();
        if (bleScanner == null) { call.reject("Bluetooth apagado"); return; }
        int timeout = call.getInt("timeout", 15000);

        bleScanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                try {
                    JSObject o = new JSObject();
                    o.put("address", result.getDevice().getAddress());
                    o.put("rssi", result.getRssi());
                    ScanRecord rec = result.getScanRecord();
                    String name = rec != null ? rec.getDeviceName() : null;
                    o.put("name", name != null ? name : "");
                    // Manufacturer specific data (clave de la hipótesis "pasivo")
                    JSArray manuf = new JSArray();
                    if (rec != null) {
                        SparseArray<byte[]> msd = rec.getManufacturerSpecificData();
                        for (int i = 0; i < msd.size(); i++) {
                            JSObject m = new JSObject();
                            m.put("companyId", msd.keyAt(i));
                            m.put("hex", toHex(msd.valueAt(i)));
                            manuf.put(m);
                        }
                        o.put("raw", toHex(rec.getBytes()));
                    }
                    o.put("manufacturerData", manuf);
                    notifyListeners("rawScanResult", o);
                } catch (Exception ignored) {}
            }
        };
        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
        bleScanner.startScan(null, settings, bleScanCallback);
        main.postDelayed(this::stopRawScanInternal, timeout);
        call.resolve();
    }

    private void stopRawScanInternal() {
        try {
            if (bleScanner != null && bleScanCallback != null) {
                bleScanner.stopScan(bleScanCallback);
            }
        } catch (Exception ignored) {}
        bleScanCallback = null;
    }

    @PluginMethod
    public void rawStopScan(PluginCall call) {
        stopRawScanInternal();
        call.resolve();
    }

    @PluginMethod
    public void rawConnect(PluginCall call) {
        BluetoothAdapter ad = adapter();
        if (ad == null) { call.reject("Bluetooth no disponible"); return; }
        String address = call.getString("address");
        if (address == null) { call.reject("address requerido"); return; }
        stopRawScanInternal();
        try {
            final android.bluetooth.BluetoothDevice dev = ad.getRemoteDevice(address);
            main.post(() -> {
                if (rawGatt != null) { try { rawGatt.close(); } catch (Exception ignored) {} }
                rawGatt = dev.connectGatt(getContext(), false, gattCallback);
            });
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage() != null ? e.getMessage() : "Error conectando GATT");
        }
    }

    @PluginMethod
    public void rawDisconnect(PluginCall call) {
        main.post(() -> {
            try { if (rawGatt != null) { rawGatt.disconnect(); rawGatt.close(); } } catch (Exception ignored) {}
            rawGatt = null;
        });
        call.resolve();
    }

    private void emitRawConnection(String state) {
        JSObject o = new JSObject();
        o.put("state", state);
        notifyListeners("rawConnection", o);
    }

    private final android.bluetooth.BluetoothGattCallback gattCallback = new android.bluetooth.BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                emitRawConnection("connected");
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                emitRawConnection("disconnected");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            JSArray services = new JSArray();
            notifyQueue.clear();
            for (BluetoothGattService s : gatt.getServices()) {
                JSObject so = new JSObject();
                so.put("uuid", s.getUuid().toString());
                JSArray chars = new JSArray();
                for (BluetoothGattCharacteristic c : s.getCharacteristics()) {
                    JSObject co = new JSObject();
                    co.put("uuid", c.getUuid().toString());
                    int p = c.getProperties();
                    co.put("notify", (p & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0);
                    co.put("indicate", (p & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0);
                    co.put("read", (p & BluetoothGattCharacteristic.PROPERTY_READ) != 0);
                    co.put("write", (p & BluetoothGattCharacteristic.PROPERTY_WRITE) != 0);
                    chars.put(co);
                    if ((p & (BluetoothGattCharacteristic.PROPERTY_NOTIFY | BluetoothGattCharacteristic.PROPERTY_INDICATE)) != 0) {
                        notifyQueue.add(c);
                    }
                }
                so.put("characteristics", chars);
                services.put(so);
            }
            JSObject o = new JSObject();
            o.put("services", services);
            notifyListeners("rawServices", o);
            processNotifyQueue(gatt);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            processNotifyQueue(gatt);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic c) {
            byte[] v = c.getValue();
            JSObject o = new JSObject();
            o.put("service", c.getService().getUuid().toString());
            o.put("characteristic", c.getUuid().toString());
            o.put("hex", toHex(v));
            o.put("ascii", toAscii(v));
            notifyListeners("rawNotify", o);
        }
    };

    private void processNotifyQueue(BluetoothGatt gatt) {
        BluetoothGattCharacteristic c = notifyQueue.poll();
        if (c == null) return;
        try {
            gatt.setCharacteristicNotification(c, true);
            BluetoothGattDescriptor d = c.getDescriptor(CCCD);
            if (d != null) {
                boolean indicate = (c.getProperties() & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0;
                d.setValue(indicate
                    ? BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                    : BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(d);
            } else {
                processNotifyQueue(gatt); // sin CCCD, pasa al siguiente
            }
        } catch (Exception e) {
            processNotifyQueue(gatt);
        }
    }
}
