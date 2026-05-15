package com.simpletv.channel

data class ChannelList(
    val version: Int,
    val updatedAt: String?,
    val channels: List<Channel>
)

data class Channel(
    val id: String,
    val name: String,
    val group: String?,
    val sources: List<ChannelSource>
)

data class ChannelSource(
    val url: String,
    val label: String?
)
