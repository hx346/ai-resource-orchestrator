package com.company.orchestrator.skill.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 演示模式的确定性技能识别：按技能名/别名扫描文本，取匹配点最近的关键词推断等级。
 * Deterministic skill extraction for demo mode: scan the text by skill
 * name/alias and infer the level from the tier keyword nearest to the match.
 * No model calls, no randomness.
 */
public final class DemoSkillExtractor {

    private DemoSkillExtractor() {}

    /** 上下文窗口半径（字符）/ Context window radius in characters. */
    private static final int WINDOW = 24;
    /** 无等级关键词时的默认等级 / Default level when no tier keyword is nearby. */
    private static final int DEFAULT_LEVEL = 3;
    /** 等级关键词，从 L5 到 L1 / Tier keywords from L5 down to L1. */
    private static final String[][] LEVEL_WORDS = {
            {"专家", "资深", "精通", "架构", "expert", "architect"},
            {"熟练", "深入", "主导", "独立负责", "senior", "lead", "proficient"},
            {"负责", "使用", "开发", "实现", "independent"},
            {"熟悉", "参与", "协助", "了解", "assist", "familiar"},
            {"接触", "入门", "学习", "beginner"}};
    private static final int MAX_SKILLS = 50;

    /** 技能库条目（名称 + 别名）/ Catalog entry: canonical name plus aliases. */
    public record CatalogSkill(long skillId, String name, List<String> aliases) {}

    /** 识别结果 / Extraction result. */
    public record Extracted(String name, long skillId, String skillName, String matchedBy, int level, double confidence, String reason) {}

    public static List<Extracted> extract(String text, List<CatalogSkill> catalog) {
        var result = new ArrayList<Extracted>();
        if (text == null || text.isBlank() || catalog == null) return result;
        String haystack = text.toLowerCase();
        for (var skill : catalog) {
            if (skill == null || skill.name() == null) continue;
            int bestLevel = 0; boolean bestExact = false; String hit = null; String basis = null;
            var candidates = new ArrayList<Map.Entry<String, Boolean>>(List.of(Map.entry(skill.name(), true)));
            for (var alias : skill.aliases() == null ? List.<String>of() : skill.aliases()) candidates.add(Map.entry(alias, false));
            for (var candidate : candidates) {
                String needle = candidate.getKey().trim();
                if (needle.length() < 2) continue;
                String lower = needle.toLowerCase();
                for (int i = indexOf(haystack, lower, 0); i >= 0; i = indexOf(haystack, lower, i + 1)) {
                    var inferred = infer(haystack, i, lower.length());
                    int level = inferred.tier() == 0 ? DEFAULT_LEVEL : inferred.tier();
                    if (level > bestLevel || (level == bestLevel && candidate.getValue() && !bestExact)) {
                        bestLevel = level; bestExact = candidate.getValue(); hit = needle;
                        basis = inferred.word() == null ? "无等级词，默认 L" + DEFAULT_LEVEL : inferred.word();
                    }
                }
            }
            if (hit != null)
                result.add(new Extracted(hit, skill.skillId(), skill.name(), bestExact ? "EXACT" : "ALIAS",
                        bestLevel, bestExact ? 0.90 : 0.75, "命中「" + hit + "」；等级依据「" + basis + "」"));
        }
        result.sort((a, b) -> a.level() != b.level() ? b.level() - a.level() : a.skillName().compareTo(b.skillName()));
        return result.size() > MAX_SKILLS ? List.copyOf(result.subList(0, MAX_SKILLS)) : result;
    }

    /** 匹配点附近的等级推断（最近关键词优先，距离相同取更高等级；word 为空表示无关键词）/ Nearest-keyword tier inference; null word = no keyword. */
    private record Inference(int tier, String word) {}

    private static Inference infer(String haystack, int matchIndex, int matchLength) {
        int from = Math.max(0, matchIndex - WINDOW);
        int to = Math.min(haystack.length(), matchIndex + matchLength + WINDOW);
        String window = haystack.substring(from, to);
        int bestTier = 0; String bestWord = null; int bestDistance = Integer.MAX_VALUE;
        for (int tier = 0; tier < LEVEL_WORDS.length; tier++)
            for (String word : LEVEL_WORDS[tier])
                for (int w = window.indexOf(word); w >= 0; w = window.indexOf(word, w + 1)) {
                    int wordStart = from + w;
                    int wordEnd = wordStart + word.length();
                    // 与匹配串重叠的关键词（技能名自带，如「机器学习」含「学习」）不作为等级依据 / Skip keywords overlapping the needle itself
                    if (wordStart < matchIndex + matchLength && wordEnd > matchIndex) continue;
                    int distance = wordStart >= matchIndex ? wordStart - (matchIndex + matchLength) : matchIndex - wordEnd;
                    if (distance < bestDistance || (distance == bestDistance && tier < bestTier)) {
                        bestTier = tier; bestWord = word; bestDistance = distance;
                    }
                }
        return new Inference(bestWord == null ? 0 : LEVEL_WORDS.length - bestTier, bestWord);
    }

    /** ASCII 词需词边界，避免 java 命中 javascript / 中文按子串匹配 / ASCII needles need word boundaries; CJK matches by substring. */
    private static int indexOf(String haystack, String needle, int from) {
        boolean word = needle.chars().allMatch(c -> c < 0x80);
        for (int i = haystack.indexOf(needle, from); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            char before = i > 0 ? haystack.charAt(i - 1) : ' ';
            char after = i + needle.length() < haystack.length() ? haystack.charAt(i + needle.length()) : ' ';
            if (!word || (!Character.isLetterOrDigit(before) && !Character.isLetterOrDigit(after))) return i;
        }
        return -1;
    }
}
