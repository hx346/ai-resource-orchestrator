package com.company.orchestrator.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.TaskUpsertRequest;
import com.company.orchestrator.project.entity.Project;
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
    private final org.springframework.jdbc.core.JdbcTemplate db;

    public List<Task> listByProject(Long projectId) {
        projectService.requireExists(projectId);
        return taskMapper.selectList(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, projectId)
                .orderByAsc(Task::getId));
    }

    public void requireEditable(Long taskId) { requireProjectEditable(requireExists(taskId).getProjectId()); }
    private void requireProjectEditable(Long projectId) {
        projectService.lock(projectId);
        if(db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('CONFIRMED','PLANNED')",Long.class,projectId)>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"请先撤销生效方案，再修改任务及需求");
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

    private static final java.util.Set<String> STATUSES = java.util.Set.of(
            Task.STATUS_TODO, Task.STATUS_IN_PROGRESS, Task.STATUS_DONE, Task.STATUS_CANCELLED);

    /** 状态机（纯函数，便于单测）：DONE / CANCELLED 为终态 / pure transition map; DONE and CANCELLED are terminal. */
    static boolean canTransition(String from, String to) {
        if (to.equals(from)) return false;
        return switch (from) {
            case Task.STATUS_TODO -> java.util.Set.of(Task.STATUS_IN_PROGRESS, Task.STATUS_DONE, Task.STATUS_CANCELLED).contains(to);
            case Task.STATUS_IN_PROGRESS -> java.util.Set.of(Task.STATUS_TODO, Task.STATUS_DONE, Task.STATUS_CANCELLED).contains(to);
            default -> false;
        };
    }

    /**
     * 执行侧状态流转（Phase 1–7 收尾段）：完结/取消任务时同步收尾其生效分配——
     * DONE→分配 COMPLETED、CANCELLED→分配 CANCELLED，容量即时释放（下游均按 PLANNED/CONFIRMED 过滤）。
     * Execution-side transition; completing or cancelling a task also closes its
     * active bookings so capacity is released immediately.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        Task task = requireExists(id);
        if (!STATUSES.contains(status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"不支持的任务状态："+status);
        if (!canTransition(task.getStatus(), status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"任务状态不允许由 "+task.getStatus()+" 变更为 "+status);
        var project = projectService.requireExists(task.getProjectId());
        if (Project.STATUS_COMPLETED.equals(project.getStatus()) || Project.STATUS_CANCELLED.equals(project.getStatus()))
            throw new BusinessException(ErrorCode.BAD_REQUEST,"项目已完结/取消，不可变更任务状态");
        if (Task.STATUS_DONE.equals(status) || Task.STATUS_CANCELLED.equals(status)) {
            if (db.queryForObject("select count(*) from task where parent_id=? and status in ('TODO','IN_PROGRESS')",Long.class,id)>0)
                throw new BusinessException(ErrorCode.BAD_REQUEST,"存在未完结的子任务，请先完结子任务");
            db.update("update resource_allocation set status=?,updated_at=now() where task_id=? and status in ('PLANNED','CONFIRMED')",
                    Task.STATUS_DONE.equals(status) ? "COMPLETED" : "CANCELLED", id);
        }
        // Task 实体多个字段为 ALWAYS 更新策略，部分实体更新会误置空，故用定向 SQL / targeted update, partial entities would null ALWAYS columns
        db.update("update task set status=?,updated_at=now() where id=?", status, id);
        log.info("task status changed, id={}, {} -> {}", id, task.getStatus(), status);
    }

    /** 删除任务（连同其依赖与技能需求）/ Delete task with dependencies and skill requirements. */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireEditable(id);
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
        requireProjectEditable(projectId);
        var project=projectService.requireExists(projectId);
        if(request.startDate()!=null && request.endDate()!=null && request.endDate().isBefore(request.startDate())) throw new BusinessException(ErrorCode.BAD_REQUEST,"任务结束日期早于开始日期");
        if(request.startDate()!=null && project.getStartDate()!=null && request.startDate().isBefore(project.getStartDate()) || request.endDate()!=null && project.getEndDate()!=null && request.endDate().isAfter(project.getEndDate())) throw new BusinessException(ErrorCode.BAD_REQUEST,"任务日期超出项目周期");
        if(request.milestoneId()!=null && db.queryForObject("select count(*) from project_milestone where id=? and project_id=?",Long.class,request.milestoneId(),projectId)==0) throw new BusinessException(ErrorCode.BAD_REQUEST,"里程碑不属于该项目");
        Long ancestor=request.parentId(); var seen=new java.util.HashSet<Long>();
        while(ancestor!=null) {
            if(ancestor.equals(task.getId()) || !seen.add(ancestor)) throw new BusinessException(ErrorCode.BAD_REQUEST,"任务层级存在循环");
            ancestor=requireExists(ancestor).getParentId();
        }
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
