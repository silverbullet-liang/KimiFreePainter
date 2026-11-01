package com.kimifreepainter.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
        val title: String,
        val text: String,
        val source: String,
        val createTime: Long,
        val authorId: String,
        val authorType: String,
        /** 引用关系：成品帖会指向触发帖，是最可靠的配对判据 */
        val refMomentId: String,
        val images: List<Image>,
        val deleted: Boolean
    )

    /**
     * links 是机器人回复里「查看作品」那条超链接的真实地址。
     * 正文只存显示文本，URL 只在这里，从 text 里正则永远抓不到。
     */
    data class Comment(
        val id: String,
        val type: String,
        val aiGenerated: Boolean,
        val text: String,
        val links: List<String>
    )

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

    /**
     * 发触发帖。
     * 平台是按正文里的 @ 文本定位目标的：只发 mentions 字段不会让动态出现 @，
     * 机器人也就收不到。两者一起给，正文里的 @ 才是真正生效的那个。
     */
    suspend fun createMoment(
        text: String,
        mentionUserId: String,
        mentionName: String,
        publicVisible: Boolean = true,
        imageId: String = ""
    ): String {
        val mention = "@$mentionName"
        val withMention = if (mentionName.isNotEmpty() && !text.contains(mention)) {
            "$mention $text"
        } else {
            text
        }
        val body = JSONObject()
            .put("text", withMention)
            .put(
                "visibility",
                if (publicVisible) "MOMENT_VISIBILITY_PUBLIC" else "MOMENT_VISIBILITY_SELF"
            )
        // 拿不到机器人 userId 时只发正文 @：多一个空 id 反而可能被判参数非法
        if (mentionUserId.isNotEmpty()) {
            body.put(
                "mentions",
                JSONArray().put(
                    JSONObject().put("userId", mentionUserId).put("name", mentionName)
                )
            )
        }
        // 带图即改图：图片随动态发出，画师以它为底图重绘。平台一条动态只吃一张。
        if (imageId.isNotEmpty()) {
            body.put("images", JSONArray().put(JSONObject().put("fileId", imageId)))
        }
        val r = ApiClient.post("/moment.v1.MomentService/CreateMoment", body)
        return r.optJSONObject("moment")?.optString("id").orEmpty()
    }

    /**
     * 图片三步上传：申请预签名地址 → PUT 直传云存储 → 登记文件。
     * 发帖时 images 字段要的是登记后的 fileId，直传地址本身不参与发帖。
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        contentType: String,
        width: Int,
        height: Int
    ): String {
        val pre = ApiClient.post(
            "/moment.v1.UploadService/GeneratePresignedURL",
            JSONObject().put("fileType", "FILE_TYPE_IMAGE")
        )
        val uploadUrl = pre.optString("uploadUrl")
        val preFileId = pre.optString("fileId")
        if (uploadUrl.isEmpty() || preFileId.isEmpty()) throw ApiException(-1, "未获取到上传地址")

        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(uploadUrl)
                .put(bytes.toRequestBody((contentType.ifEmpty { "image/jpeg" }).toMediaType()))
                .build()
            ApiClient.http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw ApiException(resp.code, "图片直传失败")
            }
        }

        val made = ApiClient.post(
            "/moment.v1.UploadService/CreateFile",
            JSONObject()
                .put("fileName", fileName.ifEmpty { "image.jpg" })
                .put("fileId", preFileId)
                .put("extra", JSONObject().put("width", width).put("height", height))
        )
        return made.optString("fileId").ifEmpty { preFileId }
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
            val content = c.optJSONObject("content")
            val links = mutableListOf<String>()
            for (key in arrayOf("links", "linkList")) {
                val la = content?.optJSONArray(key) ?: c.optJSONArray(key) ?: continue
                for (j in 0 until la.length()) {
                    val v = la.opt(j)
                    val u = when (v) {
                        is String -> v
                        is JSONObject -> v.optString("url").ifEmpty {
                            v.optString("linkUrl").ifEmpty { v.optString("href") }
                        }
                        else -> ""
                    }
                    if (u.isNotEmpty()) links += u
                }
            }
            out += Comment(
                id = c.optString("id"),
                type = c.optString("type"),
                aiGenerated = c.optBoolean("aiGenerated", false),
                text = content?.optString("text").orEmpty(),
                links = links
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
            title = content?.optString("title").orEmpty(),
            text = content?.optString("text").orEmpty(),
            source = content?.optString("source").orEmpty(),
            refMomentId = m.optString("refMomentId").ifEmpty {
                content?.optString("refMomentId").orEmpty()
            },
            createTime = ts(m.optString("createTime")),
            authorId = author?.optString("userId").orEmpty(),
            authorType = author?.optString("type").orEmpty(),
            images = images,
            deleted = m.optBoolean("isDeleted", false)
        )
    }
}
