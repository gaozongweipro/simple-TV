package com.simpletv.channel

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class ChannelParser {
    fun parse(rawJson: String): ChannelList {
        val root = JsonParser.parseString(rawJson).asObject("Channel list root must be a JSON object.")
        val version = root.requiredInt("version")
        val updatedAt = root.optionalString("updatedAt")
        val channelArray = root.requiredArray("channels")
        require(channelArray.isNotEmpty()) { "Channel list must contain at least one channel." }

        val channels = channelArray.map { item ->
            val channelObject = item.asObject("Channel item must be a JSON object.")
            val id = channelObject.requiredString("id")
            require(id.isNotEmpty()) { "Channel id must not be empty." }
            val name = channelObject.requiredString("name")
            require(name.isNotEmpty()) { "Channel $id name must not be empty." }
            val sourcesArray = channelObject.requiredArray("sources")
            require(sourcesArray.isNotEmpty()) { "Channel $id must contain at least one source." }

            val sources = sourcesArray.map { sourceItem ->
                val sourceObject = sourceItem.asObject("Source item for channel $id must be a JSON object.")
                val url = sourceObject.requiredString("url")
                require(url.startsWith("http://") || url.startsWith("https://")) {
                    "Source url for channel $id must be http or https."
                }
                ChannelSource(
                    url = url,
                    label = sourceObject.optionalString("label")
                )
            }

            Channel(
                id = id,
                name = name,
                group = channelObject.optionalString("group"),
                sources = sources
            )
        }

        return ChannelList(version = version, updatedAt = updatedAt, channels = channels)
    }

    private fun JsonElement.asObject(message: String): JsonObject {
        require(isJsonObject) { message }
        return asJsonObject
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = get(name) ?: throw IllegalArgumentException("Missing required field: $name.")
        require(!value.isJsonNull) { "Missing required field: $name." }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Field $name must be a string." }
        return value.asString.trim()
    }

    private fun JsonObject.optionalString(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Field $name must be a string." }
        return value.asString.trim().ifEmpty { null }
    }

    private fun JsonObject.requiredInt(name: String): Int {
        val value = get(name) ?: throw IllegalArgumentException("Missing required field: $name.")
        require(!value.isJsonNull) { "Missing required field: $name." }
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "Field $name must be an integer." }
        val number = value.asJsonPrimitive.asNumber
        require(number.toString().matches(INTEGER_PATTERN)) { "Field $name must be an integer." }
        return value.asInt
    }

    private fun JsonObject.requiredArray(name: String): List<JsonElement> {
        val value = get(name) ?: throw IllegalArgumentException("Missing required field: $name.")
        require(value.isJsonArray) { "Field $name must be an array." }
        return value.asJsonArray.toList()
    }

    private companion object {
        private val INTEGER_PATTERN = Regex("-?\\d+")
    }
}
