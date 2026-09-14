package com.company.orchestrator.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.enums.DependencyType;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.TaskDependencyRequest;
import com.company.orchestrator.project.entity.TaskDependency;
import com.company.orchestrator.project.mapper.TaskDependencyMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 任务依赖管理 / Task dependency management. */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDependencyService {

    private final TaskDependencyMapper dependencyMapper;
    private final TaskService taskService;
    private final org.springframework.jdbc.core.JdbcTemplate db;

    /** 查询任务的前置依赖 / List predecessors of a task. */
    public List<TaskDependency> listByTask(Long taskId) {
        taskService.requireExists(taskId);
        return dependencyMapper.selectList(new LambdaQueryWrapper<TaskDependency>()
                .eq(TaskDependency::getSuccessorTaskId, taskId)
                .orderByAsc(TaskDependency::getId));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long successorTaskId, TaskDependencyRequest request) {
        taskService.requireEditable(successorTaskId);
        var successor=taskService.requireExists(successorTaskId);
        var predecessor=taskService.requireExists(request.predecessorTaskId());
        if(!successor.getProjectId().equals(predecessor.getProjectId())) throw new BusinessException(ErrorCode.INVALID_TASK_DEPENDENCY,"跨项目依赖");
        db.queryForList("select id from project where id=? for update",successor.getProjectId());
        if(Boolean.TRUE.equals(db.queryForObject("with recursive reachable(id) as (select successor_task_id from task_dependency where predecessor_task_id=? union select d.successor_task_id from task_dependency d join reachable r on d.predecessor_task_id=r.id) select exists(select 1 from reachable where id=?)",Boolean.class,successorTaskId,request.predecessorTaskId()))) throw new BusinessException(ErrorCode.INVALID_TASK_DEPENDENCY,"依赖形成循环");
        taskService.requireExists(successorTaskId);
        if (request.predecessorTaskId().equals(successorTaskId)) {
            throw new BusinessException(ErrorCode.INVALID_TASK_DEPENDENCY, "self dependency: " + successorTaskId);
        }
        taskService.requireExists(request.predecessorTaskId());

        TaskDependency dependency = new TaskDependency();
        dependency.setSuccessorTaskId(successorTaskId);
        dependency.setPredecessorTaskId(request.predecessorTaskId());
        dependency.setDependencyType(request.dependencyType() == null
                ? DependencyType.FS
                : request.dependencyType());
        dependencyMapper.insert(dependency);
        log.info("task dependency created, {} -> {}", dependency.getPredecessorTaskId(), successorTaskId);
        return dependency.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long taskId, Long id) {
        TaskDependency dependency = dependencyMapper.selectById(id);
        if (dependency == null) {
            throw new BusinessException(ErrorCode.TASK_DEPENDENCY_NOT_FOUND, id);
        }
        taskService.requireEditable(dependency.getSuccessorTaskId());
        if (!dependency.getSuccessorTaskId().equals(taskId)) throw new BusinessException(ErrorCode.BAD_REQUEST,"记录不属于指定资源");
        dependencyMapper.deleteById(id);
    }
}
