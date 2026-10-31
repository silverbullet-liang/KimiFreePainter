package com.kimifreepainter.data.repo

import com.kimifreepainter.data.api.KimiApi

/**
 * 成品帖识别。唯一判据是 content.source == MOMENT_SOURCE_AI。
 * author 只能用来排除他人动态（成品帖的 author 就是发起人自己），不足以单独判定。
 */
object MomentProbe {

    private const val AI_SOURCE = "MOMENT_SOURCE_AI"

    /** 主链路：从我发布的动态里找新出现的 AI 帖。 */
    suspend fun scan(watermark: Long, ownerId: String, seen: Set<String>): KimiApi.Moment? {
        val (feeds, _) = KimiApi.listMyFeeds(10)
        return feeds.firstOrNull { m ->
            m.source == AI_SOURCE &&
                m.createTime >= watermark &&
                m.images.isNotEmpty() &&
                m.id !in seen &&
                (ownerId.isEmpty() || m.authorId == ownerId)
        }
    }

    /** 兜底：评论区里找机器人回复，从链接解析出成品帖 ID，再回查。 */
    suspend fun viaComments(triggerId: String, ownerId: String, seen: Set<String>): KimiApi.Moment? {
        val comments = runCatching { KimiApi.listComments(triggerId, 20) }.getOrNull().orEmpty()
        val botReply = comments.firstOrNull { it.type == "COMMENT_TYPE_BOT" || it.aiGenerated }
            ?: return null
        for (id in extractMomentIds(botReply.text)) {
            if (id == triggerId || id in seen) continue
            val m = runCatching { KimiApi.getMoment(id) }.getOrNull() ?: continue
            if (m.source == AI_SOURCE && m.images.isNotEmpty()) return m
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
