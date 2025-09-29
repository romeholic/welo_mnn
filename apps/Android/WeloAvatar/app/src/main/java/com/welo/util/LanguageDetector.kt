package com.welo.util

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier

class LanguageDetector {
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    fun detectLanguage(text: String, callback: (String) -> Unit) {

        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") { // "und" 表示无法识别
                    callback("unknown")
                } else {
                    callback(languageCode)
                }
            }
            .addOnFailureListener {
                callback("unknown")
            }
    }

    // 批量检测
    fun detectPossibleLanguages(text: String, callback: (List<Pair<String, Float>>) -> Unit) {
        languageIdentifier.identifyPossibleLanguages(text)
            .addOnSuccessListener { identifiedLanguages ->
                val results = identifiedLanguages.map {
                    it.languageTag to it.confidence
                }
                callback(results)
            }
            .addOnFailureListener {
                callback(emptyList())
            }
    }

    // 释放资源
    fun release() {
        languageIdentifier.close()
    }
}
