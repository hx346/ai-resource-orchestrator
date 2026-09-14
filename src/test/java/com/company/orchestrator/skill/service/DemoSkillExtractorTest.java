package com.company.orchestrator.skill.service;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deterministic demo extractor: keyword tiers, alias dedupe, ascii boundaries. */
class DemoSkillExtractorTest {

    private int level(List<DemoSkillExtractor.Extracted> items, String skillName) {
        return items.stream().filter(i -> i.skillName().equals(skillName)).findFirst().orElseThrow().level();
    }

    @Test
    void infersLevelFromNearestKeyword() {
        var catalog = List.of(
                new DemoSkillExtractor.CatalogSkill(1, "Java", List.of()),
                new DemoSkillExtractor.CatalogSkill(2, "Spring Boot", List.of()),
                new DemoSkillExtractor.CatalogSkill(3, "PostgreSQL", List.of()));
        var items = DemoSkillExtractor.extract("曾精通 Java，并主导 Spring Boot 改造，熟悉 PostgreSQL 调优", catalog);
        assertEquals(3, items.size());
        assertEquals(5, level(items, "Java"));
        assertEquals(4, level(items, "Spring Boot"));
        assertEquals(2, level(items, "PostgreSQL"));
    }

    @Test
    void aliasMatchesDedupeIntoBestExactHit() {
        var catalog = List.of(new DemoSkillExtractor.CatalogSkill(1, "机器学习", List.of("ML", "machine learning")));
        var items = DemoSkillExtractor.extract("熟悉 ML，后成为机器学习专家", catalog);
        assertEquals(1, items.size());
        assertEquals(5, items.get(0).level());
        assertEquals("EXACT", items.get(0).matchedBy());
    }

    @Test
    void respectsAsciiWordBoundariesAndDefaults() {
        var catalog = List.of(new DemoSkillExtractor.CatalogSkill(1, "Java", List.of()));
        assertTrue(DemoSkillExtractor.extract("使用 JavaScript 与 TypeScript 开发", catalog).isEmpty());
        var items = DemoSkillExtractor.extract("用过 java 做服务端工作", catalog);
        assertEquals(1, items.size());
        assertEquals(3, items.get(0).level());
        assertEquals(0.90, items.get(0).confidence());
    }

    @Test
    void blankTextOrEmptyCatalogYieldsNothing() {
        assertTrue(DemoSkillExtractor.extract("   ", List.of(new DemoSkillExtractor.CatalogSkill(1, "Java", List.of()))).isEmpty());
        assertTrue(DemoSkillExtractor.extract("精通 Java", List.of()).isEmpty());
    }
}
