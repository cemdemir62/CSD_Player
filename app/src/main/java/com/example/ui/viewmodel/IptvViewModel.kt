package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import com.example.data.model.UserProfile
import com.example.data.repository.IptvRepository
import com.example.data.model.XtreamSeason
import com.example.data.model.XtreamEpisode
import com.example.data.network.XtreamService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface RefreshState {
    object Idle : RefreshState
    data class Loading(val message: String) : RefreshState
    object Success : RefreshState
    data class Error(val message: String) : RefreshState
}

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Synced(val lastSyncTime: Long) : SyncState
    data class Error(val message: String, val lastSyncTime: Long?) : SyncState
}

@OptIn(ExperimentalCoroutinesApi::class)
class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val sharedPrefs = application.getSharedPreferences("zula_iptv_prefs", Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(application)
    private val repository = IptvRepository(database.iptvDao())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState = _syncState.asStateFlow()

    // 1. Profil Yönetim Yapıları
    private val _profiles = MutableStateFlow<List<UserProfile>>(emptyList())
    val profiles = _profiles.asStateFlow()

    private val _selectedProfile = MutableStateFlow<UserProfile?>(null)
    val selectedProfile = _selectedProfile.asStateFlow()

    private val _profileFavorites = MutableStateFlow<Set<String>>(emptySet())
    val profileFavorites = _profileFavorites.asStateFlow()

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    private val _continueWatchingData = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val continueWatchingData = _continueWatchingData.asStateFlow()

    val continueWatchingChannels: StateFlow<List<IptvChannel>> = combine(_continueWatchingData, _selectedPlaylist) { cwMap, playlist ->
        cwMap to playlist
    }.flatMapLatest { (cwMap, playlist) ->
        val playlistId = playlist?.id ?: return@flatMapLatest flowOf(emptyList<IptvChannel>())
        val ids = cwMap.keys.toList()
        if (ids.isEmpty()) return@flatMapLatest flowOf(emptyList())
        
        repository.getChannelsByUniqueIdsFlow(ids).map { list ->
            list.filter { it.playlistId == playlistId }
                .sortedBy { ids.indexOf(it.uniqueId) }
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<Playlist>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Uygulama Modu: Daima "TV" (Sadece TV desteği, Mobil kaldırıldı)
    private val _appMode = MutableStateFlow<String?>("TV")
    val appMode: StateFlow<String?> = _appMode.asStateFlow()

    private var hasAutoLoggedIn = false

    init {
        loadProfilesFromPrefs()
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
                        selectPlaylist(toSelect)
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
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    // Jet Hızı: XTREAM Dizi detayları ve bölümlerini bellekte önbelleğe alıyoruz (tekrar yükleme sıfır gecikme)
    private val seriesCache = java.util.concurrent.ConcurrentHashMap<String, Pair<List<XtreamSeason>, Map<Int, List<XtreamEpisode>>>>()

    fun selectSeries(channel: IptvChannel?) {
        _activeSeriesChannel.value = channel
        _seriesSeasons.value = emptyList()
        _seriesEpisodes.value = emptyMap()
        _seriesFetchError.value = null
        if (channel != null) {
            val playlist = _selectedPlaylist.value
            if (playlist != null && playlist.type == "XTREAM") {
                fetchSeriesInfo(playlist, channel)
            }
        }
    }

    fun selectSeasonNum(num: Int) {
        _selectedSeasonNum.value = num
    }

    private fun fetchSeriesInfo(playlist: Playlist, channel: IptvChannel) {
        val seriesId = channel.channelId
        val cacheKey = "${playlist.id}_$seriesId"
        viewModelScope.launch {
            _isFetchingSeriesInfo.value = true
            _seriesFetchError.value = null
            
            // Jet Hızı Önbellek Sorgulama: Daha önce çekilmişse direkt yükle, bekletme!
            val cached = seriesCache[cacheKey]
            if (cached != null) {
                _seriesSeasons.value = cached.first
                _seriesEpisodes.value = cached.second
                
                val savedSeasonNum = sharedPrefs.getInt("series_${channel.uniqueId}_last_season", -1)
                if (savedSeasonNum != -1 && cached.first.any { it.seasonNumber == savedSeasonNum }) {
                    _selectedSeasonNum.value = savedSeasonNum
                } else if (cached.first.isNotEmpty()) {
                    _selectedSeasonNum.value = cached.first.first().seasonNumber
                } else {
                    _selectedSeasonNum.value = 1
                }
                _isFetchingSeriesInfo.value = false
                return@launch
            }

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
                    
                    // Jet Hızı Önbelleğe Kaydet:
                    seriesCache[cacheKey] = Pair(seasonsList, epMap)
                    
                    // Set default season to last watched season or first available
                    val savedSeasonNum = sharedPrefs.getInt("series_${channel.uniqueId}_last_season", -1)
                    if (savedSeasonNum != -1 && seasonsList.any { it.seasonNumber == savedSeasonNum }) {
                        _selectedSeasonNum.value = savedSeasonNum
                    } else if (seasonsList.isNotEmpty()) {
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
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val channels: StateFlow<List<IptvChannel>> = combine(
        _selectedPlaylist,
        _selectedType,
        _selectedGroup,
        _searchQuery.debounce { if (it.isEmpty()) 0L else 250L }
    ) { playlist, type, group, query ->
        Triple(playlist, type, group) to query
    }.flatMapLatest { (triple, query) ->
        val (playlist, type, group) = triple
        val playlistId = playlist?.id ?: return@flatMapLatest flowOf(emptyList<IptvChannel>())
        
        val flow = when {
            query.isNotEmpty() -> {
                val dbType = if (type == "FAVORITE" || type == "RECENT") "LIVE" else type
                repository.searchChannels(playlistId, dbType, query)
            }
            type == "FAVORITE" -> {
                val favSet = _profileFavorites.value
                if (favSet.isNotEmpty()) {
                    repository.getChannelsByUniqueIdsFlow(favSet.toList())
                } else {
                    flowOf(emptyList())
                }
            }
            type == "RECENT" -> repository.getRecents(playlistId)
            group == null || group == "Hepsi" -> repository.getChannelsByType(playlistId, type)
            else -> repository.getChannelsByGroup(playlistId, type, group)
        }

        combine(flow, _profileFavorites, _selectedProfile) { list, favSet, profile ->
            var resultList = list
            if (profile?.isKids == true) {
                resultList = list.filter { chan ->
                    val nameLower = chan.name.lowercase()
                    val groupLower = (chan.groupTitle ?: "").lowercase()
                    val isAdult = groupLower.contains("18+") || groupLower.contains("adult") || groupLower.contains("xx") || groupLower.contains("porn") || nameLower.contains("18+") || nameLower.contains("porn")
                    !isAdult
                }
            }
            resultList.map { chan ->
                chan.copy(isFavorite = favSet.contains(chan.uniqueId))
            }
        }
    }.flowOn(Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectPlaylist(playlist: Playlist?) {
        _selectedPlaylist.value = playlist
        _selectedGroup.value = "Hepsi"
        _searchQuery.value = ""
        sharedPrefs.edit().putLong("last_playlist_id", playlist?.id ?: -1L).apply()
        if (playlist != null) {
            triggerSmartSync(playlist)
        } else {
            _syncState.value = SyncState.Idle
        }
    }

    fun triggerSmartSync(playlist: Playlist) {
        viewModelScope.launch {
            val count = repository.getChannelCount(playlist.id)
            val lastSync = sharedPrefs.getLong("playlist_sync_${playlist.id}", 0L)
            val now = System.currentTimeMillis()
            
            if (count > 0) {
                // Jet hızında açılış: Cache zaten veritabanında var, kullanıcıyı bekletmiyoruz!
                _syncState.value = SyncState.Synced(lastSync)
                
                // Güvenli ve Akıllı Arka Plan Senkronizasyonu (Farklı Gün Kontrolü)
                val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastSync }
                val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
                val isDifferentDay = lastSync == 0L || 
                        lastCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR) ||
                        lastCal.get(java.util.Calendar.DAY_OF_YEAR) != nowCal.get(java.util.Calendar.DAY_OF_YEAR)
                
                // Aynı günde isek ve son senkronizasyon üzerinden 30 dakikadan az geçmişse yeni senkronizasyonu atlayalım
                if (!isDifferentDay && (now - lastSync < 30 * 60 * 1000)) {
                    Log.d("IptvViewModel", "Aynı gün ve son senkronizasyon 30 dakikadan kısa süre önce yapılmış. Arka plan senkronizasyonu atlanıyor.")
                    return@launch
                }
                
                // Arka planda sessizce cihaz verilerini güncelleyelim (Smart Sync)
                _syncState.value = SyncState.Syncing
                try {
                    Log.d("IptvViewModel", "Akıllı arka plan güncellemesi başlatılıyor... Farklı gün mü? $isDifferentDay")
                    repository.refreshPlaylist(getApplication(), playlist.id)
                    sharedPrefs.edit().putLong("playlist_sync_${playlist.id}", now).apply()
                    _syncState.value = SyncState.Synced(now)
                } catch (e: Exception) {
                    Log.e("IptvViewModel", "Silent background sync error: ${e.message}")
                    _syncState.value = SyncState.Error(e.message ?: "Bağlantı Hatası", lastSync)
                }
            } else {
                // Yerel veritabanında hiç kanal yok: Kullanıcıya ilk yükleme göstergesi sunuyoruz.
                _syncState.value = SyncState.Syncing
                _refreshState.value = RefreshState.Loading("İlk kurulum senkronizasyonu yapılıyor...")
                try {
                    repository.refreshPlaylist(getApplication(), playlist.id)
                    sharedPrefs.edit().putLong("playlist_sync_${playlist.id}", now).apply()
                    _syncState.value = SyncState.Synced(now)
                    _refreshState.value = RefreshState.Success
                } catch (e: Exception) {
                    Log.e("IptvViewModel", "First-time sync error: ${e.message}")
                    _syncState.value = SyncState.Error(e.message ?: "Bağlantı Hatası", null)
                    _refreshState.value = RefreshState.Error(e.message ?: "Çalma listesi ilk senkronizasyonu başarısız oldu.")
                }
            }
        }
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
        sharedPrefs.edit().putString("app_mode", "TV").apply()
        _appMode.value = "TV"
    }

    fun resetAppMode() {
        // App is strictly TV-only, resetting to null is disabled to prevent going back to selection screens
        sharedPrefs.edit().putString("app_mode", "TV").apply()
        _appMode.value = "TV"
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
                repository.refreshPlaylist(getApplication(), id)
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
            _syncState.value = SyncState.Syncing
            val now = System.currentTimeMillis()
            try {
                repository.refreshPlaylist(getApplication(), playlist.id)
                sharedPrefs.edit().putLong("playlist_sync_${playlist.id}", now).apply()
                _syncState.value = SyncState.Synced(now)
                _refreshState.value = RefreshState.Success
            } catch (e: Exception) {
                val lastSync = sharedPrefs.getLong("playlist_sync_${playlist.id}", 0L)
                val lastSyncVal = if (lastSync > 0L) lastSync else null
                _syncState.value = SyncState.Error(e.message ?: "Güncelleme sırasında hata oluştu.", lastSyncVal)
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
        val profile = _selectedProfile.value
        if (profile != null) {
            val currentSet = sharedPrefs.getStringSet("profile_${profile.id}_favorites", emptySet()) ?: emptySet()
            val newSet = currentSet.toMutableSet()
            if (newSet.contains(channel.uniqueId)) {
                newSet.remove(channel.uniqueId)
            } else {
                newSet.add(channel.uniqueId)
            }
            sharedPrefs.edit().putStringSet("profile_${profile.id}_favorites", newSet).apply()
            _profileFavorites.value = newSet
            
            viewModelScope.launch {
                repository.toggleFavorite(channel.uniqueId, newSet.contains(channel.uniqueId))
            }
        } else {
            viewModelScope.launch {
                repository.toggleFavorite(channel.uniqueId, !channel.isFavorite)
            }
        }
        // Canlı izlenmekte olan oynatıcı bilgisini tazeleyelim
        if (_activeChannel.value?.uniqueId == channel.uniqueId) {
            _activeChannel.value = _activeChannel.value?.copy(isFavorite = !channel.isFavorite)
        }
    }

    // --- PROFIL YÖNETIMI VE YARIM KALANLARA DEVAM ET YARDIMCI METOTLARI ---
    
    private fun loadProfilesFromPrefs() {
        val jsonStr = sharedPrefs.getString("user_profiles_json", null)
        if (jsonStr != null) {
            try {
                val list = mutableListOf<UserProfile>()
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        UserProfile(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            avatarColor = obj.getString("avatarColor"),
                            avatarEmoji = obj.getString("avatarEmoji"),
                            isKids = obj.optBoolean("isKids", false)
                        )
                    )
                }
                _profiles.value = list
            } catch (e: Exception) {
                Log.e("IptvViewModel", "Error loading profiles: ${e.message}")
                initializeDefaultProfiles()
            }
        } else {
            initializeDefaultProfiles()
        }
        
        // Auto-select last profiles if any
        val lastPid = sharedPrefs.getString("last_active_profile_id", null)
        if (lastPid != null) {
            val found = _profiles.value.find { it.id == lastPid }
            if (found != null) {
                selectProfile(found)
            }
        }
    }

    private fun initializeDefaultProfiles() {
        val defaults = listOf(
            UserProfile("p1", "CSD Premium", "#E50914", "🍿", false),
            UserProfile("p2", "Yetişkin", "#8E24AA", "👑", false),
            UserProfile("p3", "Çocuk Modu", "#00897B", "🦖", true)
        )
        saveProfilesToPrefs(defaults)
    }

    private fun saveProfilesToPrefs(list: List<UserProfile>) {
        _profiles.value = list
        try {
            val arr = org.json.JSONArray()
            for (p in list) {
                val obj = org.json.JSONObject()
                obj.put("id", p.id)
                obj.put("name", p.name)
                obj.put("avatarColor", p.avatarColor)
                obj.put("avatarEmoji", p.avatarEmoji)
                obj.put("isKids", p.isKids)
                arr.put(obj)
            }
            sharedPrefs.edit().putString("user_profiles_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("IptvViewModel", "Error saving profiles: ${e.message}")
        }
    }

    fun selectProfile(profile: UserProfile?) {
        _selectedProfile.value = profile
        if (profile != null) {
            sharedPrefs.edit().putString("last_active_profile_id", profile.id).apply()
            
            // Favorileri yükle
            val favs = sharedPrefs.getStringSet("profile_${profile.id}_favorites", emptySet()) ?: emptySet()
            _profileFavorites.value = favs
            
            // Yarım Kalanları yükle
            loadContinueWatchingForProfile(profile.id)
        } else {
            sharedPrefs.edit().remove("last_active_profile_id").apply()
            _profileFavorites.value = emptySet()
            _continueWatchingData.value = emptyMap()
        }
    }

    fun addProfile(name: String, color: String, emoji: String, isKids: Boolean) {
        val newProfile = UserProfile(
            id = "p_" + System.currentTimeMillis(),
            name = name,
            avatarColor = color,
            avatarEmoji = emoji,
            isKids = isKids
        )
        val current = _profiles.value.toMutableList()
        current.add(newProfile)
        saveProfilesToPrefs(current)
    }

    fun deleteProfile(profile: UserProfile) {
        val current = _profiles.value.toMutableList()
        current.removeAll { it.id == profile.id }
        if (current.isEmpty()) {
            val defaults = listOf(
                UserProfile("p1", "CSD Premium", "#E50914", "🍿", false)
            )
            saveProfilesToPrefs(defaults)
        } else {
            saveProfilesToPrefs(current)
        }
        if (_selectedProfile.value?.id == profile.id) {
            selectProfile(null)
        }
    }

    private fun loadContinueWatchingForProfile(profileId: String) {
        val jsonStr = sharedPrefs.getString("profile_${profileId}_continue_watching_json", null)
        val dataMap = mutableMapOf<String, Pair<Long, Long>>()
        if (jsonStr != null) {
            try {
                val arr = org.json.JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val uid = obj.getString("uid")
                    val pos = obj.getLong("pos")
                    val dur = obj.getLong("dur")
                    dataMap[uid] = Pair(pos, dur)
                }
            } catch (e: Exception) {
                Log.e("IptvViewModel", "Error loading continue watching: ${e.message}")
            }
        }
        _continueWatchingData.value = dataMap
    }

    fun updatePlaybackProgress(channel: IptvChannel, position: Long, duration: Long) {
        val profile = _selectedProfile.value ?: return
        val profileId = profile.id
        
        val savedPosKey = "resume_pos_profile_${profileId}_${channel.uniqueId}"
        sharedPrefs.edit().putLong(savedPosKey, position).apply()
        
        val currentList = sharedPrefs.getString("profile_${profileId}_continue_watching_json", null)
        val itemsList = mutableListOf<org.json.JSONObject>()
        
        try {
            if (currentList != null) {
                val arr = org.json.JSONArray(currentList)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    if (obj.getString("uid") != channel.uniqueId) {
                        itemsList.add(obj)
                    }
                }
            }
        } catch (e: Exception) {}
        
        val newObj = org.json.JSONObject()
        newObj.put("uid", channel.uniqueId)
        newObj.put("pos", position)
        newObj.put("dur", duration)
        newObj.put("ts", System.currentTimeMillis())
        itemsList.add(0, newObj)
        
        val cappedList = itemsList.take(20)
        
        val newArr = org.json.JSONArray()
        val dataMap = mutableMapOf<String, Pair<Long, Long>>()
        for (item in cappedList) {
            newArr.put(item)
            dataMap[item.getString("uid")] = Pair(item.getLong("pos"), item.getLong("dur"))
        }
        
        sharedPrefs.edit().putString("profile_${profileId}_continue_watching_json", newArr.toString()).apply()
        _continueWatchingData.value = dataMap
    }

    fun removeFromContinueWatching(channelUniqueId: String) {
        val profile = _selectedProfile.value ?: return
        val profileId = profile.id
        
        val currentList = sharedPrefs.getString("profile_${profileId}_continue_watching_json", null) ?: return
        val itemsList = mutableListOf<org.json.JSONObject>()
        try {
            val arr = org.json.JSONArray(currentList)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.getString("uid") != channelUniqueId) {
                    itemsList.add(obj)
                }
            }
            
            val newArr = org.json.JSONArray()
            val dataMap = mutableMapOf<String, Pair<Long, Long>>()
            for (item in itemsList) {
                newArr.put(item)
                dataMap[item.getString("uid")] = Pair(item.getLong("pos"), item.getLong("dur"))
            }
            sharedPrefs.edit().putString("profile_${profileId}_continue_watching_json", newArr.toString()).apply()
            
            val savedPosKey = "resume_pos_profile_${profileId}_$channelUniqueId"
            sharedPrefs.edit().remove(savedPosKey).apply()
            
            _continueWatchingData.value = dataMap
        } catch (e: Exception) {}
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
