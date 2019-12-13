package dev.eternalbox.eternaljukebox

import io.vertx.ext.web.LanguageHeader
import java.text.MessageFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.contracts.ExperimentalContracts

@ExperimentalContracts
class LanguageData(private val jukebox: EternalJukebox) {
    private val rootErrorMessage: ResourceBundle = ResourceBundle.getBundle("ErrorMessages", Locale.ROOT)
    private val errorMessages: MutableMap<String, ResourceBundle> = HashMap()

    fun errorMessage(language: String, key: String): String? {
        val bundle = errorMessages[language] ?: rootErrorMessage
        if (!bundle.containsKey(key)) return null
        return bundle.getString(key)
    }
    fun errorMessageArray(language: String, key: String, params: Array<out Any?>): String? {
        val bundle = errorMessages[language] ?: rootErrorMessage
        if (!bundle.containsKey(key)) return null
        return MessageFormat.format(bundle.getString(key), *params) // >:(
    }
    fun errorMessage(languages: List<LanguageHeader>, key: String): String {
        for (language in languages) {
            return errorMessage(language.tag(), key) ?: continue
        }

        return errorMessage("NONE", key) ?: key
    }
    fun errorMessageArray(languages: List<LanguageHeader>, key: String, params: Array<out Any?>): String {
        for (language in languages) {
            return errorMessageArray(language.tag(), key, params) ?: continue
        }

        return errorMessageArray("NONE", key, params) ?: key
    }
}