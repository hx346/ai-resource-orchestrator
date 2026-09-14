package com.company.orchestrator.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.TaskUpsertRequest;
import com.company.orchestrator.project.entity.Task;
import com.company.orchestrator.project.entity.TaskDependency;
import com.company.orchestrator.project.entity.TaskSkillRequirement;
import com.company.orchestrator.project.mapper.TaskDependencyMapper;
import com.company.orchestrator.project.mapper.TaskMapper;
import com.company.orchestrator.project.mapper.TaskSkillRequirementMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 任务管理 / Task management. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskMapper taskMapper;
    private final TaskDependencyMapper dependencyMapper;
    private final TaskSkillRequirementMapper requirementMapper;
    private final ProjectService projectService;

    public List<Task> listByProject(Long projectId) {
        projectService.requireExists(projectId);
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getId));
    }

    public Task requireExists(Long taskId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, taskId);
        }
        return task;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long projectId, TaskUpsertRequest request) {
        projectService.requireExists(projectId);
        Task task = new Task();
        apply(task, projectId, request);
        task.setStatus(Task.STATUS_TODO);
        taskMapper.insert(task);
        log.info("task created, id={}, projectId={}, name={}", task.getId(), projectId, task.getName());
        return task.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TaskUpsertRequest request) {
        Task task = requireExists(id);
        apply(task, task.getProjectId(), request);
        taskMapper.updateById(task);
    }

    /** 删除任务（连同其依赖与技能需求）/ Delete task with dependencies and skill requirements. */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        dependencyMapper.delete(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getPredecessorTaskId, id)
                .or()
                .eq(TaskDependency::getSuccessorTaskId, id));
        requirementMapper.delete(new LambdaQueryWrapper<TaskSkillRequirement>()
                .eq(TaskSkillRequirement::getTaskId, id));
        taskMapper.deleteById(id);
        log.info("task deleted, id={}", id);
    }

    private void apply(Task task, Long projectId, TaskUpsertRequest request) {
        if (request.parentId() != null) {
            Task parent = requireExists(request.parentId());
            if (!parent.getProjectId().equals(projectId)) {
                throw new BusinessException(ErrorCode.INVALID_TASK_DEPENDENCY,
                        "parent task belongs to another project: " + request.parentId());
            }
        }
        task.setProjectId(projectId);
        task.setParentId(request.parentId());
        task.setMilestoneId(request.milestoneId());
        task.setName(request.name());
        task.setDescription(request.description());
        task.setPriority(request.priority() == null ? Task.DEFAULT_PRIORITY : request.priority());
        task.setEstimatedHours(request.estimatedHours());
        task.setStartDate(request.startDate());
        task.setEndDate(request.endDate());
    }
}
