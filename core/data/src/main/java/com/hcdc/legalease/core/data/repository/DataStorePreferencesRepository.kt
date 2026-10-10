package com.hcdc.legalease.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DataStorePreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override val cloudSyncEnabled: Flow<Boolean> = dataStore.data.map { it[CLOUD_SYNC_ENABLED] ?: false }

    override suspend fun setCloudSyncEnabled(enabled: Boolean) {
        dataStore.edit { it[CLOUD_SYNC_ENABLED] = enabled }
    }

    override val retention: Flow<RetentionPeriod> = dataStore.data.map { prefs ->
        RetentionPeriod.entries.firstOrNull { it.name == prefs[RETENTION] } ?: RetentionPeriod.NEVER
    }

    override suspend fun setRetention(period: RetentionPeriod) {
        dataStore.edit { it[RETENTION] = period.name }
    }

    override val appLockEnabled: Flow<Boolean> = dataStore.data.map { it[APP_LOCK] ?: false }

    override suspend fun setAppLockEnabled(enabled: Boolean) {
        dataStore.edit { it[APP_LOCK] = enabled }
    }

    override val acceptedPrivacyNoticeVersion: Flow<Int> = dataStore.data.map { it[PRIVACY_NOTICE] ?: 0 }

    override suspend fun acceptPrivacyNotice(version: Int) {
        dataStore.edit { it[PRIVACY_NOTICE] = version }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /** Highest `updatedAt` pulled from Firestore for [uid]; per user so switching accounts re-pulls. */
    suspend fun lastPulledAt(uid: String): Long = dataStore.data.first()[lastPulledKey(uid)] ?: 0L

    suspend fun setLastPulledAt(uid: String, millis: Long) {
        dataStore.edit { it[lastPulledKey(uid)] = millis }
    }

    private fun lastPulledKey(uid: String) = longPreferencesKey("last_pulled_at_$uid")

    private companion object {
        val CLOUD_SYNC_ENABLED = booleanPreferencesKey("cloud_sync_enabled")
        val RETENTION = stringPreferencesKey("retention")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val PRIVACY_NOTICE = intPreferencesKey("privacy_notice_version")
    }
}
