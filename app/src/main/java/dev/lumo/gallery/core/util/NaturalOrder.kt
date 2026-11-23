package dev.lumo.gallery.core.util

/**
 * “严格自然排序”：按文件名里的数字块做数值比较（1, 2, 10 而不是 1, 10, 2）。
 *
 * 规则（与 [NaturalSortKey] 必须保持一致，由单测锁定）：
 * 1. 连续数字块按数值比较，忽略前导零；
 * 2. 数值相等时，前导零更少的排前面（"1" < "01" < "001"）；
 * 3. 非数字字符按小写后的码点比较，完全相等时再按原始大小写比较，保证稳定；
 * 4. 一方是另一方前缀时，短的排前面。
 *
 * 用于内存排序（收藏、搜索结果、归档条目兜底）与测试基准。
 */
object NaturalOrder : Comparator<String> {

    override fun compare(a: String?, b: String?): Int {
        if (a === b) return 0
        if (a == null) return -1
        if (b == null) return 1

        var i = 0
        var j = 0
        while (i < a.length && j < b.length) {
            val ca = a[i]
            val cb = b[j]
            if (ca.isAsciiDigit() && cb.isAsciiDigit()) {
                val endA = digitRunEnd(a, i)
                val endB = digitRunEnd(b, j)
                val cmp = compareNumericRuns(a, i, endA, b, j, endB)
                if (cmp != 0) return cmp
                i = endA
                j = endB
            } else {
                val cmp = compareChars(ca, cb)
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (a.length - i) - (b.length - j)
    }

    private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

    private fun digitRunEnd(s: String, start: Int): Int {
        var k = start
        while (k < s.length && s[k].isAsciiDigit()) k++
        return k
    }

    private fun compareNumericRuns(
        a: String, startA: Int, endA: Int,
        b: String, startB: Int, endB: Int
    ): Int {
        var sigA = startA
        while (sigA < endA && a[sigA] == '0') sigA++
        var sigB = startB
        while (sigB < endB && b[sigB] == '0') sigB++

        val lenA = endA - sigA
        val lenB = endB - sigB
        if (lenA != lenB) return lenA.compareTo(lenB)
        for (k in 0 until lenA) {
            val cmp = a[sigA + k].compareTo(b[sigB + k])
            if (cmp != 0) return cmp
        }
        // 数值相同：前导零少的在前
        val zerosA = sigA - startA
        val zerosB = sigB - startB
        if (zerosA != zerosB) return zerosA.compareTo(zerosB)
        return 0
    }

    private fun compareChars(a: Char, b: Char): Int {
        val la = a.lowercaseChar()
        val lb = b.lowercaseChar()
        if (la != lb) return la.compareTo(lb)
        // 小写相同：大写优先，保证 "A" < "a" 的稳定顺序
        return a.compareTo(b)
    }

    /** 排序用便捷扩展：稳定的自然序。 */
    fun <T> comparatorBy(selector: (T) -> String?): Comparator<T> =
        Comparator { x, y -> compare(selector(x), selector(y)) }
}
