package com.company.orchestrator.project.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.ProjectUpsertRequest;
import com.company.orchestrator.project.entity.Project;
import com.company.orchestrator.project.entity.ProjectMilestone;
import com.company.orchestrator.project.entity.Task;
import com.company.orchestrator.project.mapper.ProjectMapper;
import com.company.orchestrator.project.mapper.ProjectMilestoneMapper;
import com.company.orchestrator.project.mapper.TaskMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 项目管理 / Project management. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectMapper projectMapper;
    private final ProjectMilestoneMapper milestoneMapper;
    private final TaskMapper taskMapper;
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final com.company.orchestrator.allocation.ReplanTriggerService replan;
    private final com.company.orchestrator.system.NotifyService notify;
    public void lock(Long id) { db.queryForList("select id from project where id=? for update",id); requireExists(id); }


    public IPage<Project> page(long pageNum, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<Project> wrapper = new LambdaQueryWrapper<Project>()
                .like(StringUtils.hasText(keyword), Project::getName, keyword)
                .eq(StringUtils.hasText(status), Project::getStatus, status)
                .orderByDesc(Project::getId);
        return projectMapper.selectPage(Page.of(Math.max(1,pageNum), Math.max(1,Math.min(500,pageSize))), wrapper);
    }

    public Project requireExists(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, projectId);
        }
        return project;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(ProjectUpsertRequest request) {
        Project project = new Project();
        apply(project, request);
        project.setStatus(Project.STATUS_PLANNING);
        projectMapper.insert(project);
        log.info("project created, id={}, name={}", project.getId(), project.getName());
        return project.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ProjectUpsertRequest request) {
        Project project = requireExists(id);
        apply(project, request);
        projectMapper.updateById(project);
        // 项目周期变化可能使生效分配落在窗口外 / window changes may push bookings outside the project
        replan.onProjectEvent(id, "PROJECT_UPDATED");
    }

    private static final java.util.Set<String> STATUSES = java.util.Set.of(
            Project.STATUS_PLANNING, Project.STATUS_IN_PROGRESS, Project.STATUS_ON_HOLD, Project.STATUS_COMPLETED, Project.STATUS_CANCELLED);

    /** 状态机（纯函数，便于单测）：COMPLETED / CANCELLED 为终态 / pure transition map; COMPLETED and CANCELLED are terminal. */
    static boolean canTransition(String from, String to) {
        if (to.equals(from)) return false;
        return switch (from) {
            case Project.STATUS_PLANNING -> java.util.Set.of(Project.STATUS_IN_PROGRESS, Project.STATUS_CANCELLED).contains(to);
            case Project.STATUS_IN_PROGRESS -> java.util.Set.of(Project.STATUS_ON_HOLD, Project.STATUS_COMPLETED, Project.STATUS_CANCELLED).contains(to);
            case Project.STATUS_ON_HOLD -> java.util.Set.of(Project.STATUS_IN_PROGRESS, Project.STATUS_CANCELLED).contains(to);
            default -> false;
        };
    }

    /**
     * 项目生命周期流转：进入终态（完结/取消）前须无生效分配；完结还要求全部任务收尾。
     * 与求解入口的 PLANNING/IN_PROGRESS 白名单互为呼应——终态项目不可再编排。
     * Terminal transitions require no active bookings; COMPLETED additionally
     * requires every task closed. Terminal projects can no longer be solved.
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, String status) {
        Project project = requireExists(id);
        if (!STATUSES.contains(status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"不支持的项目状态："+status);
        if (!canTransition(project.getStatus(), status)) throw new BusinessException(ErrorCode.BAD_REQUEST,"项目状态不允许由 "+project.getStatus()+" 变更为 "+status);
        if (Project.STATUS_COMPLETED.equals(status) || Project.STATUS_CANCELLED.equals(status)) {
            if (db.queryForObject("select count(*) from resource_allocation where project_id=? and status in ('PLANNED','CONFIRMED')",Long.class,id)>0)
                throw new BusinessException(ErrorCode.BAD_REQUEST,"项目仍有生效分配，请先完结任务或撤销方案");
            if (Project.STATUS_COMPLETED.equals(status) && db.queryForObject("select count(*) from task where project_id=? and status in ('TODO','IN_PROGRESS')",Long.class,id)>0)
                throw new BusinessException(ErrorCode.BAD_REQUEST,"项目仍有未完结任务，不可标记完结");
        }
        // Project 实体 description 等为 ALWAYS 更新策略，定向 SQL 更稳妥 / targeted update avoids wiping ALWAYS columns
        db.update("update project set status=?,updated_at=now() where id=?", status, id);
        log.info("project status changed, id={}, {} -> {}", id, project.getStatus(), status);
        if (Project.STATUS_COMPLETED.equals(status)) notify.projectCompleted(id);
    }

    /** 删除项目（级联删除里程碑与任务）/ Delete project with milestones and tasks. */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        milestoneMapper.delete(new LambdaQueryWrapper<ProjectMilestone>()
                .eq(ProjectMilestone::getProjectId, id));
        taskMapper.delete(new LambdaQueryWrapper<Task>()
                .eq(Task::getProjectId, id));
        projectMapper.deleteById(id);
        log.info("project deleted, id={}", id);
    }

    private void apply(Project project, ProjectUpsertRequest request) {
        if(request.startDate()!=null && request.endDate()!=null && request.endDate().isBefore(request.startDate())) throw new BusinessException(ErrorCode.BAD_REQUEST,"项目结束日期早于开始日期");
        if(project.getId()!=null && db.queryForObject("select count(*) from task where project_id=? and ((?::date is not null and start_date < ?::date) or (?::date is not null and end_date > ?::date))",Long.class,project.getId(),request.startDate(),request.startDate(),request.endDate(),request.endDate())>0) throw new BusinessException(ErrorCode.BAD_REQUEST,"已有任务超出新的项目周期");
        project.setName(request.name());
        project.setDescription(request.description());
        project.setPriority(request.priority() == null ? Project.DEFAULT_PRIORITY : request.priority());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setManagerId(request.managerId());
        project.setLocation(request.location());
    }
}
