package org.walletconnect

//Map functions that throw exceptions into optional types
fun <T> nullOnThrow(func: () -> T): T? = try {
    func.invoke()
} catch (e: Exception) {
    null
}
