package com.company.orchestrator.ai;
import java.util.*;
import java.time.LocalDate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.company.orchestrator.project.dto.TaskSkillRequirementRequest;
public record PlanDraft(@NotBlank @Size(max=4000) String summary,@NotEmpty @Size(max=100) List<@Valid DraftTask> tasks) {
    public record DraftTask(@NotBlank @Size(max=256) String name,@Size(max=8192) String description,
        @NotNull @Min(1) @Max(10000) Integer estimatedHours,@NotNull LocalDate startDate,@NotNull LocalDate endDate,
        @NotNull @Size(max=30) List<@Valid TaskSkillRequirementRequest> skills,
        @NotNull @Size(max=100) List<@Min(0) Integer> predecessorIndexes) {}
}
