package com.podgir.app

import org.json.JSONArray
import org.json.JSONObject

data class Playlist(val id: String, val name: String, val episodes: List<Episode> = emptyList()) {
    fun json() = JSONObject().put("id", id).put("name", name).put("episodes", JSONArray(episodes.map { it.json() }))
    companion object {
        fun from(json: JSONObject): Playlist {
            val items = json.optJSONArray("episodes") ?: JSONArray()
            require(items.length() <= 2000)
            return Playlist(json.getString("id"), json.getString("name"), (0 until items.length()).map { Episode.from(items.getJSONObject(it)) }.distinctBy { it.audio })
        }
    }
}
