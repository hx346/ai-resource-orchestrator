package com.company.orchestrator.skill.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic similarity: separators, containment boost, threshold. */
class SkillSimilarityTest {

    @Test
    void separatorAndCaseInsensitive() {
        assertEquals(1.0, SkillSimilarity.score("Spring Boot", "springboot"));
        assertEquals(1.0, SkillSimilarity.score("PostgreSQL", "postgre-sql"));
    }

    @Test
    void containmentBoostsLongerNames() {
        assertTrue(SkillSimilarity.score("机器学习", "机器学习平台") >= 0.9);
        assertTrue(SkillSimilarity.score("Kubernetes", "Kubernetes 运维") >= 0.9);
    }

    @Test
    void shortAmbiguousNamesStayBelowThreshold() {
        assertFalse(SkillSimilarity.score("js", "JavaScript") >= SkillSimilarity.THRESHOLD);
        assertFalse(SkillSimilarity.score("Go", "Golang") >= SkillSimilarity.THRESHOLD);
    }

    @Test
    void unrelatedOrBlankScoresLow() {
        assertTrue(SkillSimilarity.score("Java", "工业视觉") < SkillSimilarity.THRESHOLD);
        assertEquals(0, SkillSimilarity.score("", "Java"));
        assertEquals(0, SkillSimilarity.score(null, "Java"));
    }
}
