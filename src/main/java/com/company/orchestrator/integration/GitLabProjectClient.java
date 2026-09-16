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
 * GitLab 适配器（REST v4，PAT 认证）：项目按 membership 过滤，issue 工时无原生字段，
 * 默认 8h、优先级取 weight（钳 1–5）。MVP 只取前 100 条 / 页。
 * GitLab v4 adapter (PAT auth): membership projects; issues carry no native
 * estimate so hours default to 8 and priority maps from weight (1-5).
 */
@Slf4j
@Service
public class GitLabProjectClient implements ExternalProjectClient {

    private final RestClient http;
    private final ObjectMapper json;
    private final boolean enabled;

    public GitLabProjectClient(@Value("${app.sync.gitlab.enabled:false}") boolean enabled,
                               @Value("${app.sync.gitlab.base-url:}") String baseUrl,
                               @Value("${app.sync.gitlab.token:}") String token,
                               ObjectMapper json) {
        this.enabled = enabled && !baseUrl.isBlank() && !token.isBlank();
        this.json = json;
        this.http = this.enabled ? RestClient.builder().baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader("PRIVATE-TOKEN", token)
                .defaultHeader("Accept", "application/json")
                .requestFactory(JiraProjectClient.factory())
                .build() : null;
    }

    @Override public String source() { return "GITLAB"; }
    @Override public boolean enabled() { return enabled; }

    @Override
    public List<RemoteProject> listProjects() {
        var array = get("/api/v4/projects?membership=true&simple=true&per_page=100&order_by=last_activity_at");
        var projects = new ArrayList<RemoteProject>();
        for (var item : array) {
            if (!item.hasNonNull("id")) continue;
            projects.add(new RemoteProject(item.path("id").asText(), item.path("path").asText(""), item.path("name").asText(),
                    item.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE", item.path("web_url").asText("")));
        }
        return projects;
    }

    @Override
    public ProjectWithTasks fetch(String externalId) {
        var meta = get("/api/v4/projects/" + externalId);
        var issues = get("/api/v4/projects/" + externalId + "/issues?per_page=100&state=all&order_by=created_at&sort=asc");
        var tasks = new ArrayList<RemoteTask>();
        for (var issue : issues) {
            var iid = issue.path("iid").asText();
            tasks.add(new RemoteTask(iid, "#" + iid, issue.path("title").asText(""),
                    SyncMappers.gitlabStatus(issue.path("state").asText()),
                    SyncMappers.priority(issue.path("weight").isNumber() ? issue.path("weight").asInt() : null),
                    SyncMappers.hours(8), issue.path("web_url").asText("")));
        }
        return new ProjectWithTasks(new RemoteProject(meta.path("id").asText(), meta.path("path").asText(""), meta.path("name").asText(),
                meta.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE", meta.path("web_url").asText("")), tasks);
    }

    private JsonNode get(String uri) {
        try {
            return json.readTree(http.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "GitLab 调用失败：HTTP " + ex.getStatusCode().value() + "（检查地址与令牌）");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "GitLab 不可达：" + ex.getMessage());
        }
    }
}
