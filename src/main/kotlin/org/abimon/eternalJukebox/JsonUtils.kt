package org.abimon.eternalJukebox

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule

val JSON_MAPPER: ObjectMapper = ObjectMapper()
        .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

val YAML_MAPPER: ObjectMapper = ObjectMapper(YAMLFactory())
        .registerModules(Jdk8Module(), KotlinModule(), JavaTimeModule(), ParameterNamesModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .setSerializationInclusion(JsonInclude.Include.NON_ABSENT)

fun jsonStringOfObject(vararg pairs: kotlin.Pair<String, Any>): String = JSON_MAPPER.writeValueAsString(HashMap<String, Any>().apply { putAll(pairs) })