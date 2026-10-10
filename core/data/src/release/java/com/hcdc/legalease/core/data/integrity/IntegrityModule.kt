package com.hcdc.legalease.core.data.integrity

import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Release builds check Play Integrity through the integrityVerdict Cloud Function. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class IntegrityModule {
    @Binds abstract fun integrityChecker(impl: PlayIntegrityChecker): IntegrityChecker
}
