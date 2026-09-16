package com.company.orchestrator.integration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.company.orchestrator.common.exception.BusinessException;
import com.company.orchestrator.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

/**
 * 禅道适配器（OpenAPI v1，ZenTao 18+，Token 头认证）。假设：项目与任务接口为
 * /api.php/v1/projects 与 /api.php/v1/projects/{id}/tasks，返回 {projects|tasks:[…]}；
 * 不同版本路径或结构有差异时可通过 app.sync.zentao.path-projects / path-tasks 覆盖。
 * ZenTao OpenAPI v1 adapter (Token header). Assumed endpoints
 * /api.php/v1/projects and /api.php/v1/projects/{id}/tasks returning
 * {projects|tasks:[…]}; both paths are overridable via configuration for
 * version differences.
 */
@Slf4j
@Service
public class ZenTaoProjectClient implements ExternalProjectClient {

    private final RestClient http;
    private final ObjectMapper json;
    private final String pathProjects;
    private final String pathTasks;
    private final boolean enabled;

    public ZenTaoProjectClient(@Value("${app.sync.zentao.enabled:false}") boolean enabled,
                               @Value("${app.sync.zentao.base-url:}") String baseUrl,
                               @Value("${app.sync.zentao.token:}") String token,
                               @Value("${app.sync.zentao.path-projects:/api.php/v1/projects}") String pathProjects,
                               @Value("${app.sync.zentao.path-tasks:/api.php/v1/projects/{id}/tasks}") String pathTasks,
                               ObjectMapper json) {
        this.enabled = enabled && !baseUrl.isBlank() && !token.isBlank();
        this.pathProjects = pathProjects;
        this.pathTasks = pathTasks;
        this.json = json;
        this.http = this.enabled ? RestClient.builder().baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader("Token", token)
                .defaultHeader("Accept", "application/json")
                .requestFactory(JiraProjectClient.factory())
                .build() : null;
    }

    @Override public String source() { return "ZENTAO"; }
    @Override public boolean enabled() { return enabled; }

    @Override
    public List<RemoteProject> listProjects() {
        var projects = new ArrayList<RemoteProject>();
        // limit=100 翻页拉全（上限 20 页）/ page through limit=100 results
        for (int page = 1; page <= JiraProjectClient.MAX_PAGES; page++) {
            var body = get(pathProjects + "?limit=100&page=" + page);
            var array = body.has("projects") ? body.get("projects") : body.has("data") ? body.get("data") : body;
            int seen = 0;
            for (var item : array) {
                if (!item.hasNonNull("id")) continue;
                projects.add(new RemoteProject(item.path("id").asText(), item.path("code").asText(""), item.path("name").asText(),
                        item.path("status").asText(""), null));
                seen++;
            }
            if (seen < 100) break;
        }
        return projects;
    }

    @Override
    public ProjectWithTasks fetch(String externalId) {
        var project = listProjects().stream().filter(p -> p.externalId().equals(externalId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "禅道项目不存在：" + externalId));
        var tasks = new ArrayList<RemoteTask>();
        // limit=100 翻页拉全（上限 20 页）/ page through limit=100 results
        for (int page = 1; page <= JiraProjectClient.MAX_PAGES; page++) {
            var body = get(pathTasks.replace("{id}", externalId) + "?limit=100&page=" + page);
            var array = body.has("tasks") ? body.get("tasks") : body;
            int seen = 0;
            for (var item : array) {
                if (!item.hasNonNull("id")) continue;
                int estimate = (int) Math.round(item.path("estimate").asDouble(0));
                tasks.add(new RemoteTask(item.path("id").asText(), "T" + item.path("id").asText(), item.path("name").asText(""),
                        SyncMappers.genericStatus(item.path("status").asText()),
                        SyncMappers.zentaoPriority(item.path("pri").isNumber() ? item.path("pri").asInt() : null),
                        SyncMappers.hours(estimate == 0 ? 8 : estimate), null));
                seen++;
            }
            if (seen < 100) break;
        }
        return new ProjectWithTasks(project, tasks);
    }

    private JsonNode get(String uri) {
        try {
            return json.readTree(http.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禅道调用失败：HTTP " + ex.getStatusCode().value() + "（检查地址与 Token）");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "禅道不可达：" + ex.getMessage());
        }
    }
}
