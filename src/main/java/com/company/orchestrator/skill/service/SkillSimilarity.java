package com.company.orchestrator.skill.service;

/**
 * 技能名近似匹配：确定性相似度（归一化编辑距离 + 包含加分），语义向量检索留待后续引入 pgvector。
 * Approximate skill-name matching: normalized edit distance with a
 * containment boost. Vector search via pgvector stays a later upgrade.
 */
public final class SkillSimilarity {

    private SkillSimilarity() {}

    /** 建议阈值：达到才提示「相似技能」/ Minimum score to surface a suggestion. */
    public static final double THRESHOLD = 0.82;

    /** 0~1 相似度：忽略大小写与常见分隔符；完全包含（双方长度 ≥4）至少 0.9 / 0-1 similarity, separator-insensitive. */
    public static double score(String left, String right) {
        String a = normalize(left), b = normalize(right);
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        double ratio = 1.0 - (double) levenshtein(a, b) / Math.max(a.length(), b.length());
        if (a.length() >= 4 && b.length() >= 4 && (a.contains(b) || b.contains(a))) ratio = Math.max(ratio, 0.9);
        return ratio;
    }

    /** 去掉大小写与空白/标点分隔 / lowercase and strip separators. */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s_\\-.+/|()（）\\[\\]]", "");
    }

    private static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1], current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++)
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] swap = previous; previous = current; current = swap;
        }
        return previous[b.length()];
    }
}
