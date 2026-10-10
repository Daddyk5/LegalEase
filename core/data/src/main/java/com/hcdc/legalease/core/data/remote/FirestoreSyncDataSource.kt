package com.hcdc.legalease.core.data.remote

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import com.hcdc.legalease.core.data.local.AnalysisEntity
import com.hcdc.legalease.core.data.local.GeneratedContractEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

/** Reads and writes the signed-in user's Firestore collections. Throws Firestore exceptions; the caller retries. */
internal class FirestoreSyncDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    private fun analyses(uid: String): CollectionReference =
        firestore.collection(FirestoreSchema.USERS).document(uid).collection(FirestoreSchema.ANALYSES)

    private fun contracts(uid: String): CollectionReference =
        firestore.collection(FirestoreSchema.USERS).document(uid).collection(FirestoreSchema.CONTRACTS)

    /** Replaces the whole document so a tombstone also removes previously uploaded fields. */
    suspend fun putAnalysis(uid: String, entity: AnalysisEntity) {
        analyses(uid).document(entity.id).set(entity.toFirestore()).await()
    }

    suspend fun analysesUpdatedSince(uid: String, sinceMillis: Long): List<AnalysisEntity> =
        analyses(uid)
            .whereGreaterThan(FirestoreSchema.F_UPDATED_AT, sinceMillis)
            .get().await()
            .documents.mapNotNull { doc -> doc.data?.let { analysisFromFirestore(doc.id, it) } }

    suspend fun putContract(uid: String, entity: GeneratedContractEntity) {
        contracts(uid).document(entity.id).set(entity.toFirestore()).await()
    }

    /** Deletes every document LegalEase stores for [uid], then the user document itself. */
    suspend fun deleteAll(uid: String) {
        listOf(analyses(uid), contracts(uid)).forEach { deleteCollection(it) }
        firestore.collection(FirestoreSchema.USERS).document(uid).delete().await()
    }

    suspend fun contractsUpdatedSince(uid: String, sinceMillis: Long): List<GeneratedContractEntity> =
        contracts(uid)
            .whereGreaterThan(FirestoreSchema.F_UPDATED_AT, sinceMillis)
            .get().await()
            .documents.mapNotNull { doc -> doc.data?.let { contractFromFirestore(doc.id, it) } }

    private suspend fun deleteCollection(collection: CollectionReference) {
        var docs = collection.limit(DELETE_BATCH).get().await().documents
        while (docs.isNotEmpty()) {
            val batch = firestore.batch()
            docs.forEach { batch.delete(it.reference) }
            batch.commit().await()
            docs = collection.limit(DELETE_BATCH).get().await().documents
        }
    }

    private companion object {
        /** Firestore allows 500 writes per batch. */
        const val DELETE_BATCH = 400L
    }
}
