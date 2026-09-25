// swift-tools-version:6.0
import PackageDescription

// BEGIN KMMBRIDGE VARIABLES BLOCK (do not edit)
let remoteKotlinUrl = ""
let remoteKotlinChecksum = ""
let packageName = "WakeKit"
// END KMMBRIDGE BLOCK

// This manifest is a placeholder, and stays one on main. KMMBridge rewrites the
// variables block above (and the binary target below):
//   - `mise run spm:dev` writes the LOCAL-DEV form (a `.binaryTarget(path:)`
//     pointing at wake/build/XCFrameworks/debug/WakeKit.xcframework) — never
//     commit that form.
//   - the release workflow writes the RELEASED form (a remote
//     `.binaryTarget(url:checksum:)` against the GitHub Release asset) into a
//     release commit that lives only on its vX.Y.Z tag — SPM consumers pin a
//     tag and get that form (.github/PUBLISHING.md).
// Do not hand-edit beyond this initial stub (CLAUDE.md §9).
let package = Package(
    name: packageName,
    platforms: [
        .iOS(.v18),
        .macOS(.v15),
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
        ),
    ]
)
