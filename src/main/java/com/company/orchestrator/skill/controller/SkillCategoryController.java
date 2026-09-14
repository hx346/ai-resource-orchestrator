package com.company.orchestrator.skill.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.skill.dto.SkillCategoryUpsertRequest;
import com.company.orchestrator.skill.dto.SkillCategoryView;
import com.company.orchestrator.skill.service.SkillCategoryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 技能分类接口 / Skill category API. */
@Tag(name = "Skill Category", description = "技能分类 / Skill categories")
@RestController
@RequestMapping("/api/v1/skill-categories")
@RequiredArgsConstructor
public class SkillCategoryController {

    private final SkillCategoryService categoryService;

    @Operation(summary = "分类列表 / List categories")
    @GetMapping
    public Result<List<SkillCategoryView>> list() {
        return Result.ok(categoryService.listAll().stream()
                .map(SkillCategoryView::from)
                .toList());
    }

    @Operation(summary = "创建分类 / Create category")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SkillCategoryUpsertRequest request) {
        return Result.ok(categoryService.create(request));
    }

    @Operation(summary = "更新分类 / Update category")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SkillCategoryUpsertRequest request) {
        categoryService.update(id, request);
        return Result.ok();
    }
}
