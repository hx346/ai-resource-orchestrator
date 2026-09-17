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
 * GitHub 适配器（REST v3，PAT Bearer 认证）：externalId 为 owner/repo 全名，
 * /user/repos 翻页列出本人仓库；issues 接口混入 PR（含 pull_request 字段）须过滤；
 * 工时无原生字段默认 8h，优先级默认 3。
 * GitHub v3 adapter (PAT bearer auth): externalId is the owner/repo full name,
 * /user/repos pages owned repositories; the issues endpoint also returns pull
 * requests which are filtered out; hours default to 8, priority to 3.
 */
@Slf4j
@Service
public class GitHubProjectClient implements ExternalProjectClient {

    private final RestClient http;
    private final ObjectMapper json;
    private final boolean enabled;

    public GitHubProjectClient(@Value("${app.sync.github.enabled:false}") boolean enabled,
                               @Value("${app.sync.github.base-url:https://api.github.com}") String baseUrl,
                               @Value("${app.sync.github.token:}") String token,
                               ObjectMapper json) {
        this.enabled = enabled && !token.isBlank();
        this.json = json;
        this.http = this.enabled ? RestClient.builder().baseUrl(baseUrl.replaceAll("/$", ""))
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Accept", "application/vnd.github+json")
                .requestFactory(JiraProjectClient.factory())
                .build() : null;
    }

    @Override public String source() { return "GITHUB"; }
    @Override public boolean enabled() { return enabled; }

    @Override
    public List<RemoteProject> listProjects() {
        var projects = new ArrayList<RemoteProject>();
        paged("/user/repos?affiliation=owner&sort=pushed", item -> {
            if (item.hasNonNull("id"))
                projects.add(new RemoteProject(item.path("full_name").asText(), item.path("name").asText(""),
                        item.path("full_name").asText(), item.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE",
                        item.path("html_url").asText("")));
        });
        return projects;
    }

    @Override
    public ProjectWithTasks fetch(String externalId) {
        var meta = get("/repos/" + externalId);
        var tasks = new ArrayList<RemoteTask>();
        // issues 端点包含 PR：含 pull_request 字段的是 PR，跳过 / the issues endpoint also returns PRs; skip them
        paged("/repos/" + externalId + "/issues?state=all&sort=created&direction=asc", issue -> {
            if (issue.hasNonNull("pull_request")) return;
            var number = issue.path("number").asText();
            tasks.add(new RemoteTask(number, "#" + number, issue.path("title").asText(""),
                    SyncMappers.githubStatus(issue.path("state").asText()),
                    SyncMappers.priority(null), SyncMappers.hours(8), issue.path("html_url").asText("")));
        });
        return new ProjectWithTasks(new RemoteProject(meta.path("full_name").asText(), meta.path("name").asText(""),
                meta.path("full_name").asText(), meta.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE",
                meta.path("html_url").asText("")), tasks);
    }

    /** per_page=100 翻页拉全（上限 20 页）/ page through per_page=100 results. */
    private void paged(String path, java.util.function.Consumer<JsonNode> consumer) {
        for (int page = 1; page <= JiraProjectClient.MAX_PAGES; page++) {
            var array = get(path + (path.contains("?") ? "&" : "?") + "per_page=100&page=" + page);
            int seen = 0;
            for (var item : array) { consumer.accept(item); seen++; }
            if (seen < 100) break;
        }
    }

    private JsonNode get(String uri) {
        try {
            return json.readTree(http.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "GitHub 调用失败：HTTP " + ex.getStatusCode().value() + "（检查地址与令牌）");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "GitHub 不可达：" + ex.getMessage());
        }
    }
}
