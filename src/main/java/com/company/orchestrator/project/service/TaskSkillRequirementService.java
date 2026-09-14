package com.company.orchestrator.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.enums.RequirementType;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.TaskSkillRequirementRequest;
import com.company.orchestrator.project.entity.TaskSkillRequirement;
import com.company.orchestrator.project.mapper.TaskSkillRequirementMapper;
import com.company.orchestrator.skill.api.SkillUsagePort;
import com.company.orchestrator.skill.service.SkillService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 任务技能需求管理 / Task skill requirement management.
 * 实现 SkillUsagePort，供 skill 模块查询引用（依赖倒置，避免循环依赖）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskSkillRequirementService implements SkillUsagePort {

    private final TaskSkillRequirementMapper requirementMapper;
    private final TaskService taskService;
    private final SkillService skillService;

    @Override
    public long countTaskRequirements(Long skillId) {
        return requirementMapper.selectCount(new LambdaQueryWrapper<TaskSkillRequirement>()
                .eq(TaskSkillRequirement::getSkillId, skillId));
    }

    public List<TaskSkillRequirement> listByTask(Long taskId) {
        taskService.requireExists(taskId);
        return requirementMapper.selectList(new LambdaQueryWrapper<TaskSkillRequirement>()
                .eq(TaskSkillRequirement::getTaskId, taskId)
                .orderByAsc(TaskSkillRequirement::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long taskId, TaskSkillRequirementRequest request) {
        taskService.requireExists(taskId);
        skillService.requireExists(request.skillId());

        TaskSkillRequirement requirement = new TaskSkillRequirement();
        requirement.setTaskId(taskId);
        requirement.setSkillId(request.skillId());
        requirement.setMinLevel(request.minLevel());
        requirement.setWeight(request.weight());
        requirement.setRequirementType(request.requirementType() == null
                ? RequirementType.REQUIRED
                : request.requirementType());
        requirementMapper.insert(requirement);
        log.info("task skill requirement created, taskId={}, skillId={}", taskId, request.skillId());
        return requirement.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        TaskSkillRequirement requirement = requirementMapper.selectById(id);
        if (requirement == null) {
            throw new BusinessException(ErrorCode.TASK_REQUIREMENT_NOT_FOUND, id);
        }
        requirementMapper.deleteById(id);
    }
}
