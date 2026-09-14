package com.company.orchestrator.auth;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import com.company.orchestrator.common.result.Result;
@RestController @RequestMapping("/api/v1/auth") @RequiredArgsConstructor
public class AuthController {
    private final AuthService service;
    @GetMapping("/csrf") public Result<Map<String,String>> csrf(CsrfToken token) { return Result.ok(Map.of("token",token.getToken(),"headerName",token.getHeaderName())); }
    @GetMapping("/me") public Result<Map<String,Object>> me(Authentication auth) { return Result.ok(Map.of("username",auth.getName(),"roles",auth.getAuthorities().stream().map(a -> a.getAuthority().replace("ROLE_","")).toList())); }
    @GetMapping("/users") public Result<List<Map<String,Object>>> users() { return Result.ok(service.users()); }
    public record UserRequest(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,64}") String username,@NotBlank @Size(min=12,max=72) String password,@NotBlank String role) {}
    @PostMapping("/users") public Result<Void> create(@Valid @RequestBody UserRequest r) { service.create(r.username(),r.password(),r.role()); return Result.ok(); }
}
