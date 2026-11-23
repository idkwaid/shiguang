package dev.lumo.gallery.core.util

/**
 * 生成可直接入库、能用 SQL `ORDER BY` 排的“自然排序键”。
 *
 * 做法：把数字块左侧补零到 12 位，其余字符转小写；前后加分隔符避免歧义。
 * 这样字典序 == 自然序，列表可以在数据库层排序 + 分页，不必把所有行读进内存。
 *
 * 与 [NaturalOrder] 的一致性由 `NaturalSortKeyTest` 锁定。
 * 超过 12 位有效数字的极端文件名会退化为按原样比较（仍稳定，不崩）。
 */
object NaturalSortKey {

    private const val NUMBER_WIDTH = 12

    /** 压缩包条目：先按目录自然序，再按文件名自然序（平铺展示时保持分组感）。 */
    fun ofPath(locator: String): String {
        if (locator.isEmpty()) return ""
        return locator.split('/').joinToString("/") { of(it) }
    }

    fun of(name: String): String {
        if (name.isEmpty()) return ""
        val sb = StringBuilder(name.length + 8)
        var i = 0
        while (i < name.length) {
            val c = name[i]
            if (c in '0'..'9') {
                var end = i
                while (end < name.length && name[end] in '0'..'9') end++
                var sig = i
                while (sig < end && name[sig] == '0') sig++
                val significant = end - sig
                if (significant > NUMBER_WIDTH) {
                    // 超大数字：保留原始数字串，前缀长度保证同类可比
                    sb.append('~')
                    sb.append(significant.toString().padStart(4, '0'))
                    sb.append(name, sig, end)
                } else {
                    sb.append('~')
                    repeat(NUMBER_WIDTH - significant) { sb.append('0') }
                    if (significant > 0) sb.append(name, sig, end)
                    sb.append(':')
                    sb.append((sig - i).toString().padStart(3, '0'))
                }
                i = end
            } else {
                sb.append(if (c in 'A'..'Z') (c.code + 32).toChar() else c)
                i++
            }
        }
        return sb.toString()
    }
}
