/*
 * :apps:cli — entry point.
 *
 * Thin shell over the pure [parseArgs]: parse → dispatch → exit code. The suspend
 * `Wake.up` / `lookupMac` calls are bridged with `runBlocking` (a CLI's `main` is
 * the one place that's appropriate). All user-facing text goes to stdout on
 * success and stderr on error; the process exit code encodes the outcome.
 */
package com.happycodelucky.wake.cli

import com.happycodelucky.wake.MacLookupResult
import com.happycodelucky.wake.Wake
import com.happycodelucky.wake.WakeResult
import com.happycodelucky.wake.lookupMac
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

// Exit codes (also documented in USAGE).
private const val EXIT_OK = 0
private const val EXIT_ERROR = 1
private const val EXIT_BAD_USAGE = 2
private const val EXIT_NOT_IN_CACHE = 3

fun main(args: Array<String>) {
    val code =
        when (val command = parseArgs(args)) {
            is CliCommand.ShowHelp -> {
                println(USAGE)
                EXIT_OK
            }

            is CliCommand.BadUsage -> {
                System.err.println("error: ${command.message}")
                System.err.println()
                System.err.println(USAGE)
                EXIT_BAD_USAGE
            }

            is CliCommand.WakeByMac ->
                runBlocking { wakeByMacs(command.macs, command.broadcast, command.port) }

            is CliCommand.WakeByIp ->
                runBlocking { wakeByIp(command.ip, command.broadcast, command.port) }
        }
    exitProcess(code)
}

/**
 * Send a magic packet to every MAC in [macs]; return the process exit code.
 *
 * Each MAC is attempted independently and its outcome reported, so one bad MAC
 * doesn't suppress the others. The exit code is [EXIT_OK] only if *every* send
 * succeeded; otherwise [EXIT_ERROR].
 */
private suspend fun wakeByMacs(
    macs: List<String>,
    broadcast: String,
    port: Int,
): Int {
    var allOk = true
    for (mac in macs) {
        if (wakeByMac(mac, broadcast, port) != EXIT_OK) {
            allOk = false
        }
    }
    return if (allOk) EXIT_OK else EXIT_ERROR
}

/** Send a magic packet to a single [mac]; return its exit code. */
private suspend fun wakeByMac(
    mac: String,
    broadcast: String,
    port: Int,
): Int =
    when (val result = Wake.up(mac, broadcast, port)) {
        is WakeResult.Success -> {
            println("magic packet sent to $mac (broadcast $broadcast:$port)")
            EXIT_OK
        }

        is WakeResult.InvalidMacAddress -> {
            System.err.println("error: invalid MAC address \"$mac\" — ${result.reason}")
            EXIT_ERROR
        }

        is WakeResult.NetworkError -> {
            System.err.println("error: send to $mac failed — ${result.message}")
            EXIT_ERROR
        }
    }

/** Resolve [ip] to a MAC via the ARP cache, then wake it; return the exit code. */
private suspend fun wakeByIp(
    ip: String,
    broadcast: String,
    port: Int,
): Int =
    when (val lookup = lookupMac(ip)) {
        is MacLookupResult.Found -> {
            println("resolved $ip -> ${lookup.macAddress}")
            wakeByMac(lookup.macAddress, broadcast, port)
        }

        is MacLookupResult.NotInCache -> {
            System.err.println("error: no ARP entry for $ip — try contacting it first (e.g. ping $ip)")
            EXIT_NOT_IN_CACHE
        }

        is MacLookupResult.Error -> {
            System.err.println("error: lookup failed — ${lookup.message}")
            EXIT_ERROR
        }
    }
