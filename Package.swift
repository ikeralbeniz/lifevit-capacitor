// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "LifevitCapacitor",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "LifevitCapacitor",
            targets: ["LifevitPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        // SDK nativo del fabricante (xcframework; slice de dispositivo arm64).
        .binaryTarget(
            name: "LifevitSDK",
            path: "ios/vendor/lifevit-ios-sdk/LifevitSDK.xcframework"),
        .target(
            name: "LifevitPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm"),
                "LifevitSDK"
            ],
            path: "ios/Sources/LifevitPlugin",
            linkerSettings: [
                .linkedFramework("CoreBluetooth"),
                .linkedFramework("Security"),
                .linkedFramework("SystemConfiguration"),
                .unsafeFlags(["-ObjC"])
            ]),
        .testTarget(
            name: "LifevitPluginTests",
            dependencies: ["LifevitPlugin"],
            path: "ios/Tests/LifevitPluginTests")
    ]
)
