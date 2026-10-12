package com.hcdc.legalease.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.hcdc.legalease.core.data.account.FirebaseAccountDataRepository
import com.hcdc.legalease.core.data.auth.FirebaseAuthRepository
import com.hcdc.legalease.core.data.config.RemoteConfigFeatureFlags
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.AuditDao
import com.hcdc.legalease.core.data.local.DatabaseKeyProvider
import com.hcdc.legalease.core.data.local.DocumentPageDao
import com.hcdc.legalease.core.data.local.GeneratedContractDao
import com.hcdc.legalease.core.data.local.LegalEaseDatabase
import com.hcdc.legalease.core.data.repository.DataStorePreferencesRepository
import com.hcdc.legalease.core.data.repository.FirebaseUserSession
import com.hcdc.legalease.core.data.repository.OfflineFirstAnalysisRepository
import com.hcdc.legalease.core.data.repository.OfflineFirstContractRepository
import com.hcdc.legalease.core.data.repository.RoomAuditLog
import com.hcdc.legalease.core.data.telemetry.FirebaseAnalyticsTracker
import com.hcdc.legalease.core.data.telemetry.FirebaseCrashReporter
import com.hcdc.legalease.core.data.telemetry.FirebasePerformanceTracer
import com.hcdc.legalease.core.data.work.WorkManagerAnalysisScheduler
import com.hcdc.legalease.core.data.work.WorkManagerSyncScheduler
import com.hcdc.legalease.core.domain.engine.AccountDataRepository
import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.AuditLog
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.repository.AuthRepository
import com.hcdc.legalease.core.domain.repository.ContractRepository
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import com.hcdc.legalease.core.domain.repository.UserSession
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import com.hcdc.legalease.core.domain.telemetry.CrashReporter
import com.hcdc.legalease.core.domain.telemetry.PerformanceTracer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.GeneralSecurityException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataBindsModule {
    @Binds abstract fun analysisRepository(impl: OfflineFirstAnalysisRepository): AnalysisRepository

    @Binds abstract fun contractRepository(impl: OfflineFirstContractRepository): ContractRepository

    @Binds abstract fun preferencesRepository(impl: DataStorePreferencesRepository): PreferencesRepository

    @Binds abstract fun userSession(impl: FirebaseUserSession): UserSession

    @Binds abstract fun analysisScheduler(impl: WorkManagerAnalysisScheduler): AnalysisScheduler

    @Binds abstract fun syncScheduler(impl: WorkManagerSyncScheduler): SyncScheduler

    @Binds abstract fun authRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds abstract fun featureFlags(impl: RemoteConfigFeatureFlags): FeatureFlagsRepository

    @Binds abstract fun analytics(impl: FirebaseAnalyticsTracker): AnalyticsTracker

    @Binds abstract fun tracer(impl: FirebasePerformanceTracer): PerformanceTracer

    @Binds abstract fun crashReporter(impl: FirebaseCrashReporter): CrashReporter

    @Binds abstract fun auditLog(impl: RoomAuditLog): AuditLog

    @Binds abstract fun accountData(impl: FirebaseAccountDataRepository): AccountDataRepository
}

@Module
@InstallIn(SingletonComponent::class)
internal object DataProvidesModule {

    private const val TAG = "DataModule"

    /**
     * Opens the SQLCipher-encrypted database. If the stored passphrase can no longer be unsealed
     * (e.g. the Keystore key was invalidated), the unreadable database is discarded and recreated;
     * signed-in users get their results back from Firestore on the next sync.
     */
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context, keys: DatabaseKeyProvider): LegalEaseDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = try {
            keys.passphrase()
        } catch (e: GeneralSecurityException) {
            Log.e(TAG, "Database key unusable; resetting local database", e)
            context.deleteDatabase(LegalEaseDatabase.NAME)
            keys.reset()
            keys.passphrase()
        }
        return Room.databaseBuilder(context, LegalEaseDatabase::class.java, LegalEaseDatabase.NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
    }

    @Provides fun analysisDao(db: LegalEaseDatabase): AnalysisDao = db.analysisDao()

    @Provides fun documentPageDao(db: LegalEaseDatabase): DocumentPageDao = db.documentPageDao()

    @Provides fun generatedContractDao(db: LegalEaseDatabase): GeneratedContractDao = db.generatedContractDao()

    @Provides fun auditDao(db: LegalEaseDatabase): AuditDao = db.auditDao()

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
}

/** Firebase SDK entry points. */
@Module
@InstallIn(SingletonComponent::class)
internal object FirebaseModule {
    @Provides fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    fun analytics(@ApplicationContext context: Context): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)

    @Provides fun performance(): FirebasePerformance = FirebasePerformance.getInstance()

    @Provides fun crashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()

    /** Same region as Firestore (Singapore). */
    @Provides fun functions(): FirebaseFunctions = FirebaseFunctions.getInstance("asia-southeast1")

    @Provides
    fun remoteConfig(): FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance().apply {
        setConfigSettingsAsync(
            FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(REMOTE_CONFIG_FETCH_INTERVAL_SECONDS)
                .build()
        )
    }

    private const val REMOTE_CONFIG_FETCH_INTERVAL_SECONDS = 3_600L
}
