package com.mirkwood.rutinify.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Process-lifetime scope; used by receivers with `goAsync()`. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
