package com.hcdc.legalease.core.ml.llm

/**
 * Model tiers. File names are deliberately neutral: no model names appear in paths, the UI, or logs.
 * - [PRIMARY]: Gemma 3 1B instruction-tuned, int4, `.litertlm` (devices with about 6 GB of RAM or more).
 * - [LITE]: Gemma 3 270M instruction-tuned, int4, `.litertlm` (smaller devices).
 */
enum class ModelTier(val fileName: String) {
    PRIMARY("core-a.bin"),
    LITE("core-b.bin"),
}

/**
 * @property sha256 lowercase hex SHA-256 of the plaintext `.litertlm` file. Every download is
 *   verified against it; a mismatch deletes the file and retries.
 * @property sizeBytes size of the plaintext file, for the free-storage check.
 * @property maxTokens context window the engine is configured with.
 */
data class ModelSpec(
    val tier: ModelTier,
    val sha256: String,
    val sizeBytes: Long,
    val maxTokens: Int,
) {
    /** False until a real hash and size are pinned below. Unpinned models are never used. */
    val isPinned: Boolean get() = SHA256_HEX.matches(sha256) && sizeBytes > 0

    private companion object {
        val SHA256_HEX = Regex("^[0-9a-f]{64}$")
    }
}

/**
 * Pinned model artifacts.
 *
 * TODO(release): pin the hashes and sizes of the exact files uploaded to Play Asset Delivery and
 * Cloud Storage (see SETUP.md, "On-device language model"). Until then both tiers stay unpinned, the
 * app never loads a model, and every analysis uses the classifier with rule-based checks
 * ("needs review").
 */
object ModelCatalog {
    val PRIMARY = ModelSpec(ModelTier.PRIMARY, sha256 = "", sizeBytes = 0, maxTokens = 4096)
    val LITE = ModelSpec(ModelTier.LITE, sha256 = "", sizeBytes = 0, maxTokens = 2048)

    fun spec(tier: ModelTier): ModelSpec = when (tier) {
        ModelTier.PRIMARY -> PRIMARY
        ModelTier.LITE -> LITE
    }

    /** Asset pack holding both model files for Play Store installs. */
    const val ASSET_PACK = "llm_pack"

    /** Folder inside the asset pack and in Cloud Storage. */
    const val REMOTE_FOLDER = "models"
}
