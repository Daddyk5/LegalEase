package com.hcdc.legalease.core.data.integrity

import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug builds are not recognised by Play, so the integrity check is skipped. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class IntegrityModule {
    @Binds abstract fun integrityChecker(impl: DisabledIntegrityChecker): IntegrityChecker
}
