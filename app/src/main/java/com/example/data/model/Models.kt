package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import java.io.Serializable

@Entity(tableName = "playlists")
data class Playlist(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String, // "M3U" or "XTREAM"
    val url: String,
    val username: String? = null,
    val password: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(
    tableName = "iptv_channels",
    indices = [
        Index(value = ["playlistId", "type", "name"]),
        Index(value = ["playlistId", "type", "groupTitle", "name"]),
        Index(value = ["playlistId", "isFavorite", "name"]),
        Index(value = ["playlistId", "isRecent", "lastWatchedTimestamp"])
    ]
)
data class IptvChannel(
    @PrimaryKey val uniqueId: String, // format: "${playlistId}_${channelId}"
    val playlistId: Long,
    val channelId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null, // Cihaz/Sunucu tarafından sağlanan grup/kategori adı
    val isFavorite: Boolean = false,
    val isRecent: Boolean = false,
    val lastWatchedTimestamp: Long = 0,
    val type: String = "LIVE" // "LIVE" (Canlı), "MOVIE" (Sinema), "SERIES" (Dizi)
) : Serializable

data class XtreamSeason(
    val name: String,
    val seasonNumber: Int
) : Serializable

data class XtreamEpisode(
    val id: String,
    val name: String,
    val episodeNum: Int,
    val streamUrl: String,
    val logoUrl: String? = null
) : Serializable

