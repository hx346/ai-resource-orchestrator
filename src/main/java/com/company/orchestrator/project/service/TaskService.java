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
