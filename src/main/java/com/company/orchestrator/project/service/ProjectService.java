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
