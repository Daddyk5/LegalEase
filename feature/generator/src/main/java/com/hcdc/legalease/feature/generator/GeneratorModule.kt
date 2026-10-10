package com.hcdc.legalease.feature.generator

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class GeneratorModule {
    @Binds
    abstract fun bindContractDocuments(impl: AndroidContractDocuments): ContractDocuments
}
