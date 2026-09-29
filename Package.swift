// swift-tools-version:6.0
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://github.com/happycodelucky/wake-kmp/releases/download/v1.2.0/WakeKit.xcframework.zip"
let remoteKotlinChecksum = "26ed810e02bc9af52df22e3c8b152c47c9e325155a1ec8fdbbdf2a98001e7aeb"
let packageName = "WakeKit"
// END KMMBRIDGE BLOCK

let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v18),
.macOS(.v15)
    ],
    products: [
        .library(
            name: packageName,
            targets: [packageName]
        ),
    ],
    targets: [
        .binaryTarget(
            name: packageName,
            url: remoteKotlinUrl,
            checksum: remoteKotlinChecksum
        )
        ,
    ]
)