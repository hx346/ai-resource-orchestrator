package com.company.orchestrator.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.company.orchestrator.project.dto.MilestoneUpsertRequest;
import com.company.orchestrator.project.entity.ProjectMilestone;
import com.company.orchestrator.project.mapper.ProjectMilestoneMapper;

import lombok.RequiredArgsConstructor;

/** 项目里程碑管理 / Project milestone management. */
@Service
@RequiredArgsConstructor
public class ProjectMilestoneService {

    private final ProjectMilestoneMapper milestoneMapper;
    private final ProjectService projectService;

    public List<ProjectMilestone> listByProject(Long projectId) {
        return milestoneMapper.selectList(new LambdaQueryWrapper<ProjectMilestone>()
                .eq(ProjectMilestone::getProjectId, projectId)
                .orderByAsc(ProjectMilestone::getSortOrder));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long projectId, MilestoneUpsertRequest request) {
        projectService.requireExists(projectId);
        ProjectMilestone milestone = new ProjectMilestone();
        apply(milestone, projectId, request);
        milestone.setStatus(ProjectMilestone.STATUS_PENDING);
        milestoneMapper.insert(milestone);
        return milestone.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, MilestoneUpsertRequest request) {
        ProjectMilestone milestone = requireExists(id);
        apply(milestone, milestone.getProjectId(), request);
        milestoneMapper.updateById(milestone);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireExists(id);
        milestoneMapper.deleteById(id);
    }

    private ProjectMilestone requireExists(Long id) {
        ProjectMilestone milestone = milestoneMapper.selectById(id);
        if (milestone == null) {
            throw new BusinessException(ErrorCode.MILESTONE_NOT_FOUND, id);
        }
        return milestone;
    }

    private void apply(ProjectMilestone milestone, Long projectId, MilestoneUpsertRequest request) {
        milestone.setProjectId(projectId);
        milestone.setName(request.name());
        milestone.setDueDate(request.dueDate());
        milestone.setSortOrder(request.sortOrder());
    }
}
