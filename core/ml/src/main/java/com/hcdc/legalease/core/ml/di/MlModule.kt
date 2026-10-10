package com.hcdc.legalease.core.ml.di

import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.ml.MlKitTextExtractor
import com.hcdc.legalease.core.ml.TFLiteClassifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MlModule {

    @Binds
    abstract fun bindClassifier(impl: TFLiteClassifier): ContractClassifier

    @Binds
    abstract fun bindTextExtractor(impl: MlKitTextExtractor): TextExtractor
}
