/*
 * :apps:cli — argument parsing.
 *
 * Hand-rolled, dependency-free, and pure: [parseArgs] turns the raw `args` array
 * into a [CliCommand] with no I/O, so it is fully unit-testable. It validates
 * only the *structure* of the invocation (which mode, which flags, well-formed
 * port) — it deliberately does NOT validate MAC or IP syntax, leaving that to
 * `Wake.up` / `lookupMac`, which report it as a typed result. Defaults come from
 * the `:wake` public constants so there is a single source of truth.
 */
package com.happycodelucky.wake.cli

import com.happycodelucky.wake.DEFAULT_BROADCAST_ADDRESS
import com.happycodelucky.wake.DEFAULT_WAKE_PORT

private const val MIN_PORT = 0
private const val MAX_PORT = 65535

/** The parsed intent of a CLI invocation. */
internal sealed interface CliCommand {
    /**
     * Wake one or more devices by MAC address.
     *
     * [macs] holds every positional MAC given, in order. Multiple MACs are sent a
     * packet each — useful for a device that listens on more than one interface
     * (e.g. a Roku in deep sleep, whose Wi-Fi and Ethernet NICs have different
     * MACs), where you wake every interface to be sure one lands.
     */
    data class WakeByMac(
        val macs: List<String>,
        val broadcast: String,
        val port: Int,
    ) : CliCommand

    /** Resolve [ip] to a MAC via the ARP cache, then wake it. */
    data class WakeByIp(
        val ip: String,
        val broadcast: String,
        val port: Int,
    ) : CliCommand

    /** Print usage and exit successfully. */
    data object ShowHelp : CliCommand

    /** The invocation was malformed; [message] explains why. */
    data class BadUsage(
        val message: String,
    ) : CliCommand
}

/** Usage text, printed for `--help` and on a [CliCommand.BadUsage]. */
internal val USAGE: String =
    """
    wake — send a Wake-on-LAN magic packet

    Usage:
      wake <MAC>...             wake one or more devices by MAC (AA:BB:CC:DD:EE:FF, aa-bb-.., or bare hex)
      wake --ip <IP>            resolve <IP> in the ARP cache, then wake that MAC

    Passing several MACs sends a packet to each — handy for a device that listens
    on more than one interface (e.g. a Roku's separate Wi-Fi and Ethernet MACs):
      wake <wifi-mac> <ethernet-mac>

    Options:
      --broadcast <addr>        broadcast address (default $DEFAULT_BROADCAST_ADDRESS)
      --port <n>                UDP port (default $DEFAULT_WAKE_PORT)
      --help                    show this help

    Exit codes:
      0  all packets sent   1  a wake/lookup failed   2  bad usage   3  IP not in ARP cache
    """.trimIndent()

/**
 * Parse [args] into a [CliCommand].
 *
 * Recognizes a single positional MAC, `--ip <IP>`, `--broadcast <addr>`,
 * `--port <n>`, and `--help`. Returns [CliCommand.BadUsage] for structural
 * errors: an unknown flag, a flag missing its value, a non-numeric or
 * out-of-range port, both a MAC and `--ip`, or neither.
 */
@Suppress("ReturnCount") // Guard-clause early-returns are clearer than nesting here.
internal fun parseArgs(args: Array<String>): CliCommand {
    val macs = mutableListOf<String>()
    var ip: String? = null
    var broadcast = DEFAULT_BROADCAST_ADDRESS
    var port = DEFAULT_WAKE_PORT

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--help", "-h" -> return CliCommand.ShowHelp

            "--ip" -> {
                val value = args.getOrNull(i + 1) ?: return CliCommand.BadUsage("--ip requires an address")
                ip = value
                i += 2
            }

            "--broadcast" -> {
                val value = args.getOrNull(i + 1) ?: return CliCommand.BadUsage("--broadcast requires an address")
                broadcast = value
                i += 2
            }

            "--port" -> {
                val value = args.getOrNull(i + 1) ?: return CliCommand.BadUsage("--port requires a number")
                port =
                    value.toIntOrNull()?.takeIf { it in MIN_PORT..MAX_PORT }
                        ?: return CliCommand.BadUsage("--port must be a number in $MIN_PORT..$MAX_PORT, was \"$value\"")
                i += 2
            }

            else -> {
                if (arg.startsWith("-")) return CliCommand.BadUsage("unknown option: $arg")
                macs += arg
                i += 1
            }
        }
    }

    return when {
        macs.isNotEmpty() && ip != null -> CliCommand.BadUsage("give MAC(s) or --ip, not both")
        macs.isNotEmpty() -> CliCommand.WakeByMac(macs.toList(), broadcast, port)
        ip != null -> CliCommand.WakeByIp(ip, broadcast, port)
        else -> CliCommand.BadUsage("give one or more MAC addresses, or --ip <IP>")
    }
}
