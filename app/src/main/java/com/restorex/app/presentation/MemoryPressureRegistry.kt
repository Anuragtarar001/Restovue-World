package com.restorex.app.presentation

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class MemoryPressureRegistry {
    private val events = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val pressure = events.asSharedFlow()

    fun notify(level: Int) { events.tryEmit(level) }
}
