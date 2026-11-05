package com.scrumai.fraud.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenRouterClientTest {
  private final ObjectMapper mapper = new ObjectMapper();
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsSchemaAndParsesProviderContent() throws Exception {
    AtomicReference<String> request = new AtomicReference<>();
    String json = mapper.writeValueAsString(AiAnalysisServiceTest.analysis("TX-1", "UNUSUAL_HOUR"));
    String body =
        mapper.writeValueAsString(
            Map.of(
                "choices",
                List.of(Map.of("finish_reason", "stop", "message", Map.of("content", json)))));
    OpenRouterClient client = client(200, body, request);
    assertEquals("Flagged by rules", client.analyze("evidence").fraudExplanation().summary());
    var sent = mapper.readTree(request.get());
    assertEquals("json_schema", sent.path("response_format").path("type").asText());
    assertTrue(sent.path("provider").path("require_parameters").asBoolean());
  }

  @Test
  void hidesProviderErrorBody() throws Exception {
    OpenRouterClient client = client(401, "SECRET-KEY provider failure", new AtomicReference<>());
    BusinessException error =
        assertThrows(BusinessException.class, () -> client.analyze("evidence"));
    assertEquals(ErrorCode.AI_PROVIDER_ERROR, error.errorCode());
    assertFalse(error.getMessage().contains("SECRET-KEY"));
  }

  @Test
  void rejectsMalformedProviderEnvelope() throws Exception {
    OpenRouterClient client = client(200, "not json", new AtomicReference<>());
    assertEquals(
        ErrorCode.AI_PROVIDER_ERROR,
        assertThrows(BusinessException.class, () -> client.analyze("evidence")).errorCode());
  }

  @Test
  void rejectsMalformedAnalysisJson() throws Exception {
    String body =
        mapper.writeValueAsString(
            Map.of(
                "choices",
                List.of(
                    Map.of("finish_reason", "stop", "message", Map.of("content", "not json")))));
    OpenRouterClient client = client(200, body, new AtomicReference<>());
    assertEquals(
        ErrorCode.AI_PROVIDER_ERROR,
        assertThrows(BusinessException.class, () -> client.analyze("evidence")).errorCode());
  }

  @Test
  void rejectsMissingConfiguration() {
    OpenRouterClient client =
        new OpenRouterClient("", "", "https://openrouter.ai/api/v1", 60, mapper);
    assertEquals(
        ErrorCode.AI_PROVIDER_UNAVAILABLE,
        assertThrows(BusinessException.class, () -> client.analyze("evidence")).errorCode());
  }

  private OpenRouterClient client(int status, String body, AtomicReference<String> request)
      throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/chat/completions",
        exchange -> {
          request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    return new OpenRouterClient(
        "test-key", "test/model", "http://127.0.0.1:" + server.getAddress().getPort(), 5, mapper);
  }
}
