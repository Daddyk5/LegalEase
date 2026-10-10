package com.hcdc.legalease.core.data.local

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [AnalysisEntity::class, DocumentPageEntity::class, GeneratedContractEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class LegalEaseDatabase : RoomDatabase() {
    abstract fun analysisDao(): AnalysisDao
    abstract fun documentPageDao(): DocumentPageDao
    abstract fun generatedContractDao(): GeneratedContractDao

    companion object {
        const val NAME = "legalease.db"
    }
}
