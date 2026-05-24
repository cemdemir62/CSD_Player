package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import com.example.data.repository.IptvRepository
import com.example.data.model.XtreamSeason
import com.example.data.model.XtreamEpisode
import com.example.data.network.XtreamService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface RefreshState {
    object Idle : RefreshState
    data class Loading(val message: String) : RefreshState
    object Success : RefreshState
    data class Error(val message: String) : RefreshState
}

@OptIn(ExperimentalCoroutinesApi::class)
class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("zula_iptv_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = IptvRepository(database.iptvDao())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    // Uygulama Modu: "TV" (D-Pad Kumanda) veya "MOBIL" (Touch) - null ise seçim ekranına yönlendirilir
    private val _appMode = MutableStateFlow<String?>(sharedPrefs.getString("app_mode", null))
    val appMode: StateFlow<String?> = _appMode.asStateFlow()

    private var hasAutoLoggedIn = false

    init {
        viewModelScope.launch {
            // Wait for the first non-empty emissions of playlist from database
            playlists.first { it.isNotEmpty() }.let { list ->
                if (!hasAutoLoggedIn) {
                    val lastPlaylistId = sharedPrefs.getLong("last_playlist_id", -1L)
                    val toSelect = if (lastPlaylistId != -1L) {
                        list.find { it.id == lastPlaylistId } ?: list.firstOrNull()
                    } else {
                        list.firstOrNull()
                    }
                    if (toSelect != null) {
                        _selectedPlaylist.value = toSelect
                    }
                    hasAutoLoggedIn = true
                }
            }
        }
    }

    private val _selectedType = MutableStateFlow("LIVE") // "LIVE", "MOVIE", "SERIES", "FAVORITE", "RECENT"
    val selectedType = _selectedType.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>("Hepsi")
    val selectedGroup = _selectedGroup.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refreshState = _refreshState.asStateFlow()

    private val _activeChannel = MutableStateFlow<IptvChannel?>(null)
    val activeChannel = _activeChannel.asStateFlow()

    // Oynatma listesine ve türe göre aktif kategoriler / gruplar listesi
    val groupsByType: StateFlow<List<String>> = combine(_selectedPlaylist, _selectedType) { playlist, type ->
        playlist to type
    }.flatMapLatest { (playlist, type) ->
        val playlistId = playlist?.id ?: return@flatMapLatest flowOf(emptyList())
        if (type == "FAVORITE" || type == "RECENT") {
            flowOf(emptyList())
        } else {
            repository.getGroupsByType(playlistId, type)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dizi Detay & Bölüm Seçim Ekranı Durumları
    private val _activeSeriesChannel = MutableStateFlow<IptvChannel?>(null)
    val activeSeriesChannel = _activeSeriesChannel.asStateFlow()

    private val _seriesSeasons = MutableStateFlow<List<XtreamSeason>>(emptyList())
    val seriesSeasons = _seriesSeasons.asStateFlow()

    private val _seriesEpisodes = MutableStateFlow<Map<Int, List<XtreamEpisode>>>(emptyMap())
    val seriesEpisodes = _seriesEpisodes.asStateFlow()

    private val _selectedSeasonNum = MutableStateFlow<Int>(1)
    val selectedSeasonNum = _selectedSeasonNum.asStateFlow()

    private val _isFetchingSeriesInfo = MutableStateFlow<Boolean>(false)
    val isFetchingSeriesInfo = _isFetchingSeriesInfo.asStateFlow()

    private val _seriesFetchError = MutableStateFlow<String?>(null)
    val seriesFetchError = _seriesFetchError.asStateFlow()

    fun selectSeries(channel: IptvChannel?) {
        _activeSeriesChannel.value = channel
        _seriesSeasons.value = emptyList()
        _seriesEpisodes.value = emptyMap()
        _seriesFetchError.value = null
        if (channel != null) {
            val playlist = _selectedPlaylist.value
            if (playlist != null && playlist.type == "XTREAM") {
                fetchSeriesInfo(playlist, channel.channelId)
            }
        }
    }

    fun selectSeasonNum(num: Int) {
        _selectedSeasonNum.value = num
    }

    private fun fetchSeriesInfo(playlist: Playlist, seriesId: String) {
        viewModelScope.launch {
            _isFetchingSeriesInfo.value = true
            _seriesFetchError.value = null
            try {
                val jsonStr = XtreamService.fetchSeriesInfo(
                    baseUrl = playlist.url,
                    username = playlist.username ?: "",
                    password = playlist.password ?: "",
                    seriesId = seriesId
                )
                if (jsonStr != null) {
                    val json = org.json.JSONObject(jsonStr)
                    
                    // Parse seasons
                    val seasonsList = mutableListOf<XtreamSeason>()
                    val seasonsArray = json.optJSONArray("seasons")
                    if (seasonsArray != null && seasonsArray.length() > 0) {
                        for (i in 0 until seasonsArray.length()) {
                            val obj = seasonsArray.getJSONObject(i)
                            val name = obj.optString("name", "Sezon ${obj.optInt("season_number")}")
                            val num = obj.optInt("season_number", i + 1)
                            seasonsList.add(XtreamSeason(name = name, seasonNumber = num))
                        }
                    } else {
                        // Sometimes seasons array can be empty but episodes has keys.
                        val episodesObj = json.optJSONObject("episodes")
                        if (episodesObj != null) {
                            val keys = episodesObj.keys()
                            while (keys.hasNext()) {
                                val keyStr = keys.next()
                                val num = keyStr.toIntOrNull() ?: 1
                                seasonsList.add(XtreamSeason(name = "Sezon $num", seasonNumber = num))
                            }
                        }
                    }
                    seasonsList.sortBy { it.seasonNumber }
                    _seriesSeasons.value = seasonsList

                    // Parse episodes
                    val epMap = mutableMapOf<Int, List<XtreamEpisode>>()
                    val episodesObj = json.optJSONObject("episodes")
                    if (episodesObj != null) {
                        val keys = episodesObj.keys()
                        while (keys.hasNext()) {
                            val keyStr = keys.next()
                            val seasonNum = keyStr.toIntOrNull() ?: 1
                            val epArray = episodesObj.optJSONArray(keyStr)
                            if (epArray != null) {
                                val listForSeason = mutableListOf<XtreamEpisode>()
                                val cleanUrl = playlist.url.trim().removeSuffix("/")
                                for (j in 0 until epArray.length()) {
                                    val epObj = epArray.getJSONObject(j)
                                    val id = epObj.optString("id")
                                    val title = epObj.optString("title", "Bölüm ${epObj.optInt("episode_num")}")
                                    val num = epObj.optInt("episode_num", j + 1)
                                    val ext = epObj.optString("container_extension", "mp4")
                                    val streamUrl = "$cleanUrl/series/${playlist.username}/${playlist.password}/$id.$ext"
                                    val icon = epObj.optString("stream_icon").takeIf { it.isNotEmpty() }
                                    listForSeason.add(
                                        XtreamEpisode(
                                            id = id,
                                            name = title,
                                            episodeNum = num,
                                            streamUrl = streamUrl,
                                            logoUrl = icon
                                        )
                                    )
                                }
                                listForSeason.sortBy { it.episodeNum }
                                epMap[seasonNum] = listForSeason
                            }
                        }
                    }
                    _seriesEpisodes.value = epMap
                    
                    // Set default season to first available
                    if (seasonsList.isNotEmpty()) {
                        _selectedSeasonNum.value = seasonsList.first().seasonNumber
                    } else {
                        _selectedSeasonNum.value = 1
                    }
                } else {
                    _seriesFetchError.value = "Sunucudan dizi bilgileri alınamadı."
                }
            } catch (e: Exception) {
                Log.e("IptvViewModel", "Error fetching series info: ${e.message}")
                _seriesFetchError.value = "Hata oluştu: ${e.message}"
            } finally {
                _isFetchingSeriesInfo.value = false
            }
        }
    }

    // Filtreleme, arama ve gruplara göre canlı güncellenen dinamik kanal akışı
    val channels: StateFlow<List<IptvChannel>> = combine(
        _selectedPlaylist,
        _selectedType,
        _selectedGroup,
        _searchQuery
    ) { playlist, type, group, query ->
        playlist to Triple(type, group, query)
    }.flatMapLatest { (playlist, filters) ->
        val playlistId = playlist?.id ?: return@flatMapLatest flowOf(emptyList())
        val (type, group, query) = filters

        when {
            query.isNotEmpty() -> {
                val dbType = if (type == "FAVORITE" || type == "RECENT") "LIVE" else type
                repository.searchChannels(playlistId, dbType, query)
            }
            type == "FAVORITE" -> repository.getFavorites(playlistId)
            type == "RECENT"   -> repository.getRecents(playlistId)
            group == null || group == "Hepsi" -> repository.getChannelsByType(playlistId, type)
            else -> repository.getChannelsByGroup(playlistId, type, group)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        _selectedGroup.value = "Hepsi"
        _searchQuery.value = ""
        sharedPrefs.edit().putLong("last_playlist_id", playlist?.id ?: -1L).apply()
    }

    fun selectType(type: String) {
        _selectedType.value = type
        _selectedGroup.value = "Hepsi"
        _searchQuery.value = ""
    }

    fun selectGroup(group: String?) {
        _selectedGroup.value = group
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAppMode(mode: String) {
        sharedPrefs.edit().putString("app_mode", mode).apply()
        _appMode.value = mode
    }

    fun resetAppMode() {
        sharedPrefs.edit().remove("app_mode").apply()
        _appMode.value = null
    }

    fun addPlaylist(name: String, type: String, url: String, user: String?, pass: String?) {
        viewModelScope.launch {
            _refreshState.value = RefreshState.Loading("Oynatma listesi kaydediliyor...")
            try {
                val playlist = Playlist(
                    name = name.trim(),
                    type = type,
                    url = url.trim(),
                    username = user?.trim(),
                    password = pass?.trim()
                )
                val id = repository.addPlaylist(playlist)
                _refreshState.value = RefreshState.Loading("Kanallar senkronize ediliyor...")
                repository.refreshPlaylist(id)
                _refreshState.value = RefreshState.Success
            } catch (e: Exception) {
                _refreshState.value = RefreshState.Error(e.message ?: "Kaynak bilgileri doğrulanırken hata oluştu.")
            }
        }
    }

    fun refreshActivePlaylist() {
        val playlist = _selectedPlaylist.value ?: return
        viewModelScope.launch {
            _refreshState.value = RefreshState.Loading("Veriler güncelleniyor...")
            try {
                repository.refreshPlaylist(playlist.id)
                _refreshState.value = RefreshState.Success
            } catch (e: Exception) {
                _refreshState.value = RefreshState.Error(e.message ?: "Güncelleme sırasında hata oluştu.")
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            try {
                if (_selectedPlaylist.value?.id == playlist.id) {
                    _selectedPlaylist.value = null
                }
                repository.deletePlaylist(playlist)
                val lastId = sharedPrefs.getLong("last_playlist_id", -1L)
                if (lastId == playlist.id) {
                    sharedPrefs.edit().remove("last_playlist_id").apply()
                }
            } catch (e: Exception) {
                Log.e("IptvViewModel", "Liste silinirken hata oluştu: ${e.message}")
            }
        }
    }

    fun clearRefreshState() {
        _refreshState.value = RefreshState.Idle
    }

    fun selectChannel(channel: IptvChannel?) {
        _activeChannel.value = channel
        if (channel != null) {
            viewModelScope.launch {
                repository.markAsWatched(channel.uniqueId)
            }
        }
    }

    fun toggleFavorite(channel: IptvChannel) {
        viewModelScope.launch {
            repository.toggleFavorite(channel.uniqueId, !channel.isFavorite)
            // Canlı izlenmekte olan oynatıcı bilgisini tazeleyelim
            if (_activeChannel.value?.uniqueId == channel.uniqueId) {
                _activeChannel.value = _activeChannel.value?.copy(isFavorite = !channel.isFavorite)
            }
        }
    }

    // Player sırasında zapping işlemleri
    fun zapNext() {
        val current = _activeChannel.value ?: return
        val currentList = channels.value
        val currentIndex = currentList.indexOfFirst { it.uniqueId == current.uniqueId }
        if (currentIndex != -1 && currentIndex < currentList.lastIndex) {
            selectChannel(currentList[currentIndex + 1])
        } else if (currentList.isNotEmpty()) {
            // Başa dön
            selectChannel(currentList[0])
        }
    }

    fun zapPrev() {
        val current = _activeChannel.value ?: return
        val currentList = channels.value
        val currentIndex = currentList.indexOfFirst { it.uniqueId == current.uniqueId }
        if (currentIndex > 0) {
            selectChannel(currentList[currentIndex - 1])
        } else if (currentList.isNotEmpty()) {
            // Sona git
            selectChannel(currentList.last())
        }
    }
}
