package com.company.orchestrator.skill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 确定性本地嵌入：同源名高相似、无关名近零、输出可作 pgvector 字面量 / hash embedding behaviour. */
class SemanticEmbeddingTest {

    @Test
    void identicalNamesScoreOne() {
        assertEquals(1.0, SemanticEmbedding.cosine(
                SemanticEmbedding.hash("Spring Boot", 256), SemanticEmbedding.hash("spring boot", 256)), 1e-6);
    }

    @Test
    void separatorVariantsConverge() {
        assertEquals(1.0, SemanticEmbedding.cosine(
                SemanticEmbedding.hash("spring-boot", 256), SemanticEmbedding.hash("SpringBoot", 256)), 1e-6);
    }

    @Test
    void relatedNamesScoreAboveSuggestThreshold() {
        // 前缀共享的近形名应达到 0.5 的建议线 / related names clear the suggestion bar
        double spring = SemanticEmbedding.cosine(
                SemanticEmbedding.hash("Spring Boot", 256), SemanticEmbedding.hash("Spring Cloud", 256));
        double chinese = SemanticEmbedding.cosine(
                SemanticEmbedding.hash("工业视觉", 256), SemanticEmbedding.hash("工业视觉检测", 256));
        assertTrue(spring >= 0.5, "score=" + spring);
        assertTrue(chinese >= 0.5, "score=" + chinese);
    }

    @Test
    void unrelatedNamesScoreNearZero() {
        double score = SemanticEmbedding.cosine(
                SemanticEmbedding.hash("Java", 256), SemanticEmbedding.hash("Photoshop", 256));
        assertTrue(score < 0.3, "score=" + score);
    }

    @Test
    void deterministicAndLiteralFormatted() {
        var upper = SemanticEmbedding.hash("K8s", 256);
        var lower = SemanticEmbedding.hash("k8s", 256);
        for (int i = 0; i < upper.length; i++) assertEquals(lower[i], upper[i], 1e-9);
        String literal = SemanticEmbedding.literal(SemanticEmbedding.hash("PostgreSQL", 256));
        assertTrue(literal.startsWith("[") && literal.endsWith("]") && literal.contains("."), literal);
    }

    @Test
    void emptyNameYieldsZeroVector() {
        assertEquals(0f, SemanticEmbedding.hash("", 256)[0]);
        assertEquals(0f, SemanticEmbedding.hash(null, 256)[0]);
    }
}
