package dev.eternalbox.ytmusicapi

typealias UnknownJsonObj = Map<String, Any>
typealias MutableUnknownJsonObj = MutableMap<String, Any>

data class SimpleEntry(override val key: String, override val value: String): Map.Entry<String, String>