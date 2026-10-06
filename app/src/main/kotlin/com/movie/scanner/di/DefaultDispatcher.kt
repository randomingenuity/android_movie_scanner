package com.movie.scanner.di

import javax.inject.Qualifier

/**
 * Marks the application default [kotlinx.coroutines.CoroutineDispatcher] for CPU-bound work off the main thread.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
