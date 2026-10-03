package com.arm.aichat.internal

import kotlinx.coroutines.suspendCancellableCoroutine

/** Connect coroutine timeouts to a native stop flag while JNI blocks its dispatcher. */
internal suspend fun <T> nativeOperation(stop: () -> Unit, action: () -> T): T =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { stop() }
        if (!continuation.isActive) return@suspendCancellableCoroutine
        try { continuation.resumeWith(Result.success(action())) }
        catch (error: Exception) { continuation.resumeWith(Result.failure(error)) }
    }
