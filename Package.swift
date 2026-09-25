// swift-tools-version:6.0
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = "https://github.com/happycodelucky/wake-kmp/releases/download/v1.0.0/WakeKit.xcframework.zip"
let remoteKotlinChecksum = "6c83341449002b43f9b86ef3d5cae3540a0b0cf05f575d4216ac04271e678823"
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