package dev.eternalbox.common.utils

import kotlinx.serialization.json.*

public inline fun JsonObject.getJsonObject(key: String) =
    getValue(key).jsonObject

public inline fun JsonObject.getJsonArray(key: String) =
    getValue(key).jsonArray

public inline fun JsonObject.getJsonPrimitive(key: String) =
    getValue(key).jsonPrimitive

public inline fun JsonObject.getString(key: String) =
    getValue(key).jsonPrimitive.content

public inline fun JsonObject.getInt(key: String) =
    getValue(key).jsonPrimitive.int

public inline fun JsonObject.getLong(key: String) =
    getValue(key).jsonPrimitive.long


public inline fun JsonObject.getJsonObjectOrNull(key: String) =
    get(key) as? JsonObject

public inline fun JsonObject.getJsonArrayOrNull(key: String) =
    get(key) as? JsonArray

public inline fun JsonObject.getJsonPrimitiveOrNull(key: String) =
    (get(key) as? JsonPrimitive)

public inline fun JsonObject.getStringOrNull(key: String) =
    (get(key) as? JsonPrimitive)?.contentOrNull

public inline fun JsonObject.getIntOrNull(key: String) =
    (get(key) as? JsonPrimitive)?.intOrNull

public inline fun JsonObject.getLongOrNull(key: String) =
    (get(key) as? JsonPrimitive)?.longOrNull

public inline fun JsonObject.getDoubleOrNull(key: String) =
    (get(key) as? JsonPrimitive)?.doubleOrNull

public inline fun JsonObject.getDoubleOrNull(vararg keys: String) =
    keys.firstNotNullOfOrNull { (get(it) as? JsonPrimitive)?.doubleOrNull }

public inline fun JsonObject.getBooleanOrNull(key: String) =
    (get(key) as? JsonPrimitive)?.booleanOrNull