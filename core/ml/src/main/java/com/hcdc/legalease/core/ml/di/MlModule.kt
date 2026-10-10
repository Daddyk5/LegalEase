package com.hcdc.legalease.core.ml.di

import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.OfflineModelProvisioner
import com.hcdc.legalease.core.domain.engine.PageImageRenderer
import com.hcdc.legalease.core.ml.MlKitTextExtractor
import com.hcdc.legalease.core.ml.PdfPageImageRenderer
import com.hcdc.legalease.core.ml.TFLiteClassifier
import com.hcdc.legalease.core.ml.llm.ModelProvisioner
import com.hcdc.legalease.core.ml.llm.OnDeviceLlmAnalyzer
import com.google.firebase.storage.FirebaseStorage
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MlModule {

    @Binds
    abstract fun bindClassifier(impl: TFLiteClassifier): ContractClassifier

    @Binds
    abstract fun bindTextExtractor(impl: MlKitTextExtractor): TextExtractor

    @Binds
    abstract fun bindLlmAnalyzer(impl: OnDeviceLlmAnalyzer): LlmAnalyzer

    @Binds
    abstract fun bindModelProvisioner(impl: ModelProvisioner): OfflineModelProvisioner

    @Binds
    abstract fun bindPageRenderer(impl: PdfPageImageRenderer): PageImageRenderer

    companion object {
        @Provides
        fun storage(): FirebaseStorage = FirebaseStorage.getInstance()
    }
}
