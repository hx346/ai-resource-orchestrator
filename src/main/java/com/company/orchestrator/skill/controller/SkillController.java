package com.company.orchestrator.skill.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.orchestrator.common.result.PageVO;
import com.company.orchestrator.common.result.Result;
import com.company.orchestrator.skill.dto.SkillUpsertRequest;
import com.company.orchestrator.skill.dto.SkillView;
import com.company.orchestrator.skill.entity.SkillAlias;
import com.company.orchestrator.skill.service.SkillAliasService;
import com.company.orchestrator.skill.service.SkillNormalizer;
import com.company.orchestrator.skill.service.SkillService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

/** 技能接口 / Skill API. */
@Tag(name = "Skill", description = "技能库与归一化 / Skill library & normalization")
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final SkillAliasService aliasService;
    private final SkillNormalizer skillNormalizer;

    @Operation(summary = "技能分页查询 / Page skills")
    @GetMapping
    public Result<PageVO<SkillView>> page(@RequestParam(defaultValue = "1") long pageNum,
                                          @RequestParam(defaultValue = "10") long pageSize,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long categoryId) {
        return Result.ok(PageVO.from(skillService.page(pageNum, pageSize, keyword, categoryId))
                .map(SkillView::from));
    }

    @Operation(summary = "创建技能 / Create skill")
    @PostMapping
    public Result<Long> create(@Valid @RequestBody SkillUpsertRequest request) {
        return Result.ok(skillService.create(request));
    }

    @Operation(summary = "更新技能 / Update skill")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SkillUpsertRequest request) {
        skillService.update(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除技能（被引用时拒绝）/ Delete skill (rejected when in use)")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        skillService.delete(id);
        return Result.ok();
    }

    @Operation(summary = "技能名称归一化 / Normalize a raw skill name")
    @GetMapping("/normalize")
    public Result<SkillNormalizer.NormalizedSkill> normalize(@RequestParam String name) {
        return Result.ok(skillNormalizer.normalize(name).orElse(null));
    }

    @Operation(summary = "技能别名列表 / List aliases of a skill")
    @GetMapping("/{id}/aliases")
    public Result<List<SkillAlias>> listAliases(@PathVariable Long id) {
        return Result.ok(aliasService.listBySkill(id));
    }

    @Operation(summary = "新增技能别名 / Add an alias to a skill")
    @PostMapping("/{id}/aliases")
    public Result<Long> addAlias(@PathVariable Long id,
                                 @Valid @RequestBody AliasUpsertRequest request) {
        return Result.ok(aliasService.create(id, request.alias()));
    }

    @Operation(summary = "删除技能别名 / Delete an alias")
    @DeleteMapping("/{id}/aliases/{aliasId}")
    public Result<Void> deleteAlias(@PathVariable Long id, @PathVariable Long aliasId) {
        aliasService.delete(id, aliasId);
        return Result.ok();
    }

    /** 别名入参 / Alias request. */
    public record AliasUpsertRequest(@NotBlank @Size(max = 128) String alias) {
    }
}
