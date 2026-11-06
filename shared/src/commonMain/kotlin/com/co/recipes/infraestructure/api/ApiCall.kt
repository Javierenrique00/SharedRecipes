package com.co.recipes.infraestructure.api

import kotlinx.coroutines.CancellationException

/** Wraps a suspending call in [Result] without swallowing coroutine cancellation. */
internal suspend fun <T> apiCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }

/** Human readable message for a failed call. */
fun Throwable.readableMessage(): String =
    message?.takeIf { it.isNotBlank() }
        ?: this::class.simpleName
        ?: "Unexpected error"
