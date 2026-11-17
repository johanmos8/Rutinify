package com.mirkwood.rutinify.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** Process-lifetime scope; used by receivers with `goAsync()` and by the alarm screen buttons. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
