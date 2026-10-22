import Foundation
import Capacitor
import LifevitSDK

/**
 * Plugin iOS del SDK de Lifevit. Espejo del plugin Android: expone
 * scan/connect/medida y reenvía los delegados del SDK a los mismos eventos JS
 * (connectionChanged, connectionError, measurement, measurementProgress, scanResult).
 *
 * Diferencias iOS respecto a Android (absorbidas por el JS):
 *  - El tensiómetro es DEVICE_HEART (0).
 *  - No hay MAC: se usa el UUID del periférico (connectByUUID / getDeviceUUID).
 *  - HeartData no trae IHB (arritmia) en iOS.
 */
@objc(LifevitPlugin)
public class LifevitPlugin: CAPPlugin, CAPBridgedPlugin,
    LifevitDeviceDelegate, LifevitHeartDelegate, LifevitOximeterDelegate,
    LifevitThermometerDelegate, LifevitWeightScaleDelegate, LifevitSDKDevicesScanDelegate {

    public let identifier = "LifevitPlugin"
    public let jsName = "Lifevit"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "scan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "connect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "disconnect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isConnected", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDeviceAddress", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startMeasurement", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "sendThermometerCommand", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rawScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rawStopScan", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rawConnect", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "rawDisconnect", returnType: CAPPluginReturnPromise)
    ]

    // Tipos de dispositivo (valores de LifevitSDKConstants en iOS).
    private let DEVICE_HEART: Int32 = 0
    private let DEVICE_OXIMETER: Int32 = 2
    private let DEVICE_THERMOMETER: Int32 = 3
    private let DEVICE_WEIGHT_SCALE: Int32 = 4

    private func typeToInt(_ type: String?) -> Int32 {
        switch type {
        case "tensiometer": return DEVICE_HEART
        case "oximeter": return DEVICE_OXIMETER
        case "thermometer": return DEVICE_THERMOMETER
        case "weightscale": return DEVICE_WEIGHT_SCALE
        default: return DEVICE_HEART
        }
    }

    private func intToType(_ t: Int32) -> String {
        switch t {
        case DEVICE_HEART: return "tensiometer"
        case DEVICE_OXIMETER: return "oximeter"
        case DEVICE_THERMOMETER: return "thermometer"
        case DEVICE_WEIGHT_SCALE: return "weightscale"
        default: return "unknown"
        }
    }

    private func statusToString(_ s: Int32) -> String {
        switch s {
        case 0: return "scanning"
        case 1: return "connecting"
        case 2: return "connected"
        default: return "disconnected"
        }
    }

    private func errorMessage(_ code: Int32) -> String {
        switch code {
        case -2: return "Señal baja"
        case -3: return "Ruido / movimiento durante la medida"
        case -4: return "Problema al inflar el brazalete"
        case -5: return "Resultado anómalo"
        case -6: return "Reintenta la medida"
        case -7: return "Batería baja del dispositivo"
        case -8: return "Sin resultados"
        case -9: return "Demasiada interferencia"
        default: return code == 0 ? "" : "Error (\(code))"
        }
    }

    private var manager: LifevitSDKManager { LifevitSDKManager.sharedInstance() }

    public override func load() {
        let m = manager
        m.deviceDelegate = self
        m.heartDelegate = self
        m.oximeterDelegate = self
        m.thermometerDelegate = self
        m.weightscaleDelegate = self
        m.devicesScanDelegate = self
        // Imprescindible en iOS: crea el CBCentralManager (dispara el permiso de
        // Bluetooth y habilita el escaneo). Sin esto, scan/connect no hacen nada.
        m.initCentralManagerWithoutBackgroundMode()
    }

    // MARK: - Permisos (iOS pide el permiso de Bluetooth automáticamente al usarlo)
    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        call.resolve(["granted": true])
    }

    // MARK: - Scan
    @objc func scan(_ call: CAPPluginCall) {
        manager.scanAllDevices()
        call.resolve()
    }

    @objc func stopScan(_ call: CAPPluginCall) {
        call.resolve()
    }

    // MARK: - Conexión
    @objc func connect(_ call: CAPPluginCall) {
        guard let type = call.getString("deviceType") else { call.reject("deviceType requerido"); return }
        let timeout = Int32(call.getInt("timeout") ?? 15000) / 1000 // iOS usa segundos
        let typeInt = typeToInt(type)
        if let address = call.getString("address"), !address.isEmpty {
            manager.connect(byUUID: address, withType: typeInt)
        } else {
            manager.connectDevice(typeInt, withTimeout: timeout)
        }
        call.resolve()
    }

    @objc func disconnect(_ call: CAPPluginCall) {
        guard let type = call.getString("deviceType") else { call.reject("deviceType requerido"); return }
        manager.disconnectDevice(typeToInt(type))
        call.resolve()
    }

    @objc func isConnected(_ call: CAPPluginCall) {
        guard let type = call.getString("deviceType") else { call.reject("deviceType requerido"); return }
        call.resolve(["connected": manager.isDeviceConnected(typeToInt(type))])
    }

    @objc func getDeviceAddress(_ call: CAPPluginCall) {
        guard let type = call.getString("deviceType") else { call.reject("deviceType requerido"); return }
        let uuid = manager.getDeviceUUID(typeToInt(type)) ?? ""
        call.resolve(["address": uuid])
    }

    // MARK: - Medida
    @objc func startMeasurement(_ call: CAPPluginCall) {
        _ = manager.startMeasurement()
        call.resolve()
    }

    @objc func sendThermometerCommand(_ call: CAPPluginCall) {
        // No expuesto en el SDK iOS de la misma forma; no-op.
        call.resolve()
    }

    // MARK: - Inspector BLE en crudo (solo Android por ahora)
    @objc func rawScan(_ call: CAPPluginCall) { call.resolve() }
    @objc func rawStopScan(_ call: CAPPluginCall) { call.resolve() }
    @objc func rawConnect(_ call: CAPPluginCall) { call.resolve() }
    @objc func rawDisconnect(_ call: CAPPluginCall) { call.resolve() }

    // MARK: - LifevitDeviceDelegate
    public func device(_ device: Int32, onConnectionChanged status: Int32) {
        notifyListeners("connectionChanged", data: [
            "deviceType": intToType(device),
            "status": statusToString(status)
        ])
    }

    public func device(_ device: Int32, onConnectionError error: Int32) {
        notifyListeners("connectionError", data: [
            "deviceType": intToType(device),
            "errorCode": error,
            "message": errorMessage(error)
        ])
    }

    public func device(_ device: Int32, onSendInformation data: Any) {}

    // MARK: - LifevitHeartDelegate (tensiómetro)
    public func heartDevice(onProgressMeasurement pulse: Int32) {
        notifyListeners("measurementProgress", data: ["deviceType": "tensiometer", "progress": pulse])
    }

    public func heartDevice(onResult data: LifevitSDKHeartData) {
        let err = data.errorCode?.int32Value ?? 0
        notifyListeners("measurement", data: [
            "deviceType": "tensiometer",
            "systolic": data.systolic?.doubleValue ?? 0,
            "diastolic": data.diastolic?.doubleValue ?? 0,
            "pulse": data.pulse?.doubleValue ?? 0,
            "errorCode": err,
            "errorMessage": errorMessage(err),
            "date": (data.date?.timeIntervalSince1970 ?? 0) * 1000
        ])
    }

    // MARK: - LifevitOximeterDelegate
    public func oximeterDevice(onProgressMeasurement data: LifevitSDKOximeterData) {
        notifyListeners("measurementProgress", data: [
            "deviceType": "oximeter",
            "progress": data.pleth?.intValue ?? 0
        ])
    }

    public func oximeterDevice(onResult data: LifevitSDKOximeterData) {
        notifyListeners("measurement", data: [
            "deviceType": "oximeter",
            "spo2": data.sp2?.intValue ?? 0,
            "pi": data.pi?.intValue ?? 0,
            "rpm": data.rpm?.intValue ?? 0,
            "lpm": data.lpm?.intValue ?? 0,
            "date": (data.date?.timeIntervalSince1970 ?? 0) * 1000
        ])
    }

    // MARK: - LifevitThermometerDelegate
    public func thermometer(onResult data: LifevitSDKThermometerData) {
        notifyListeners("measurement", data: [
            "deviceType": "thermometer",
            "mode": data.mode?.intValue ?? 0,
            "value": data.temperature?.doubleValue ?? 0,
            "unit": data.unit ?? ""
        ])
    }

    public func thermometer(onSuccessCommand data: LifevitSDKThermometerSuccessData) {}

    public func thermometer(onError errorCode: Int32) {
        notifyListeners("connectionError", data: [
            "deviceType": "thermometer",
            "errorCode": errorCode,
            "message": errorMessage(errorCode)
        ])
    }

    // MARK: - LifevitWeightScaleDelegate
    public func weightscale(onMeasurementResult data: LifevitSDKWeightScaleData) {}

    public func weightscale(onResult data: LifevitSDKWeightScaleData) {
        notifyListeners("measurement", data: [
            "deviceType": "weightscale",
            "weight": data.weight?.doubleValue ?? 0,
            "imc": data.imc?.doubleValue ?? 0,
            "fatPercentage": data.fatPercentage?.doubleValue ?? 0
        ])
    }

    // MARK: - LifevitSDKDevicesScanDelegate
    public func allDevicesDetected(_ devicesList: NSMutableDictionary) {
        for (_, value) in devicesList {
            guard let arr = value as? [LifevitSDKDevice] else { continue }
            for d in arr {
                notifyListeners("scanResult", data: [
                    "deviceType": intToType(d.type.int32Value),
                    "address": d.deviceIdentifier ?? "",
                    "rssi": d.rssi.intValue
                ])
            }
        }
    }
}
