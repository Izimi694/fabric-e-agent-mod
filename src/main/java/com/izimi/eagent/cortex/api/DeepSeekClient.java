package com.izimi.eagent.cortex.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.izimi.eagent.hippocampus.MemoryEntry;
import com.izimi.eagent.state.PlayerState;
import com.izimi.eagent.cortex.task.Task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class DeepSeekClient implements AIClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final Gson GSON = new Gson();
    private final HttpClient httpClient;
    private final AIConfig config;
    private volatile long lastCallTime = 0;
    private static final long MIN_CALL_INTERVAL_MS = 2000;

    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private static final int MAX_RETRIES = 5;
    private static final long BASE_RETRY_DELAY_MS = 1000;
    private final ScheduledExecutorService retryExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "DeepSeekClient-retry");
        t.setDaemon(true);
        return t;
    });

    public DeepSeekClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build(), AIConfig.load());
    }

    DeepSeekClient(HttpClient httpClient, AIConfig config) {
        this.httpClient = httpClient;
        this.config = config;
    }

    public void resetCircuitBreaker() {
        circuitOpen.set(false);
        consecutiveFailures.set(0);
        LOGGER.info("[DeepSeekClient] Circuit breaker manually reset");
    }

    public boolean isCircuitOpen() {
        return circuitOpen.get();
    }

    @Override
    public boolean isConfigured() {
        return config.isConfigured();
    }

    @Override
    public boolean testConnection() {
        try {
            var response = sendMessage(List.of(
                    AIMessage.system("回复'ok'"),
                    AIMessage.user("ping")
            )).get(10, java.util.concurrent.TimeUnit.SECONDS);
            return response != null && !response.isEmpty();
        } catch (Exception e) {
            LOGGER.warn("[DeepSeekClient] 连接测试失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void setApiKey(String key) {
        config.setApiKey(key);
    }

    @Override
    public void setApiModel(String model) {
        config.setApiModel(model);
    }

    @Override
    public CompletableFuture<AIResponse> planTask(String playerMessage, PlayerState state,
                                                   Task activeTask, List<MemoryEntry> recentMemories,
                                                   Map<String, Double> preferences) {
        List<AIMessage> messages = AIRequest.buildPlanningRequest(
                playerMessage, state, activeTask, recentMemories, preferences);
        return sendMessage(messages);
    }

    @Override
    public CompletableFuture<AIResponse> generateMemory(Task completedTask, PlayerState state) {
        List<AIMessage> messages = AIRequest.buildMemoryRequest(completedTask, state);
        return sendMessage(messages);
    }

    @Override
    public CompletableFuture<AIResponse> sendMessage(List<AIMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            if (circuitOpen.get()) {
                LOGGER.warn("[DeepSeekClient] Circuit breaker open, skipping LLM call");
                return AIResponse.empty();
            }
            return sendWithRetry(messages, 0);
        }, retryExecutor);
    }

    private AIResponse sendWithRetry(List<AIMessage> messages, int attempt) {
        try {
            long now = System.currentTimeMillis();
            long waitTime = MIN_CALL_INTERVAL_MS - (now - lastCallTime);
            if (waitTime > 0) {
                Thread.sleep(waitTime);
            }
            lastCallTime = System.currentTimeMillis();

            JsonObject body = new JsonObject();
            body.addProperty("model", config.apiModel);

            JsonArray msgArray = new JsonArray();
            for (AIMessage msg : messages) {
                JsonObject obj = new JsonObject();
                obj.addProperty("role", msg.role);
                obj.addProperty("content", msg.content);
                msgArray.add(obj);
            }
            body.add("messages", msgArray);

            JsonObject responseFormat = new JsonObject();
            responseFormat.addProperty("type", "json_object");
            body.add("response_format", responseFormat);

            body.addProperty("temperature", 0.7);
            body.addProperty("max_tokens", 1024);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getFullEndpoint()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
                long delayMs = BASE_RETRY_DELAY_MS;
                if (retryAfter != null) {
                    try {
                        delayMs = Long.parseLong(retryAfter) * 1000;
                    } catch (NumberFormatException e) {
                        LOGGER.warn("Retry-After 头解析失败: '{}' — {}", retryAfter, e.getMessage());
                    }
                }
                return handleRetry(messages, attempt, delayMs);
            }

            if (response.statusCode() != 200) {
                LOGGER.error("[DeepSeekClient] API 返回错误 {}: {}", response.statusCode(), response.body());
                return handleRetry(messages, attempt, BASE_RETRY_DELAY_MS * (1L << attempt));
            }

            consecutiveFailures.set(0);
            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return AIResponse.empty();
        } catch (Exception e) {
            LOGGER.error("[DeepSeekClient] 请求失败: {}", e.getMessage());
            return handleRetry(messages, attempt, BASE_RETRY_DELAY_MS * (1L << attempt));
        }
    }

    private AIResponse handleRetry(List<AIMessage> messages, int attempt, long delayMs) {
        int failures = consecutiveFailures.incrementAndGet();
        if (attempt >= MAX_RETRIES || failures >= MAX_RETRIES) {
            circuitOpen.set(true);
            LOGGER.error("[DeepSeekClient] Max retries reached, circuit breaker opened");
            return AIResponse.empty();
        }
        LOGGER.warn("[DeepSeekClient] Retry {}/{} after {}ms", attempt + 1, MAX_RETRIES, delayMs);
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return sendWithRetry(messages, attempt + 1);
    }

    static AIResponse parseResponse(String body) {
        try {
            JsonObject root = GSON.fromJson(body, JsonObject.class);
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                LOGGER.warn("[DeepSeekClient] 响应无choices");
                return AIResponse.empty();
            }

            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            String content = message.get("content").getAsString();

            content = content.trim();
            if (content.startsWith("```json")) {
                content = content.substring(7);
            }
            if (content.startsWith("```")) {
                content = content.substring(3);
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3);
            }
            content = content.trim();

            return GSON.fromJson(content, AIResponse.class);
        } catch (Exception e) {
            LOGGER.error("[DeepSeekClient] 解析响应失败: {} -- body: {}",
                    e.getMessage(), body.substring(0, Math.min(200, body.length())));
            return AIResponse.empty();
        }
    }
}
