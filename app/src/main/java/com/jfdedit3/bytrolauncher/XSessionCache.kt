package com.jfdedit3.bytrolauncher

import org.json.JSONObject

/** Only the last explicitly extracted post is shared with the local API. Never cookies. */
object XSessionCache {
    @Volatile private var lastJson: String? = null

    fun save(json: String) {
        val objectValue = JSONObject(json)
        require(objectValue.optString("type") == "post")
        lastJson = objectValue.toString()
    }

    fun latest(): String? = lastJson

    fun clear() { lastJson = null }
}
