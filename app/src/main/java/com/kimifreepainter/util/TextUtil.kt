package com.kimifreepainter.util

/**
 * 成品帖正文剥离，与 Web 端 extractIntro 同一套规则。
 *
 * 成品帖正文 = 简介 + 「@画师 提示词」引用块 + 「—— 昵称」署名。
 * 不认画师名、也不认用户昵称，只按三件事剥离，换名字也不会失效：
 *   1. 以 @ 开头的提及行
 *   2. 纯破折号署名行
 *   3. 正文里原样引用的本次提示词（以 prompt 为准逐行比对）
 * 剥完为空时退回原文，宁可多显示也不空白。
 */
object TextUtil {

    private val SIGN = Regex("^[—–―]{2,}\\s*\\S{0,30}$")
    private val BLANKS = Regex("\\n{3,}")

    fun extractIntro(raw: String, prompt: String): String {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n')
        if (text.isBlank()) return ""
        val pn = norm(prompt)
        val kept = text.split('\n').filterNot { junk(it, pn) }

        // 提示词被平台合并/改写后，整段仍可能是它的片段
        val blocks = kept.joinToString("\n").split(Regex("\\n{2,}")).filter { b ->
            if (b.isBlank()) return@filter false
            val n = norm(b)
            if (pn.isEmpty() || n.length < 10) return@filter true
            !(pn.contains(n) || n.contains(pn))
        }

        val out = BLANKS.replace(blocks.joinToString("\n\n"), "\n\n").trim()
        if (out.isNotEmpty()) return out

        return BLANKS.replace(
            text.replace(Regex("^@\\S.*$", RegexOption.MULTILINE), "")
                .replace(Regex("^[—–―]{2,}\\s*\\S{0,30}$", RegexOption.MULTILINE), ""),
            "\n\n"
        ).trim()
    }

    private fun junk(line: String, pn: String): Boolean {
        val s = line.trim()
        if (s.isEmpty()) return false
        if (s.length > 1 && s.startsWith("@")) return true
        if (SIGN.matches(s)) return true
        if (pn.isNotEmpty()) {
            val n = norm(s)
            if (n.length >= 6 && pn.contains(n)) return true
        }
        return false
    }

    private fun norm(s: String): String = if (s.isEmpty()) "" else s.replace(Regex("[\\s@]"), "").lowercase()
}
