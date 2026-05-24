package com.example.data.network

import android.util.Log
import com.example.data.model.IptvChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object XtreamService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    suspend fun loginAndFetchChannels(
        playlistId: Long,
        baseUrl: String,
        username: String,
        password: String
    ): List<IptvChannel> {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val loginUrl = "$cleanUrl/player_api.php?username=$username&password=$password"
        
        // Adım 1: Oturum Açma ve Doğrulama
        val loginRequest = Request.Builder().url(loginUrl).build()
        val loginResponseString = executeRequestOnThread(loginRequest) 
            ?: throw Exception("Xtream sunucusuna bağlanılamadı. Lütfen URL ve internet bağlantınızı kontrol edin.")
        
        try {
            val json = JSONObject(loginResponseString)
            val userInfo = json.optJSONObject("user_info")
            val status = userInfo?.optString("status") ?: "Active"
            if (!status.equals("Active", ignoreCase = true)) {
                throw Exception("Kullanıcı hesabı aktif değil: $status")
            }
        } catch (e: Exception) {
            if (e.message?.contains("Kullanıcı hesabı") == true) {
                throw e
            }
            // Bazı Xtream API'ler JSON nesnesi yerine farklı formatlar verebilir;
            // Veri çekebiliyorsak işleme devam etmek en doğrusudur.
        }

        val channels = mutableListOf<IptvChannel>()

        // Adım 2: Kategorileri Al (Canlı, Sinema, Dizi) daldırma haritaları
        val liveCats = fetchCategories(cleanUrl, username, password, "get_live_categories")
        val vodCats = fetchCategories(cleanUrl, username, password, "get_vod_categories")
        val seriesCats = fetchCategories(cleanUrl, username, password, "get_series_categories")

        // Adım 3: Canlı Yayınları Al (Live Streams)
        fetchStreams(cleanUrl, username, password, "get_live_streams") { item ->
            val id = item.optString("stream_id")
            val name = item.optString("name")
            val logo = item.optString("stream_icon")
            val catId = item.optString("category_id")
            val ext = item.optString("container_extension").takeIf { !it.isNullOrEmpty() } ?: "ts"
            val group = liveCats[catId] ?: "Canlı TV"
            val streamUrl = "$cleanUrl/live/$username/$password/$id.$ext"

            channels.add(
                IptvChannel(
                    uniqueId = "${playlistId}_live_$id",
                    playlistId = playlistId,
                    channelId = id,
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logo,
                    groupTitle = group,
                    type = "LIVE"
                )
            )
        }

        // Adım 4: Sinema (VOD) Yayınlarını Al
        fetchStreams(cleanUrl, username, password, "get_vod_streams") { item ->
            val id = item.optString("stream_id")
            val name = item.optString("name")
            val logo = item.optString("stream_icon")
            val catId = item.optString("category_id")
            val ext = item.optString("container_extension").takeIf { !it.isNullOrEmpty() } ?: "mp4"
            val group = vodCats[catId] ?: "Sinema"
            val streamUrl = "$cleanUrl/movie/$username/$password/$id.$ext"

            channels.add(
                IptvChannel(
                    uniqueId = "${playlistId}_vod_$id",
                    playlistId = playlistId,
                    channelId = id,
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logo,
                    groupTitle = group,
                    type = "MOVIE"
                )
            )
        }

        // Adım 5: Dizileri Al (Series)
        fetchStreams(cleanUrl, username, password, "get_series") { item ->
            val id = item.optString("series_id")
            val name = item.optString("name")
            val logo = item.optString("cover")
            val catId = item.optString("category_id")
            val group = seriesCats[catId] ?: "Diziler"
            // Serilerin her biri için Xtream özel oynatma URL'leri sunulmaktadır, varsayılan seriler listesi
            val streamUrl = "$cleanUrl/series/$username/$password/$id.mp4"

            channels.add(
                IptvChannel(
                    uniqueId = "${playlistId}_series_$id",
                    playlistId = playlistId,
                    channelId = id,
                    name = name,
                    streamUrl = streamUrl,
                    logoUrl = logo,
                    groupTitle = group,
                    type = "SERIES"
                )
            )
        }

        return channels
    }

    private fun fetchCategories(
        baseUrl: String,
        u: String,
        p: String,
        action: String
    ): Map<String, String> {
        val url = "$baseUrl/player_api.php?username=$u&password=$p&action=$action"
        val map = mutableMapOf<String, String>()
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return map
                val str = resp.body?.string() ?: return map
                val arr = JSONArray(str)
                for (i in 0 until arr.length()) {
                    val catObj = arr.getJSONObject(i)
                    val id = catObj.optString("category_id")
                    val name = catObj.optString("category_name")
                    if (id.isNotEmpty() && name.isNotEmpty()) {
                        map[id] = name
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("XtreamService", "Kategoriler yüklenirken hata oluştu: $action. Detay: ${e.message}")
        }
        return map
    }

    private fun fetchStreams(
        baseUrl: String,
        u: String,
        p: String,
        action: String,
        onItemParsed: (JSONObject) -> Unit
    ) {
        val url = "$baseUrl/player_api.php?username=$u&password=$p&action=$action"
        try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return
                val str = resp.body?.string() ?: return
                if (str.trim().startsWith("[")) {
                    val arr = JSONArray(str)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        onItemParsed(obj)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("XtreamService", "Kanallar yüklenirken hata oluştu: $action. Detay: ${e.message}")
        }
    }

    suspend fun fetchSeriesInfo(
        baseUrl: String,
        username: String,
        password: String,
        seriesId: String
    ): String? {
        val cleanUrl = baseUrl.trim().removeSuffix("/")
        val url = "$cleanUrl/player_api.php?username=$username&password=$password&action=get_series_info&series_id=$seriesId"
        val request = Request.Builder().url(url).build()
        return executeRequestOnThread(request)
    }

    private suspend fun executeRequestOnThread(request: Request): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}
