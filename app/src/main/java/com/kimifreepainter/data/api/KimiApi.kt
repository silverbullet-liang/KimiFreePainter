package com.kimifreepainter.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/** moment.v1.* 社区接口。字段名与 proto JSON 一致（camelCase）。 */
object KimiApi {

    data class Bot(val userId: String, val name: String, val desc: String)

    data class Image(val originUrl: String, val downloadUrl: String, val width: Int, val height: Int) {
        val best: String get() = originUrl.ifEmpty { downloadUrl }
    }

    data class Moment(
        val id: String,
        val text: String,
        val source: String,
        val createTime: Long,
        val authorId: String,
        val authorType: String,
        val images: List<Image>,
        val deleted: Boolean
    )

    data class Comment(val id: String, val type: String, val aiGenerated: Boolean, val text: String)

    private fun arr(o: JSONObject?, key: String): JSONArray = o?.optJSONArray(key) ?: JSONArray()

    @Suppress("unused")
    private fun ts(s: String?): Long = s?.trim()?.toLongOrNull()
        ?: s?.let { runCatching { java.time.Instant.parse(it).epochSecond }.getOrNull() } ?: 0L

    /** 列机器人，用于解析出可 @ 的画师 userId。 */
    suspend fun listBots(scene: String = "MENTIONABLE_SCENE_TYPE_AGENT"): List<Bot> {
        val r = ApiClient.post(
            "/moment.v1.UserService/ListMentionableBots",
            JSONObject().put("sceneType", scene)
        )
        val out = mutableListOf<Bot>()
        val bots = arr(r, "bots")
        for (i in 0 until bots.length()) {
            val b = bots.optJSONObject(i) ?: continue
            out += Bot(
                userId = b.optString("userId"),
                name = b.optString("name"),
                desc = b.optString("description")
            )
        }
        return out
    }

    /** 发触发帖。@ 通过 mentions 表达，不写进正文。 */
    suspend fun createMoment(
        text: String,
        mentionUserId: String,
        mentionName: String,
        publicVisible: Boolean = true
    ): String {
        val body = JSONObject()
            .put("text", text)
            .put(
                "visibility",
                if (publicVisible) "MOMENT_VISIBILITY_PUBLIC" else "MOMENT_VISIBILITY_SELF"
            )
            .put(
                "mentions",
                JSONArray().put(
                    JSONObject().put("userId", mentionUserId).put("name", mentionName)
                )
            )
        val r = ApiClient.post("/moment.v1.MomentService/CreateMoment", body)
        return r.optJSONObject("moment")?.optString("id").orEmpty()
    }

    suspend fun deleteMoment(momentId: String) {
        ApiClient.post(
            "/moment.v1.MomentService/DeleteMoment",
            JSONObject().put("momentId", momentId)
        )
    }

    /** 我发布的动态。pageToken 传上次的 nextPageToken。 */
    suspend fun listMyFeeds(pageSize: Int = 10, pageToken: String = ""): Pair<List<Moment>, String> {
        val body = JSONObject()
            .put("category", "FEED_CATEGORY_USER_PUBLISH")
            .put("pageSize", pageSize)
        if (pageToken.isNotEmpty()) body.put("pageToken", pageToken)
        val r = ApiClient.post("/moment.v1.FeedService/ListFeeds", body)
        val list = mutableListOf<Moment>()
        val feeds = arr(r, "feeds")
        for (i in 0 until feeds.length()) {
            val f = feeds.optJSONObject(i) ?: continue
            f.optJSONObject("moment")?.let { parseMoment(it) }?.let { list += it }
        }
        return list to r.optString("nextPageToken")
    }

    suspend fun getMoment(momentId: String): Moment? {
        val r = ApiClient.post(
            "/moment.v1.FeedService/GetFeed",
            JSONObject().put("feedType", "FEED_TYPE_MOMENT").put("feedId", momentId)
        )
        return r.optJSONObject("feed")?.optJSONObject("moment")?.let { parseMoment(it) }
    }

    suspend fun listComments(momentId: String, pageSize: Int = 20): List<Comment> {
        val r = ApiClient.post(
            "/moment.v1.CommentService/ListComments",
            JSONObject().put("momentId", momentId).put("pageSize", pageSize)
        )
        val out = mutableListOf<Comment>()
        val cs = arr(r, "comments")
        for (i in 0 until cs.length()) {
            val c = cs.optJSONObject(i) ?: continue
            out += Comment(
                id = c.optString("id"),
                type = c.optString("type"),
                aiGenerated = c.optBoolean("aiGenerated", false),
                text = c.optJSONObject("content")?.optString("text").orEmpty()
            )
        }
        return out
    }

    suspend fun me(): JSONObject = ApiClient.post("/moment.v1.UserService/GetMe", JSONObject().put("view", "VIEW_DEFAULT"))

    /** 下载图片字节，不做任何转码。 */
    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
            .header("Referer", "https://www.kimi.com/")
            .build()
        ApiClient.http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "download failed")
            resp.body?.bytes() ?: throw ApiException(-1, "empty body")
        }
    }

    private fun parseMoment(m: JSONObject): Moment {
        val content = m.optJSONObject("content")
        val author = m.optJSONObject("author")
        val images = mutableListOf<Image>()
        val imgs = content?.optJSONObject("media")?.optJSONArray("images")
        if (imgs != null) {
            for (i in 0 until imgs.length()) {
                val im = imgs.optJSONObject(i) ?: continue
                val extra = im.optJSONObject("extra")
                images += Image(
                    originUrl = im.optString("originUrl"),
                    downloadUrl = im.optString("downloadUrl"),
                    width = extra?.optInt("width", 0) ?: 0,
                    height = extra?.optInt("height", 0) ?: 0
                )
            }
        }
        return Moment(
            id = m.optString("id"),
            text = content?.optString("text").orEmpty(),
            source = content?.optString("source").orEmpty(),
            createTime = ts(m.optString("createTime")),
            authorId = author?.optString("userId").orEmpty(),
            authorType = author?.optString("type").orEmpty(),
            images = images,
            deleted = m.optBoolean("isDeleted", false)
        )
    }
}
