package com.company.orchestrator.skill.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 确定性本地嵌入（语义检索的 local 模式）：名称归一化后的 2/3 字符组做特征哈希，
 * L2 归一。中文按字组、英文去分隔符后按字符组，近形 / 同源名余弦高，无关名接近 0。
 * 不调用任何外部服务；切换 live 嵌入模式后执行重建即可替换。
 * Deterministic local embedding (semantic search, `local` mode): hashed char
 * 2/3-grams of the normalized name, L2-normalized. No external calls; rebuild
 * to replace after switching to a live embedding model.
 */
public final class SemanticEmbedding {

    private SemanticEmbedding() {}

    public static final int DEFAULT_DIM = 256;

    public static float[] hash(String name, int dim) {
        var vector = new float[Math.max(2, dim)];
        var normalized = normalize(name);
        if (normalized.isEmpty()) return vector;
        var features = new ArrayList<String>();
        for (int width = 2; width <= 3; width++)
            for (int i = 0; i + width <= normalized.length(); i++) features.add(normalized.substring(i, i + width));
        if (features.isEmpty()) features.add(normalized);   // 单字符名 / single-char name
        for (var feature : features) {
            int code = feature.hashCode();
            vector[Math.floorMod(code, vector.length)] += (code >>> 16 & 1) == 0 ? 1 : -1;
        }
        double sum = 0;
        for (float value : vector) sum += (double) value * value;
        if (sum == 0) return vector;
        double length = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) vector[i] = (float) (vector[i] / length);
        return vector;
    }

    /** pgvector 文本字面量 '[0.1,0.2,…]' / vector literal accepted by pgvector casts. */
    public static String literal(float[] vector) {
        var text = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) text.append(i == 0 ? "" : ",").append(String.format(Locale.ROOT, "%.6f", vector[i]));
        return text.append(']').toString();
    }

    public static double cosine(float[] left, float[] right) {
        if (left.length != right.length) return 0;
        double dot = 0;
        for (int i = 0; i < left.length; i++) dot += (double) left[i] * right[i];
        return dot;   // 向量已 L2 归一 / vectors are unit-length
    }

    /** 与 SkillSimilarity 同规则的小写与分隔符归一 / same normalization as SkillSimilarity. */
    static String normalize(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s_\\-.+/|()（）\\[\\]·,，:：;；]", "");
    }

    static List<String> grams(String value) {
        var normalized = normalize(value);
        var features = new ArrayList<String>();
        for (int width = 2; width <= 3; width++)
            for (int i = 0; i + width <= normalized.length(); i++) features.add(normalized.substring(i, i + width));
        if (features.isEmpty() && !normalized.isEmpty()) features.add(normalized);
        return features;
    }
}
