package org.mind.framework.cache;

import org.apache.commons.lang3.StringUtils;

/**
 * 把通配符按 * 切成固定片段，然后用 startsWith + indexOf 顺序推进，全程纯字符比较，零正则开销。
 *
 * @author: Marcus
 * @date: 2026/11/17
 * @version: 1.0
 */
public final class WildcardMatcher {
    private final String pattern;
    private final boolean startsWithStar;
    private final boolean endsWithStar;

    // 按 * 切分的固定片段
    private final String[] segments;

    public WildcardMatcher(String pattern) {
        this.pattern = pattern;
        this.startsWithStar = pattern.startsWith("*");
        this.endsWithStar = pattern.endsWith("*");
        this.segments = pattern.split("\\*", -1);
    }

    public boolean matches(String origStr) {
        if (StringUtils.isEmpty(origStr))
            return false;

        // 无 * 的情况：完全相等
        if (segments.length == 1)
            return origStr.equals(pattern);

        // 首段为空（pattern 以 * 开头），跳过
        int idx = 0;
        int segCount = segments.length;

        // 1. 首段：非 * 开头必须精确前缀匹配
        String first = segments[0];
        if (!startsWithStar) {
            if (!origStr.startsWith(first))
                return false;
            idx = first.length();
        }

        // 2. 中间段：顺序 indexOf 推进
        int end = segCount - 1;
        for (int i = 1; i < end; i++) {
            String seg = segments[i];

            // 连续 ** 的情况
            if (seg.isEmpty())
                continue;

            int pos = origStr.indexOf(seg, idx);
            if (pos < 0)
                return false;

            idx = pos + seg.length();
        }

        // 3. 尾段：非 * 结尾必须精确后缀匹配
        String last = segments[end];
        if (!endsWithStar) {
            // 尾段必须落在字符串末尾
            int pos = origStr.lastIndexOf(last);
            return pos >= idx && pos + last.length() == origStr.length();
        }

        return true;
    }
}
