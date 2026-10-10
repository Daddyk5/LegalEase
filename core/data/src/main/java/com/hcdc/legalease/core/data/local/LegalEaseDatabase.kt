package com.hcdc.legalease.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        AnalysisEntity::class, DocumentPageEntity::class, GeneratedContractEntity::class, AuditEntryEntity::class,
    ],
    version = 4,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
    ],
)
abstract class LegalEaseDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun generatedContractDao(): GeneratedContractDao
    abstract fun auditDao(): AuditDao

    companion object {
        const val NAME = "legalease.db"
    }
}
