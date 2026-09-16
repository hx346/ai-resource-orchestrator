package com.company.orchestrator.project.dto;

import jakarta.validation.constraints.NotBlank;

/** 项目状态流转请求 / Project status transition request. */
public record ProjectStatusRequest(@NotBlank String status) {
}
