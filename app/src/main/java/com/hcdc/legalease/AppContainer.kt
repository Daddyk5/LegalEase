package com.hcdc.legalease

import android.content.Context
import com.hcdc.legalease.data.DocumentRepository
import com.hcdc.legalease.ml.TFLiteClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons, created once by [LegalEaseApplication]. */
class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val documentRepository = DocumentRepository()
    val classifier = TFLiteClassifier(context, applicationScope)
}
