package com.company.orchestrator.project.dto;

import jakarta.validation.constraints.NotBlank;

/** 任务状态流转请求 / Task status transition request. */
public record TaskStatusRequest(@NotBlank String status) {
}
