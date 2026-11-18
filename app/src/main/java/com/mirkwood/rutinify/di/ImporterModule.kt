package com.mirkwood.rutinify.di

import com.mirkwood.rutinify.domain.importer.PlanExporter
import com.mirkwood.rutinify.data.importer.JsonPlanExporter
import com.mirkwood.rutinify.data.draft.InMemoryDraftStore
import com.mirkwood.rutinify.data.importer.RawPromptTemplateSource
import com.mirkwood.rutinify.data.share.InMemorySharedTextInbox
import com.mirkwood.rutinify.data.importer.RoutineJsonImporter
import com.mirkwood.rutinify.data.system.AndroidClipboard
import com.mirkwood.rutinify.data.system.AndroidTextSharer
import com.mirkwood.rutinify.data.system.SharedPrefsTemplateScenarioStore
import com.mirkwood.rutinify.domain.importer.PromptTemplateSource
import com.mirkwood.rutinify.domain.importer.RoutineImporter
import com.mirkwood.rutinify.domain.importer.TemplateScenarioStore
import com.mirkwood.rutinify.domain.repository.DraftStore
import com.mirkwood.rutinify.domain.repository.SharedTextInbox
import com.mirkwood.rutinify.domain.system.Clipboard
import com.mirkwood.rutinify.domain.system.TextSharer
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

    @Binds
    abstract fun bindSharedTextInbox(impl: InMemorySharedTextInbox): SharedTextInbox

    @Binds
    abstract fun bindTextSharer(impl: AndroidTextSharer): TextSharer

    @Binds
    abstract fun bindTemplateScenarioStore(impl: SharedPrefsTemplateScenarioStore): TemplateScenarioStore

    @Binds
    abstract fun bindPlanExporter(impl: JsonPlanExporter): PlanExporter
}
