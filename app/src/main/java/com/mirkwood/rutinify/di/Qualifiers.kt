package com.mirkwood.rutinify.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Scope que vive lo mismo que el proceso; lo usan los receivers con `goAsync()`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
