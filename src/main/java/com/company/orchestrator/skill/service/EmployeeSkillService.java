package com.company.orchestrator.skill.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.company.orchestrator.common.enums.SkillSource;
import com.company.orchestrator.employee.service.EmployeeService;
import com.company.orchestrator.skill.dto.EmployeeSkillUpsertRequest;
import com.company.orchestrator.skill.dto.EmployeeSkillView;
import com.company.orchestrator.skill.entity.EmployeeSkill;
import com.company.orchestrator.skill.entity.Skill;
import com.company.orchestrator.skill.mapper.EmployeeSkillMapper;
import com.company.orchestrator.skill.mapper.SkillMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 员工技能画像管理 / Employee skill profile management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmployeeSkillService {

    private final EmployeeSkillMapper employeeSkillMapper;
    private final SkillMapper skillMapper;
    private final EmployeeService employeeService;

    /** 查询员工技能（带技能名，两次查询避免 join 映射）/ List employee skills with skill names. */
    public List<EmployeeSkillView> listByEmployee(Long employeeId) {
        List<EmployeeSkill> employeeSkills = employeeSkillMapper.selectList(
                new LambdaQueryWrapper<EmployeeSkill>()
                        .eq(EmployeeSkill::getEmployeeId, employeeId)
                        .orderByDesc(EmployeeSkill::getLevel));
        if (employeeSkills.isEmpty()) {
            return List.of();
        }
        Map<Long, Skill> skillById = skillMapper.selectBatchIds(
                        employeeSkills.stream().map(EmployeeSkill::getSkillId).toList())
                .stream()
                .collect(Collectors.toMap(Skill::getId, Function.identity()));
        return employeeSkills.stream()
                .map(es -> EmployeeSkillView.of(es,
                        skillById.containsKey(es.getSkillId())
                                ? skillById.get(es.getSkillId()).getName()
                                : null))
                .toList();
    }

    /** 全量替换员工技能（AI 识别后确认 / 人工维护均走此入口）/ Replace all skills of an employee. */
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(Long employeeId, List<EmployeeSkillUpsertRequest> requests) {
        employeeService.requireExists(employeeId);
        requests.forEach(request -> skillMapper.selectById(request.skillId()));

        employeeSkillMapper.delete(new LambdaQueryWrapper<EmployeeSkill>()
                .eq(EmployeeSkill::getEmployeeId, employeeId));
        for (EmployeeSkillUpsertRequest request : requests) {
            EmployeeSkill employeeSkill = new EmployeeSkill();
            employeeSkill.setEmployeeId(employeeId);
            employeeSkill.setSkillId(request.skillId());
            employeeSkill.setLevel(request.level());
            employeeSkill.setExperienceMonths(request.experienceMonths() == null ? 0 : request.experienceMonths());
            employeeSkill.setSource(request.source() == null ? SkillSource.SELF : request.source());
            employeeSkill.setConfidence(request.confidence());
            employeeSkill.setVerified(Boolean.FALSE);
            employeeSkill.setLastUsedAt(request.lastUsedAt());
            employeeSkillMapper.insert(employeeSkill);
        }
        log.info("employee skills replaced, employeeId={}, size={}", employeeId, requests.size());
    }
}
