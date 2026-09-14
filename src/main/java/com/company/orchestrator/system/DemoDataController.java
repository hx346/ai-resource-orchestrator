package com.company.orchestrator.system;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import com.company.orchestrator.common.result.Result;
@RestController @RequiredArgsConstructor
public class DemoDataController {
    private final DemoDataService service;
    @PostMapping("/api/v1/system/demo-data") public Result<Void> seed() { service.seed(); return Result.ok(); }
}
