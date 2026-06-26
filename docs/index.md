# Wake

Kotlin Multiplatform **Wake-on-LAN / Wake-on-Wireless**: send a magic packet
over UDP broadcast to wake a device on the network by its MAC address.

This documentation site is a stub for the initial scaffold. For now, see the
project [README](https://github.com/happycodelucky/wake#readme) for usage, and
the generated [API reference](api/index.html) (built by Dokka via
`mise run docs:dokka`).

## Quick start

=== "Kotlin"

    ```kotlin
    when (val result = Wake.up("AA:BB:CC:DD:EE:FF")) {
        is WakeResult.Success -> println("magic packet sent")
        is WakeResult.InvalidMacAddress -> println("bad MAC: ${result.reason}")
        is WakeResult.NetworkError -> println("send failed: ${result.message}")
    }
    ```

=== "Swift"

    ```swift
    import WakeKit

    switch try await Wake.up(mac: "AA:BB:CC:DD:EE:FF") {
    case .success: print("magic packet sent")
    case let .invalidMacAddress(reason): print("bad MAC: \(reason)")
    case let .networkError(message): print("send failed: \(message)")
    }
    ```
