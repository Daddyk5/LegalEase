package com.hcdc.legalease.core.ml

/** Failures that stop the offline classifier from producing a prediction. */
sealed class ClassifierException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The model could not be downloaded or is not cached on the device yet. */
    class ModelUnavailable(message: String, cause: Throwable? = null) : ClassifierException(message, cause)

    /** The model file exists but its tensors do not match what the app expects. */
    class IncompatibleModel(message: String, cause: Throwable? = null) : ClassifierException(message, cause)

    /** vocab.json is missing, malformed, or empty. */
    class VocabUnavailable(message: String, cause: Throwable? = null) : ClassifierException(message, cause)
}
