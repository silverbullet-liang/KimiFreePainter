package com.kimifreepainter.data.repo

import com.kimifreepainter.data.api.KimiApi

/**
 * 成品帖识别。五条判据依次尝试，任何一条命中即返回。
 *
 * 关键点：不能只认「content.media.images 里有图」。
 * 图文动态在列表层往往只给 coverImage，取不到 media 时候选集就是空的——
 * 这正是带图触发时明明有成品帖却认不出来的原因。
 * 所以「有图」= 正文配图 或 封面图 任一非空。
 *
 * 1. 引用关系：成品帖引用触发帖，或触发帖反过来引用成品帖
 * 2. 新出现且有图的帖（来源为 AI 优先）
 * 3. 新帖回查详情：有图 / 来源是 AI / 正文引用了本次提示词，都算
 * 4. 触发帖评论里机器人回复的链接（富文本 links，正文里没有 URL）
 * 5. GetFeed 的 similarMoments，成品帖有时只出现在这里
 */
object MomentProbe {

    private const val AI_SOURCE = "MOMENT_SOURCE_AI"

    suspend fun scan(
        triggerId: String,
        prompt: String,
        watermark: Long,
        ownerId: String,
        seen: Set<String>
    ): KimiApi.Moment? {
        val feeds = runCatching { KimiApi.listMyFeeds(20).first }.getOrDefault(emptyList())
        if (feeds.isEmpty()) return null

        val mine = feeds.filter { m ->
            m.id.isNotEmpty() && m.id != triggerId && m.id !in seen &&
                (ownerId.isEmpty() || m.authorId.isEmpty() || m.authorId == ownerId) &&
                (watermark <= 0 || m.createTime <= 0 || m.createTime >= watermark - 120)
        }
        val head = prompt.trim().take(12)

        // 判据一：引用关系，两个方向都查
        mine.firstOrNull { it.refMomentId.isNotEmpty() && it.refMomentId == triggerId }?.let { return it }
        feeds.firstOrNull { it.id == triggerId }?.refMomentId
            ?.takeIf { it.isNotEmpty() && it != triggerId }
            ?.let { back ->
                runCatching { KimiApi.getMoment(back) }.getOrNull()?.let { return it }
            }

        // 判据二：AI 来源且有图
        mine.firstOrNull { it.source == AI_SOURCE && it.hasImage }?.let { return it }

        // 判据三：新帖回查详情
        for (cand in mine) {
            val d = runCatching { KimiApi.getMoment(cand.id) }.getOrNull() ?: continue
            if (d.hasImage || d.source == AI_SOURCE || (head.length >= 4 && d.text.contains(head))) {
                return d
            }
        }

        // 判据五：similarMoments
        val bundle = runCatching { KimiApi.getFeedBundle(triggerId) }.getOrNull()
        if (bundle != null) {
            val pool = bundle.second + listOfNotNull(bundle.first)
            pool.firstOrNull { it.id != triggerId && (it.hasImage || it.source == AI_SOURCE) }
                ?.let { return it }
        }
        return null
    }

    /** 兜底：评论区机器人回复与热门评论里的链接。链接在 links 里，正文只有显示文本。 */
    suspend fun viaComments(
        triggerId: String,
        ownerId: String,
        seen: Set<String>
    ): KimiApi.Moment? {
        val comments = runCatching { KimiApi.listComments(triggerId, 20) }.getOrNull().orEmpty()
        for (reply in comments) {
            if (reply.type != "COMMENT_TYPE_BOT" && !reply.aiGenerated) continue
            val found = fromLinks(reply.links + reply.text, triggerId, seen)
            if (found != null) return found
        }
        // 触发帖自带的热门评论里也可能有机器人回复
        val bundle = runCatching { KimiApi.getFeedBundle(triggerId) }.getOrNull()
        val hot = bundle?.first?.hotCommentLinks.orEmpty()
        return fromLinks(hot, triggerId, seen)
    }

    private suspend fun fromLinks(
        sources: List<String>,
        triggerId: String,
        seen: Set<String>
    ): KimiApi.Moment? {
        for (src in sources) {
            for (id in extractMomentIds(src)) {
                if (id == triggerId || id in seen) continue
                val m = runCatching { KimiApi.getMoment(id) }.getOrNull() ?: continue
                if (m.hasImage || m.source == AI_SOURCE) return m
            }
        }
        return null
    }

    /** 从文本里抽候选 moment id（链接末段）。不做接口调用，纯解析。 */
    fun extractMomentIds(text: String): List<String> {
        val out = LinkedHashSet<String>()
        Regex("""https?://[^\s，。、）)】\]"']+""").findAll(text).forEach { hit ->
            val path = hit.value.substringAfter("://").substringAfter('/', "")
                .substringBefore('?').substringBefore('#')
            val last = path.trim('/').split('/').lastOrNull().orEmpty()
            if (last.length >= 8 && last.all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                out += last
            }
        }
        return out.toList()
    }
}
