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
        /** content.excerpt：平台给出的摘要 */
        val excerpt: String,
        /** coverImage：图文动态在列表层往往只给这个，取不到 media 时靠它认得出有图 */
        val cover: String,
        /** moment.hotComment 里的链接，机器人回复常在这里 */
        val hotCommentLinks: List<String>,
        val deleted: Boolean
    ) {
        /** 这条帖子有没有图：正文配图或封面图任一有即可 */
        val hasImage: Boolean get() = images.isNotEmpty() || cover.isNotEmpty()
    }

    /** 上传结果，字段与 proto 的 ImageInfo 对齐。 */
    data class UploadedImage(
        val fileId: String,
        val originUrl: String,
        val thumbnailUrl: String,
        val width: Int,
        val height: Int
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
        image: UploadedImage? = null
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
        // images 的元素是 ImageInfo，只给 fileId 不算完整对象。
        if (image != null && image.fileId.isNotEmpty()) {
            val o = JSONObject().put("fileId", image.fileId)
            if (image.originUrl.isNotEmpty()) o.put("originUrl", image.originUrl)
            if (image.thumbnailUrl.isNotEmpty()) o.put("thumbnailUrl", image.thumbnailUrl)
            if (image.width > 0 && image.height > 0) {
                o.put("extra", JSONObject().put("width", image.width).put("height", image.height))
            }
            body.put("images", JSONArray().put(o))
        }
        val r = ApiClient.post("/moment.v1.MomentService/CreateMoment", body)
        return r.optJSONObject("moment")?.optString("id").orEmpty()
    }

    /**
     * 图片三步上传：GeneratePresignedURL 拿直传地址 → PUT 上传 → CreateFile 登记。
     *
     * 两个易错点：
     * 1. 预签名 URL 若把 Content-Type 纳入签名，带了不匹配的头会 403，
     *    所以先不带头上传，失败再带上图片 MIME 重试一次。
     * 2. 发帖要的是 CreateFile 返回的 fileId，不是预签名那一次给的。
     */
    suspend fun uploadImage(
        bytes: ByteArray,
        fileName: String,
        contentType: String,
        width: Int,
        height: Int
    ): UploadedImage {
        val pre = ApiClient.post(
            "/moment.v1.UploadService/GeneratePresignedURL",
            JSONObject().put("fileType", "FILE_TYPE_IMAGE")
        )
        val uploadUrl = pre.optString("uploadUrl")
        val preFileId = pre.optString("fileId")
        if (uploadUrl.isEmpty() || preFileId.isEmpty()) throw ApiException(-1, "未获取到上传地址")

        val ok = putObject(uploadUrl, bytes, null) ||
            putObject(uploadUrl, bytes, contentType.ifEmpty { "image/jpeg" })
        if (!ok) throw ApiException(-1, "图片上传被云存储拒绝")

        val made = ApiClient.post(
            "/moment.v1.UploadService/CreateFile",
            JSONObject()
                .put("fileName", fileName.ifEmpty { "image.jpg" })
                .put("fileId", preFileId)
                .put("extra", JSONObject().put("width", width).put("height", height))
        )
        return UploadedImage(
            fileId = made.optString("fileId").ifEmpty { preFileId },
            originUrl = made.optString("originUrl"),
            thumbnailUrl = made.optString("thumbnailUrl"),
            width = width,
            height = height
        )
    }

    /** PUT 到预签名地址。mediaType 传 null 时不带 Content-Type。 */
    private suspend fun putObject(url: String, bytes: ByteArray, mediaType: String?): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = bytes.toRequestBody(mediaType?.toMediaType())
                val req = Request.Builder().url(url).put(body).build()
                ApiClient.http.newCall(req).execute().use { it.isSuccessful }
            }.getOrDefault(false)
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

    /** 图片元素可能是 {url} 对象，也可能是裸字符串，两种都要认。 */
    private fun normImg(i: Any?): Image? {
        if (i == null) return null
        if (i is String) return if (i.isEmpty()) null else Image(i, i, 0, 0)
        val o = i as? JSONObject ?: return null
        val origin = o.optString("originUrl").ifEmpty {
            o.optString("origin_url").ifEmpty {
                o.optString("url").ifEmpty {
                    o.optString("imageUrl").ifEmpty { o.optString("image_url") }
                }
            }
        }
        val download = o.optString("downloadUrl").ifEmpty {
            o.optString("download_url").ifEmpty { o.optString("url").ifEmpty { origin } }
        }
        if (origin.isEmpty() && download.isEmpty()) return null
        val extra = o.optJSONObject("extra")
        return Image(
            originUrl = origin,
            downloadUrl = download,
            width = extra?.optInt("width", 0) ?: 0,
            height = extra?.optInt("height", 0) ?: 0
        )
    }

    private fun linkUrl(v: Any?): String {
        if (v is String) return v
        val o = v as? JSONObject ?: return ""
        for (k in listOf("url", "linkUrl", "href", "link")) {
            val s = o.optString(k)
            if (s.isNotEmpty()) return s
        }
        return ""
    }

    private fun parseMoment(m: JSONObject): Moment {
        val content = m.optJSONObject("content")
        val author = m.optJSONObject("author")
        val images = mutableListOf<Image>()
        val imgs = content?.optJSONObject("media")?.optJSONArray("images")
        if (imgs != null) {
            for (i in 0 until imgs.length()) {
                normImg(imgs.opt(i))?.let { images += it }
            }
        }
        val hotLinks = mutableListOf<String>()
        val hot = m.optJSONObject("hotComment")
        val hotArr = hot?.optJSONArray("links") ?: hot?.optJSONObject("content")?.optJSONArray("links")
        if (hotArr != null) {
            for (i in 0 until hotArr.length()) {
                val u = linkUrl(hotArr.opt(i))
                if (u.isNotEmpty()) hotLinks += u
            }
        }
        return Moment(
            id = m.optString("id"),
            title = content?.optString("title").orEmpty(),
            text = content?.optString("text").orEmpty(),
            source = content?.optString("source").orEmpty().uppercase(),
            refMomentId = m.optString("refMomentId").ifEmpty {
                content?.optString("refMomentId").orEmpty()
            },
            createTime = ts(m.optString("createTime")),
            authorId = author?.optString("userId").orEmpty(),
            authorType = author?.optString("type").orEmpty(),
            images = images,
            excerpt = content?.optString("excerpt").orEmpty(),
            cover = normImg(m.opt("coverImage"))?.originUrl.orEmpty(),
            hotCommentLinks = hotLinks,
            deleted = m.optBoolean("isDeleted", false)
        )
    }

    /** GetFeed 除 feed 外还带 similarMoments，成品帖有时只出现在这里。 */
    suspend fun getFeedBundle(momentId: String): Pair<Moment?, List<Moment>> {
        val r = ApiClient.post(
            "/moment.v1.FeedService/GetFeed",
            JSONObject().put("feedType", "FEED_TYPE_MOMENT").put("feedId", momentId)
        )
        val feed = r.optJSONObject("feed")
        val moment = feed?.optJSONObject("moment")?.let { parseMoment(it) }
        val similar = mutableListOf<Moment>()
        val arr = r.optJSONArray("similarMoments")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { similar += parseMoment(it) }
            }
        }
        return moment to similar
    }
}
