package com.github.avbox.osc.ui.player

import com.github.avbox.osc.bean.VodInfo
import com.github.avbox.osc.haikuo.HaikuoBridge
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.haikuo.HaikuoPlayLine
import com.github.avbox.osc.haikuo.HaikuoPlayRequest
import com.github.avbox.osc.haikuo.haikuoHashHex
import com.github.avbox.osc.player.PlaybackSession
import com.github.avbox.osc.sourcedata.PushUrlParser
import java.net.URLEncoder
import org.json.JSONObject

fun haikuoPushUrl(url: String, headers: Map<String, String>): String {
    if (headers.isEmpty()) return url
    val json = JSONObject()
    for ((key, value) in headers) {
        json.put(key, value)
    }
    return url + PushUrlParser.PUSH_HEADERS_MARKER +
        URLEncoder.encode(json.toString(), "UTF-8") + "@"
}

fun haikuoVodInfo(request: HaikuoPlayRequest, ruleTitle: String): VodInfo {
    val lines = if (request.lines.isEmpty()) {
        listOf(HaikuoPlayLine(request.url, "线路1", request.headers))
    } else {
        request.lines
    }
    val episodeTitle = request.episodes.getOrNull(request.index)?.let { episode ->
        HaikuoJson.str(episode["name"]).ifEmpty { HaikuoJson.str(episode["title"]) }
    }.orEmpty().ifEmpty { request.title }
    val seriesMap = LinkedHashMap<String?, MutableList<VodInfo.VodSeries>>()
    val flags = ArrayList<VodInfo.VodSeriesFlag>()
    for (line in lines) {
        flags.add(VodInfo.VodSeriesFlag(line.name))
        seriesMap[line.name] = mutableListOf(
            VodInfo.VodSeries(
                episodeTitle,
                haikuoPushUrl(line.url, line.headers)
            )
        )
    }
    val rawId = ruleTitle + "\n" + request.title + "\n" + request.url
    val id = "haikuo:" + haikuoHashHex("md5", rawId.toByteArray(Charsets.UTF_8))
    return VodInfo(
        id = id,
        name = request.title.ifEmpty { request.series.ifEmpty { ruleTitle } },
        seriesFlags = flags,
        seriesMap = seriesMap,
        playFlag = lines[0].name,
        playIndex = 0,
        sourceKey = PushUrlParser.PUSH_FALLBACK,
        sourceName = ruleTitle
    )
}

fun haikuoPlaybackSession(request: HaikuoPlayRequest, ruleTitle: String): PlaybackSession {
    return PlaybackSession(
        haikuoVodInfo(request, ruleTitle),
        PushUrlParser.PUSH_FALLBACK,
        false
    )
}

fun haikuoPlayRequestToJson(request: HaikuoPlayRequest): String {
    val map = LinkedHashMap<String, Any?>()
    map["url"] = request.url
    map["title"] = request.title
    map["series"] = request.series
    map["subtitle"] = request.subtitle
    map["lyric"] = request.lyric
    map["audioUrls"] = request.audioUrls
    map["headers"] = request.headers
    map["episodes"] = request.episodes
    map["lines"] = request.lines.map { line ->
        mapOf(
            "url" to line.url,
            "name" to line.name,
            "headers" to line.headers
        )
    }
    map["danmaku"] = request.danmaku
    map["index"] = request.index
    map["detail"] = request.detail
    return HaikuoJson.stringify(map)
}

fun haikuoPlayRequestFromJson(json: String): HaikuoPlayRequest? {
    val decoded = HaikuoJson.parse(json)
    if (decoded !is Map<*, *>) return null
    val payload = LinkedHashMap<String, Any?>()
    for ((key, value) in decoded) {
        payload[key.toString()] = value
    }
    val request = HaikuoBridge.parsePlayRequest(payload)
    if (request.url.isEmpty()) return null
    return request
}
