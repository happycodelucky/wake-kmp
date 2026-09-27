/*
 * Wake — ARP lookup orchestration.
 *
 * [performLookup] is the platform-agnostic core behind the per-platform
 * `lookupMac` entry points (macosMain / jvmMain): validate → read → map. It holds
 * no platform code; the only platform-specific dependency is the injected
 * [ArpResolver]. Each platform's `lookupMac` calls it with the real resolver, and
 * commonTest calls it with a recording fake — so the validate/map logic is
 * exercised identically everywhere, with no real OS read.
 */
package com.happycodelucky.wake.internal

import com.happycodelucky.wake.MacLookupException

/**
 * Validate [ip], read its hardware address via [resolver], and map the outcome
 * onto a `Result` whose failure is a [MacLookupException].
 *
 * The IPv4 string is validated here (via [parseIpv4]) before the resolver runs,
 * so an unparseable address yields a consistent
 * [MacLookupException.InvalidIpAddress] on every
 * platform and the resolver only ever sees a syntactically valid dotted-quad.
 * This never throws — bad input, a missing entry, and read failures come back
 * as a failed `Result`.
 *
 * @param resolver the ARP-read seam (platform implementation in production, a
 *   fake in tests).
 * @param ip the target device's IPv4 address, in dotted-quad form.
 * @return the canonical `AA:BB:CC:DD:EE:FF` MAC, or a failure holding a
 *   [MacLookupException].
 */
internal suspend fun performLookup(
    resolver: ArpResolver,
    ip: String,
): Result<String> {
    parseIpv4(ip)
        ?: return Result.failure(MacLookupException.InvalidIpAddress(ip))

    return when (val outcome = resolver.resolve(ip)) {
        is ArpLookupOutcome.Resolved -> Result.success(formatMac(outcome.mac))
        is ArpLookupOutcome.NotFound -> Result.failure(MacLookupException.NotInCache(ip))
        is ArpLookupOutcome.Failed -> Result.failure(MacLookupException.LookupFailed(outcome.message))
    }
}
