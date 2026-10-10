package com.hcdc.legalease.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.work.WorkManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.DatabaseKeyProvider
import com.hcdc.legalease.core.data.local.DocumentPageDao
import com.hcdc.legalease.core.data.local.GeneratedContractDao
import com.hcdc.legalease.core.data.local.LegalEaseDatabase
import com.hcdc.legalease.core.data.repository.DataStorePreferencesRepository
import com.hcdc.legalease.core.data.repository.FirebaseUserSession
import com.hcdc.legalease.core.data.repository.OfflineFirstAnalysisRepository
import com.hcdc.legalease.core.data.repository.OfflineFirstContractRepository
import com.hcdc.legalease.core.data.work.WorkManagerAnalysisScheduler
import com.hcdc.legalease.core.data.work.WorkManagerSyncScheduler
import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.repository.ContractRepository
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import com.hcdc.legalease.core.domain.repository.UserSession
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

    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides fun workManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    @Provides fun firebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()
    @Provides fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance()
}
