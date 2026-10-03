package com.arm.aichat.internal

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NativeOperationTest {
    @Test fun cancellationSignalsBlockedNativeWork() = runBlocking {
        val entered=CountDownLatch(1)
        val stopped=CountDownLatch(1)
        val operation=async(Dispatchers.IO) {
            nativeOperation({stopped.countDown()}) {
                entered.countDown()
                check(stopped.await(2,TimeUnit.SECONDS)) { "JNI work did not receive cancellation" }
                42
            }
        }
        assertTrue(entered.await(2,TimeUnit.SECONDS))
        operation.cancel()
        try {operation.await();fail("Cancellation was swallowed")}
        catch(expected: CancellationException) { }
        assertEquals(0L,stopped.count)
    }
    @Test fun normalWorkDoesNotSignalStop() = runBlocking {
        var stopped=false
        assertEquals(42,nativeOperation({stopped=true}) {42})
        assertFalse(stopped)
    }
    @Test fun nativeExceptionPropagates() = runBlocking {
        try {nativeOperation({}) {throw IllegalStateException("native failure")};fail("Error swallowed")}
        catch(expected: IllegalStateException) {assertEquals("native failure",expected.message)}
    }
}
