package com.example.data.repository

import com.example.data.local.IptvDao
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import com.example.data.network.XtreamService
import com.example.data.parser.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class IptvRepository(private val iptvDao: IptvDao) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    val playlists: Flow<List<Playlist>> = iptvDao.getAllPlaylists()

    suspend fun getPlaylistById(id: Long): Playlist? = withContext(Dispatchers.IO) {
        iptvDao.getPlaylistById(id)
    }

    suspend fun addPlaylist(playlist: Playlist): Long = withContext(Dispatchers.IO) {
        iptvDao.insertPlaylist(playlist)
    }

    suspend fun deletePlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        iptvDao.deleteChannelsByPlaylist(playlist.id)
        iptvDao.deletePlaylist(playlist)
    }

    suspend fun refreshPlaylist(context: android.content.Context, playlistId: Long): Unit = withContext(Dispatchers.IO) {
        val playlist = iptvDao.getPlaylistById(playlistId) ?: return@withContext
        iptvDao.deleteChannelsByPlaylist(playlistId)

        val channels = if (playlist.type == "M3U") {
            val content = downloadUrl(playlist.url) ?: throw Exception("M3U çalma listesi indirilemedi. Bağlantı adresini kontrol edin.")
            M3uParser.parse(content, playlistId)
        } else {
            XtreamService.loginAndFetchChannels(
                context = context,
                playlistId = playlistId,
                baseUrl = playlist.url,
                username = playlist.username ?: "",
                password = playlist.password ?: ""
            )
        }

        if (channels.isEmpty()) {
            throw Exception("İçerik bulunamadı veya parse edilemedi.")
        }

        // Chunk/Batch insertion (500 adetlik paketlerle veritabanına yazarak RAM ve performans tasarrufu sağlarız)
        channels.chunked(500).forEach { chunk ->
            iptvDao.insertChannels(chunk)
        }
    }

    private suspend fun downloadUrl(url: String): String? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).build()
        try {
            httpClient.newCall(req).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getChannelsByType(playlistId: Long, type: String): Flow<List<IptvChannel>> =
        iptvDao.getChannelsByType(playlistId, type)

    fun getChannelsByGroup(playlistId: Long, type: String, groupTitle: String): Flow<List<IptvChannel>> =
        iptvDao.getChannelsByGroup(playlistId, type, groupTitle)

    fun getGroupsByType(playlistId: Long, type: String): Flow<List<String>> =
        iptvDao.getGroupsByType(playlistId, type)

    fun getFavorites(playlistId: Long): Flow<List<IptvChannel>> =
        iptvDao.getFavorites(playlistId)

    fun getRecents(playlistId: Long): Flow<List<IptvChannel>> =
        iptvDao.getRecents(playlistId)

    fun searchChannels(playlistId: Long, type: String, query: String): Flow<List<IptvChannel>> =
        iptvDao.searchChannels(playlistId, type, query)

    suspend fun toggleFavorite(uniqueId: String, isFav: Boolean) = withContext(Dispatchers.IO) {
        iptvDao.updateFavorite(uniqueId, isFav)
    }

    suspend fun markAsWatched(uniqueId: String) = withContext(Dispatchers.IO) {
        iptvDao.markAsWatched(uniqueId, System.currentTimeMillis())
    }

    suspend fun getChannelCount(playlistId: Long): Int = withContext(Dispatchers.IO) {
        iptvDao.getChannelCount(playlistId)
    }

    suspend fun getChannelsByUniqueIds(ids: List<String>): List<IptvChannel> = withContext(Dispatchers.IO) {
        iptvDao.getChannelsByUniqueIds(ids)
    }

    fun getChannelsByUniqueIdsFlow(ids: List<String>): Flow<List<IptvChannel>> =
        iptvDao.getChannelsByUniqueIdsFlow(ids)
}
