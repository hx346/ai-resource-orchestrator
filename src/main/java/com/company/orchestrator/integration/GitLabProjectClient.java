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
 * GitLab 适配器（REST v4，PAT 认证）：项目按 membership 过滤、per_page=100 翻页拉全，
 * issue 工时无原生字段默认 8h、优先级取 weight（钳 1–5）。
 * GitLab v4 adapter (PAT auth): membership projects paged at per_page=100;
 * issues carry no native estimate so hours default to 8 and priority maps from weight (1-5).
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
        var projects = new ArrayList<RemoteProject>();
        paged("/api/v4/projects?membership=true&simple=true&order_by=last_activity_at", item -> {
            if (item.hasNonNull("id"))
                projects.add(new RemoteProject(item.path("id").asText(), item.path("path").asText(""), item.path("name").asText(),
                        item.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE", item.path("web_url").asText("")));
        });
        return projects;
    }

    /** per_page=100 翻页拉全（上限 20 页）/ page through per_page=100 results. */
    private void paged(String path, java.util.function.Consumer<com.fasterxml.jackson.databind.JsonNode> consumer) {
        for (int page = 1; page <= JiraProjectClient.MAX_PAGES; page++) {
            var array = get(path + (path.contains("?") ? "&" : "?") + "per_page=100&page=" + page);
            int seen = 0;
            for (var item : array) { consumer.accept(item); seen++; }
            if (seen < 100) break;
        }
    }

    @Override
    public ProjectWithTasks fetch(String externalId) {
        var meta = get("/api/v4/projects/" + externalId);
        var tasks = new ArrayList<RemoteTask>();
        paged("/api/v4/projects/" + externalId + "/issues?state=all&order_by=created_at&sort=asc", issue -> {
            var iid = issue.path("iid").asText();
            tasks.add(new RemoteTask(iid, "#" + iid, issue.path("title").asText(""),
                    SyncMappers.gitlabStatus(issue.path("state").asText()),
                    SyncMappers.priority(issue.path("weight").isNumber() ? issue.path("weight").asInt() : null),
                    SyncMappers.hours(8), issue.path("web_url").asText("")));
        });
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
