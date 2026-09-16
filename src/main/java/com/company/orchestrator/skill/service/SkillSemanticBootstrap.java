package com.company.orchestrator.skill.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语义检索首次启用时自动补齐向量（迁移已建表但表为空）。
 * 首启失败不阻断应用：功能在下次成功重建前保持不可用。
 * Backfill skill vectors on first enable (table created by the optional
 * migration but still empty); a failure is logged without blocking startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillSemanticBootstrap implements ApplicationRunner {

    private final SkillSemanticService semantic;

    @Override
    public void run(ApplicationArguments args) {
        if (!semantic.enabled()) return;
        try {
            var status = semantic.status();
            if (((Number) status.get("embeddedSkills")).intValue() == 0) {
                var result = semantic.rebuild();
                log.info("skill semantic backfill on first enable: {}", result);
            }
        } catch (Exception ex) {
            log.error("skill semantic bootstrap failed — check pgvector availability or disable SKILL_SEMANTIC_ENABLED", ex);
        }
    }
}
