package com.example.data.local

import androidx.room.*
import com.example.data.model.IptvChannel
import com.example.data.model.Playlist
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {
    // Playlist İşlemleri
    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistById(playlistId: Long): Playlist?

    // Kanal İşlemleri
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<IptvChannel>)

    @Query("DELETE FROM iptv_channels WHERE playlistId = :playlistId")
    suspend fun deleteChannelsByPlaylist(playlistId: Long)

    @Query("SELECT * FROM iptv_channels WHERE playlistId = :playlistId AND type = :type ORDER BY name ASC")
    fun getChannelsByType(playlistId: Long, type: String): Flow<List<IptvChannel>>

    @Query("SELECT * FROM iptv_channels WHERE playlistId = :playlistId AND type = :type AND groupTitle = :groupTitle ORDER BY name ASC")
    fun getChannelsByGroup(playlistId: Long, type: String, groupTitle: String): Flow<List<IptvChannel>>

    @Query("SELECT DISTINCT groupTitle FROM iptv_channels WHERE playlistId = :playlistId AND type = :type AND groupTitle IS NOT NULL AND groupTitle != '' ORDER BY groupTitle ASC")
    fun getGroupsByType(playlistId: Long, type: String): Flow<List<String>>

    @Query("SELECT * FROM iptv_channels WHERE playlistId = :playlistId AND isFavorite = 1 ORDER BY name ASC")
    fun getFavorites(playlistId: Long): Flow<List<IptvChannel>>

    @Query("SELECT * FROM iptv_channels WHERE playlistId = :playlistId AND isRecent = 1 ORDER BY lastWatchedTimestamp DESC LIMIT 30")
    fun getRecents(playlistId: Long): Flow<List<IptvChannel>>

    @Query("SELECT * FROM iptv_channels WHERE playlistId = :playlistId AND type = :type AND name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchChannels(playlistId: Long, type: String, query: String): Flow<List<IptvChannel>>

    @Query("UPDATE iptv_channels SET isFavorite = :isFav WHERE uniqueId = :uniqueId")
    suspend fun updateFavorite(uniqueId: String, isFav: Boolean)

    @Query("UPDATE iptv_channels SET isRecent = 1, lastWatchedTimestamp = :timestamp WHERE uniqueId = :uniqueId")
    suspend fun markAsWatched(uniqueId: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM iptv_channels WHERE playlistId = :playlistId")
    suspend fun getChannelCount(playlistId: Long): Int

    @Query("SELECT * FROM iptv_channels WHERE uniqueId IN (:ids)")
    suspend fun getChannelsByUniqueIds(ids: List<String>): List<IptvChannel>

    @Query("SELECT * FROM iptv_channels WHERE uniqueId IN (:ids)")
    fun getChannelsByUniqueIdsFlow(ids: List<String>): Flow<List<IptvChannel>>
}
