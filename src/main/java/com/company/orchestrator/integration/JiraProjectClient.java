package com.company.orchestrator.integration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
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
 * Jira 适配器（REST v2，Server / Data Center；Cloud 兼容）：用户名 + API Token 走
 * Basic，只填 Token 时走 Bearer PAT。任务按 statusCategory 归一状态，工时取原始估时
 * （秒转小时，缺失按 8h）。假设：Cloud 项目列表若返回 {values:[…]} 分页结构同样兼容。
 * Jira REST v2 adapter: Basic auth (username + token) or Bearer PAT; statuses
 * normalize via statusCategory, hours from timeoriginalestimate (default 8h).
 * Assumption: Cloud-style {values:[…]} pagination is tolerated on the list call.
 */
@Slf4j
@Service
public class JiraProjectClient implements ExternalProjectClient {

    private final RestClient http;
    private final ObjectMapper json;
    private final String base;
    private final boolean enabled;

    public JiraProjectClient(@Value("${app.sync.jira.enabled:false}") boolean enabled,
                             @Value("${app.sync.jira.base-url:}") String baseUrl,
                             @Value("${app.sync.jira.username:}") String username,
                             @Value("${app.sync.jira.api-token:}") String token,
                             ObjectMapper json) {
        this.enabled = enabled && !baseUrl.isBlank() && !token.isBlank();
        this.base = baseUrl.replaceAll("/$", "");
        this.json = json;
        this.http = this.enabled ? RestClient.builder().baseUrl(this.base)
                .defaultHeader("Authorization", username == null || username.isBlank()
                        ? "Bearer " + token
                        : "Basic " + Base64.getEncoder().encodeToString((username + ":" + token).getBytes(StandardCharsets.UTF_8)))
                .defaultHeader("Accept", "application/json")
                .requestFactory(factory())
                .build() : null;
    }

    @Override public String source() { return "JIRA"; }
    @Override public boolean enabled() { return enabled; }

    @Override
    public List<RemoteProject> listProjects() {
        var body = get("/rest/api/2/project");
        var array = body.has("values") ? body.get("values") : body;
        var projects = new ArrayList<RemoteProject>();
        for (var item : array) {
            if (!item.hasNonNull("key")) continue;
            var key = item.path("key").asText();
            projects.add(new RemoteProject(item.path("id").asText(), key, item.path("name").asText(),
                    item.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE", base + "/browse/" + key));
        }
        return projects;
    }

    @Override
    public ProjectWithTasks fetch(String externalId) {
        var meta = get("/rest/api/2/project/" + URLEncoder.encode(externalId, StandardCharsets.UTF_8));
        var tasks = new ArrayList<RemoteTask>();
        // startAt 分页拉全（上限 20 页）/ paged by startAt, capped at 20 pages
        for (int startAt = 0, page = 0; page < MAX_PAGES; page++) {
            var search = get("/rest/api/2/search?jql=" + URLEncoder.encode("project=" + externalId + " ORDER BY created ASC", StandardCharsets.UTF_8)
                    + "&maxResults=100&startAt=" + startAt + "&fields=summary,status,priority,timeoriginalestimate");
            var issues = search.path("issues");
            for (var issue : issues) {
                var fields = issue.path("fields");
                var key = issue.path("key").asText();
                tasks.add(new RemoteTask(issue.path("id").asText(), key, fields.path("summary").asText(key),
                        SyncMappers.jiraStatus(fields.path("status").path("statusCategory").path("key").asText()),
                        SyncMappers.priority(fields.path("priority").path("id").asInt(3)),
                        SyncMappers.hours(fields.path("timeoriginalestimate").isNumber() ? fields.path("timeoriginalestimate").asInt() : null, 8),
                        base + "/browse/" + key));
            }
            startAt += issues.size();
            if (issues.size() < 100 || startAt >= search.path("total").asInt(startAt)) break;
        }
        var key = meta.path("key").asText();
        return new ProjectWithTasks(new RemoteProject(meta.path("id").asText(), key, meta.path("name").asText(),
                meta.path("archived").asBoolean(false) ? "ARCHIVED" : "ACTIVE", base + "/browse/" + key), tasks);
    }

    private JsonNode get(String uri) {
        try {
            return json.readTree(http.get().uri(uri).retrieve().body(String.class));
        } catch (RestClientResponseException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Jira 调用失败：HTTP " + ex.getStatusCode().value() + "（检查地址与凭证）");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Jira 不可达：" + ex.getMessage());
        }
    }

    /** 分页上限：20 页 × 100 条 / page cap. */
    static final int MAX_PAGES = 20;

    /** 5s 连接 / 20s 读取超时 / shared request factory. */
    static JdkClientHttpRequestFactory factory() {
        var factory = new JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }
}
