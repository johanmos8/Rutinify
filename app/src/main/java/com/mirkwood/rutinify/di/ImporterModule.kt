package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.data.importer.RoutineJsonImporter
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImporterModule {

    @Binds
    abstract fun bindRoutineImporter(impl: RoutineJsonImporter): RoutineImporter
}
