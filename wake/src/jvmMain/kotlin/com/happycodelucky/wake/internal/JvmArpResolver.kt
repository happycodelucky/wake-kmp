/*
 * Wake — JVM-desktop ARP resolver (java.net + /proc + arp).
 *
 * Lives in `jvmMain` (NOT `jvmShared`): the JVM desktop can read the ARP cache,
 * but Android cannot (`/proc/net/arp` is SELinux-blocked since API 29), so the
 * Android target deliberately has no ARP code and no `lookupMac` at all.
 *
 * Two tiers, host-OS dependent:
 *   1. Linux: read and parse `/proc/net/arp` directly (no subprocess).
 *   2. macOS / Windows / BSD (no `/proc`): shell out to `arp` and parse stdout.
 *
 * The blocking read runs on `Dispatchers.IO`. All failure folds into
 * `ArpLookupOutcome.Failed` so the public `lookupMac` never throws. The pure text
 * parsers ([parseProcNetArp], [parseArpCommandOutput]) are split out from the I/O
 * so they can be unit-tested hermetically against sample output (jvmTest), with
 * no dependence on the host machine's live ARP cache.
 */
package com.happycodelucky.wake.internal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private const val PROC_NET_ARP_PATH = "/proc/net/arp"
private const val ARP_PROCESS_TIMEOUT_SECONDS = 2L

// /proc/net/arp columns: "IP address  HW type  Flags  HW address  Mask  Device".
private const val PROC_ARP_COLUMN_IP = 0
private const val PROC_ARP_COLUMN_FLAGS = 2
private const val PROC_ARP_COLUMN_MAC = 3
private const val PROC_ARP_MIN_COLUMNS = 4

// A /proc/net/arp flags value of 0x0 means the entry is incomplete (no MAC yet).
private const val PROC_ARP_FLAG_INCOMPLETE = "0x0"

internal class JvmArpResolver : ArpResolver {
    override suspend fun resolve(ipv4: String): ArpLookupOutcome =
        withContext(Dispatchers.IO) {
            try {
                val procFile = File(PROC_NET_ARP_PATH)
                if (procFile.canRead()) {
                    parseProcNetArp(procFile.readText(), ipv4)
                } else {
                    parseArpCommandOutput(runArpCommand(ipv4), ipv4)
                }
            } catch (
                // Any read/parse failure folds into Failed so lookupMac never throws.
                @Suppress("TooGenericExceptionCaught")
                e: Exception,
            ) {
                ArpLookupOutcome.Failed(e.message ?: e::class.simpleName ?: "ARP read failed")
            }
        }

    /**
     * Run the platform `arp` command for [ipv4] and return its combined stdout.
     *
     * Drains the child's output fully before [Process.waitFor] to avoid a
     * full-pipe deadlock, bounds the wait, and force-destroys a hung child.
     */
    private fun runArpCommand(ipv4: String): String {
        // `-n` asks arp for numeric output (no reverse DNS); accepted on both
        // macOS and Linux. The IP narrows the table to the entry of interest.
        val process =
            ProcessBuilder("arp", "-n", ipv4)
                .redirectErrorStream(true)
                .start()
        return try {
            // Read to EOF BEFORE waitFor — a child that fills the pipe would
            // otherwise block forever.
            val output = process.inputStream.bufferedReader().use { it.readText() }
            if (!process.waitFor(ARP_PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            output
        } finally {
            if (process.isAlive) {
                process.destroyForcibly()
            }
        }
    }
}

/**
 * Parse the text of `/proc/net/arp` for the row matching [ip].
 *
 * Returns [ArpLookupOutcome.Resolved] when a complete entry is found,
 * [ArpLookupOutcome.NotFound] when the IP is absent or its entry is incomplete
 * (flags `0x0`). Pure — no I/O — so it is unit-tested against sample table text.
 */
@Suppress("ReturnCount") // Guard-clause early-returns are clearer than nesting here.
internal fun parseProcNetArp(
    content: String,
    ip: String,
): ArpLookupOutcome {
    content
        .lineSequence()
        .drop(1) // header row
        .forEach { line ->
            val columns = line.trim().split(Regex("\\s+"))
            if (columns.size >= PROC_ARP_MIN_COLUMNS && columns[PROC_ARP_COLUMN_IP] == ip) {
                if (columns[PROC_ARP_COLUMN_FLAGS] == PROC_ARP_FLAG_INCOMPLETE) {
                    return ArpLookupOutcome.NotFound
                }
                return macTokenToOutcome(columns[PROC_ARP_COLUMN_MAC])
            }
        }
    return ArpLookupOutcome.NotFound
}

/**
 * Parse the stdout of the `arp` command for the MAC associated with [ip].
 *
 * Handles both the macOS/BSD form (`? (192.168.1.5) at aa:bb:cc:dd:ee:ff on en0
 * …`, including short non-zero-padded octets and `no entry`) and the Linux form
 * (`192.168.1.5 ether aa:bb:cc:dd:ee:ff C eth0`, including `(incomplete)`). Pure —
 * unit-tested against sample command output.
 */
@Suppress("ReturnCount") // Guard-clause early-returns are clearer than nesting here.
internal fun parseArpCommandOutput(
    output: String,
    ip: String,
): ArpLookupOutcome {
    output
        .lineSequence()
        .forEach { line ->
            if (!line.contains(ip)) return@forEach
            if (line.contains("no entry", ignoreCase = true) ||
                line.contains("incomplete", ignoreCase = true)
            ) {
                return ArpLookupOutcome.NotFound
            }
            val mac = firstMacToken(line) ?: return@forEach
            return macTokenToOutcome(mac)
        }
    return ArpLookupOutcome.NotFound
}

/**
 * Find the first colon-separated hardware-address token in [line] (e.g.
 * `aa:bb:cc:dd:ee:ff` or the short `a:b:c:d:e:f`). Returns `null` if none.
 */
private fun firstMacToken(line: String): String? =
    line
        .split(Regex("\\s+"))
        .firstOrNull { token -> token.count { it == ':' } == MAC_LENGTH - 1 }

/**
 * Normalize a colon-separated MAC token (zero-padding short octets that BSD `arp`
 * may emit) and parse it to bytes. Returns [ArpLookupOutcome.Resolved] on success
 * or [ArpLookupOutcome.NotFound] if the token is not a valid MAC.
 */
private fun macTokenToOutcome(token: String): ArpLookupOutcome {
    val normalized =
        token
            .split(":")
            .joinToString(":") { octet -> octet.padStart(2, '0') }
    val bytes = parseMacBytes(normalized) ?: return ArpLookupOutcome.NotFound
    return ArpLookupOutcome.Resolved(bytes)
}
