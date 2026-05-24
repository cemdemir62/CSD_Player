package com.example.data.parser

import com.example.data.model.IptvChannel
import java.io.BufferedReader
import java.io.StringReader

object M3uParser {
    fun parse(content: String, playlistId: Long): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val reader = BufferedReader(StringReader(content))
        var line: String? = reader.readLine()
        
        var currentMetadata: Metadata? = null
        var index = 0

        while (line != null) {
            line = line.trim()
            if (line.startsWith("#EXTINF:")) {
                currentMetadata = parseExtInf(line)
            } else if (line.isNotEmpty() && !line.startsWith("#")) {
                val metadata = currentMetadata ?: Metadata(name = "Kanal ${index + 1}")
                index++
                
                val url = line
                val name = metadata.name
                val logo = metadata.logoUrl
                val group = metadata.groupTitle?.trim()?.takeIf { it.isNotEmpty() } ?: "Kategorilenmemiş"
                
                // Grup adına veya uzantıya göre akıllı kategori ataması
                val type = when {
                    group.contains("sinema", ignoreCase = true) || 
                    group.contains("movie", ignoreCase = true) || 
                    group.contains("film", ignoreCase = true) ||
                    group.contains("vod", ignoreCase = true) -> "MOVIE"
                    
                    group.contains("series", ignoreCase = true) || 
                    group.contains("dizi", ignoreCase = true) || 
                    group.contains("season", ignoreCase = true) -> "SERIES"
                    
                    url.endsWith(".mp4") || url.endsWith(".mkv") || url.contains("/movie/") -> "MOVIE"
                    url.contains("/series/") -> "SERIES"
                    else -> "LIVE"
                }

                val id = metadata.tvgId?.takeIf { it.isNotEmpty() } ?: "ch_${index}"
                val uniqueId = "${playlistId}_${id}_${index}"

                channels.add(
                    IptvChannel(
                        uniqueId = uniqueId,
                        playlistId = playlistId,
                        channelId = id,
                        name = name,
                        streamUrl = url,
                        logoUrl = logo,
                        groupTitle = group,
                        type = type
                    )
                )
                currentMetadata = null
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun parseExtInf(line: String): Metadata {
        // Örnek satır: #EXTINF:-1 tvg-id="Canal1" tvg-name="Canal 1" tvg-logo="http://..." group-title="Sports",Canal 1 HD
        val logoReg = """tvg-logo="([^"]+)"""".toRegex()
        val groupReg = """group-title="([^"]+)"""".toRegex()
        val idReg = """tvg-id="([^"]+)"""".toRegex()

        val logoUrl = logoReg.find(line)?.groupValues?.get(1)
        val groupTitle = groupReg.find(line)?.groupValues?.get(1)
        val tvgId = idReg.find(line)?.groupValues?.get(1)

        val nameIndex = line.lastIndexOf(",")
        val name = if (nameIndex != -1 && nameIndex < line.length - 1) {
            line.substring(nameIndex + 1).trim()
        } else {
            "Bilinmeyen Kanal"
        }

        return Metadata(name, logoUrl, groupTitle, tvgId)
    }

    private data class Metadata(
        val name: String,
        val logoUrl: String? = null,
        val groupTitle: String? = null,
        val tvgId: String? = null
    )
}
