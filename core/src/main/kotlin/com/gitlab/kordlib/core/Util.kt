package com.gitlab.kordlib.core


import com.gitlab.kordlib.core.`object`.data.ChannelData
import com.gitlab.kordlib.core.entity.Entity
import com.gitlab.kordlib.core.entity.Snowflake
import com.gitlab.kordlib.core.entity.channel.Channel
import com.gitlab.kordlib.rest.request.RequestException
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.format.DateTimeFormatter

internal fun String?.toSnowflakeOrNull(): Snowflake? = when {
    this == null -> null
    else -> Snowflake(this)
}

internal fun String.toInstant() = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(this, Instant::from)
internal fun Int.toInstant() = Instant.ofEpochMilli(toLong())

internal inline fun <T> catchNotFound(block: () -> T): T? = try {
    block()
} catch (exception: RequestException) {
    if (exception.response.status.value == 404) null
    else throw exception
}

fun <T : Entity> Flow<T>.sorted(): Flow<T> = flow {
    for (entity in toList().sorted()) {
        emit(entity)
    }
}

internal fun <T> Flow<T>.switchIfEmpty(flow: Flow<T>) : Flow<T> = flow {
    var empty = true
    collect {
        empty = false
        emit(it)
    }

    if (empty) {
        flow.collect {
            emit(it)
        }
    }
}

internal suspend inline fun <T> Flow<T>.indexOfFirstOrNull(crossinline predicate: (T) -> Boolean): Int? {
    val counter = atomic(0)
    return map { counter.getAndIncrement() to it }
            .filter { predicate(it.second) }
            .take(1)
            .singleOrNull()?.first
}
