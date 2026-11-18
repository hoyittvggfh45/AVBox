package com.github.avbox.osc.haikuo

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

object HaikuoJson {

    private val gson = Gson()

    fun parse(text: String): Any? {
        return try {
            convert(JsonParser.parseString(text))
        } catch (e: Exception) {
            null
        }
    }

    fun parseOrThrow(text: String): Any? {
        return convert(JsonParser.parseString(text))
    }

    fun stringify(value: Any?): String {
        return gson.toJson(value)
    }

    fun asMap(value: Any?): Map<String, Any?> {
        if (value is Map<*, *>) {
            val out = LinkedHashMap<String, Any?>()
            for ((key, entryValue) in value) {
                out[key.toString()] = entryValue
            }
            return out
        }
        return emptyMap()
    }

    fun asList(value: Any?): List<Any?> {
        if (value is List<*>) {
            return value
        }
        return emptyList()
    }

    fun str(value: Any?): String {
        return when (value) {
            null -> ""
            is String -> value
            else -> value.toString()
        }
    }

    fun num(value: Any?): Double? {
        return when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }

    private fun convert(element: JsonElement): Any? {
        return when {
            element is JsonNull -> null
            element is JsonObject -> {
                val map = LinkedHashMap<String, Any?>()
                for ((key, child) in element.entrySet()) {
                    map[key] = convert(child)
                }
                map
            }
            element is JsonArray -> {
                val list = ArrayList<Any?>()
                for (child in element) {
                    list.add(convert(child))
                }
                list
            }
            element is JsonPrimitive -> {
                when {
                    element.isBoolean -> element.asBoolean
                    element.isString -> element.asString
                    element.isNumber -> numberOf(element.asString)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun numberOf(text: String): Number {
        if (text.contains('.') || text.contains('e') || text.contains('E')) {
            return text.toDoubleOrNull() ?: 0.0
        }
        return text.toLongOrNull() ?: text.toDoubleOrNull() ?: 0L
    }
}
