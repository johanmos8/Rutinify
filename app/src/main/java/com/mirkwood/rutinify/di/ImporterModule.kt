package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.data.importer.RawPromptTemplateSource
import com.mirkwood.rutinify.data.importer.RoutineJsonImporter
import com.mirkwood.rutinify.data.system.AndroidClipboard
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import com.mirkwood.rutinify.domain.repository.DraftStore
import com.mirkwood.rutinify.domain.system.Clipboard
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ImporterModule {

    @Binds
    abstract fun bindRoutineImporter(impl: RoutineJsonImporter): RoutineImporter

    @Binds
    abstract fun bindPromptTemplateSource(impl: RawPromptTemplateSource): PromptTemplateSource

    @Binds
    abstract fun bindDraftStore(impl: InMemoryDraftStore): DraftStore

    @Binds
    abstract fun bindClipboard(impl: AndroidClipboard): Clipboard
}
