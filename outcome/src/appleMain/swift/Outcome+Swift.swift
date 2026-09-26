//
// Outcome — the Swift half, written once for every library that exports `:outcome`.
//
// SKIE's Swift bundling compiles this file into each framework that `export`s the
// `:outcome` module, so any Kotlin API returning `Outcome<T>` gets these for free:
//
//     let mac: String = try await lookupMac(ip: "192.168.1.42").get()
//     try await Wake.up(mac: mac).get()                       // Outcome<Unit>
//     let r = await ….result(as: String.self)                 // Swift.Result
//
// Failures are the Kotlin exception objects themselves (Kotlin `Throwable` is made
// a Swift `Error` below), so callers `catch let e as SomeKotlinException` and switch
// exhaustively with SKIE's `onEnum(of:)` — no per-error Swift enums, no NSError.
//
// Why a protocol instead of `extension Outcome { … }`: Kotlin generic classes are
// exported as ObjC lightweight-generic classes, and Swift refuses any
// (non-`@objc`) member in an extension of one ("cannot access the class's generic
// parameters at runtime"), even members that never mention `T`. A *non-generic*
// protocol that `Outcome` conforms to sidesteps that: its members don't mention
// `T` (`__anyValue` is Kotlin's `Any?`), and generic helpers live in the
// protocol extension. The price is that the Swift caller names the value type
// (`let x: String = try o.get()` or `get(as: String.self)`): the class's own `T`
// is the ObjC-bridged type (`NSString`, `KotlinInt`, …), and `as? V` bridges it to
// the Swift type. A wrong `V` is caught at runtime as `OutcomeTypeMismatchError`.
//

import Foundation

// Kotlin exceptions become Swift errors, thrown and caught as themselves.
extension KotlinThrowable: Error {}

extension KotlinThrowable: LocalizedError {
    public var errorDescription: String? { message }
}

/// The type-erased view of a Kotlin `Outcome<T>` that the Swift helpers build on.
///
/// Every exported `Outcome` conforms; you never need to conform a type yourself.
public protocol AnyOutcome {
    /// `true` if the outcome is a success.
    var isSuccess: Bool { get }
    /// The success value, type-erased (Kotlin `anyValue`).
    var __anyValue: Any? { get }
    /// The failure's exception, or `nil` on success.
    func exceptionOrNull() -> KotlinThrowable?
}

extension Outcome: AnyOutcome {}

/// `get(as:)` found a success value that is not of the requested type.
public struct OutcomeTypeMismatchError: Error, CustomStringConvertible {
    /// The type the caller asked for.
    public let expected: Any.Type
    /// The value the outcome actually held.
    public let actual: Any?

    public var description: String {
        "Outcome value \(String(describing: actual)) is not a \(expected)"
    }
}

extension AnyOutcome {
    /// The success value bridged to `V` (`String`, `Int`, `[String]`, `String?`, …),
    /// or the failure's Kotlin exception thrown as a Swift `Error`.
    ///
    /// ```swift
    /// let name: String = try outcome.get()
    /// let count = try outcome.get(as: Int.self)
    /// ```
    ///
    /// - Throws: The failure's Kotlin exception, or `OutcomeTypeMismatchError` if
    ///   the success value is not a `V`.
    public func get<V>(as type: V.Type = V.self) throws -> V {
        if let exception = exceptionOrNull() { throw exception }
        guard let value = __anyValue as? V else {
            throw OutcomeTypeMismatchError(expected: V.self, actual: __anyValue)
        }
        return value
    }

    /// Returns normally on success, or throws the failure's Kotlin exception —
    /// for `Outcome<Unit>`, or when the value is not needed.
    public func get() throws {
        if let exception = exceptionOrNull() { throw exception }
    }

    /// This outcome as a `Swift.Result`, with the value bridged to `V`.
    public func result<V>(as type: V.Type = V.self) -> Swift.Result<V, any Error> {
        Swift.Result { try get(as: V.self) }
    }
}
