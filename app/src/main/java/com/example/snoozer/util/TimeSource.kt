package com.example.snoozer.util

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * The current local time, as a dependency so ViewModels can be tested with a fixed clock.
 */
interface TimeSource {
    fun now(): LocalDateTime

    /**
     * Emits the time right away, then at the start of every minute, so "in 2h 15m" labels
     * stay current. Collect it only while the screen is visible.
     */
    val minuteTicks: Flow<LocalDateTime>
}

class SystemTimeSource @Inject constructor() : TimeSource {
    override fun now(): LocalDateTime = LocalDateTime.now()

    override val minuteTicks: Flow<LocalDateTime> = flow {
        while (true) {
            val now = now()
            emit(now)
            // Sleep until the next whole minute. Re-reading the clock each time keeps it in step
            // after the device clock or time zone changes.
            val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
            delay(ChronoUnit.MILLIS.between(now(), nextMinute).coerceIn(1L, 60_000L))
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {
    @Binds
    abstract fun bindTimeSource(impl: SystemTimeSource): TimeSource
}
