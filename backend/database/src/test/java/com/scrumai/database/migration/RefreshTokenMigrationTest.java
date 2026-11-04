package com.scrumai.database.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefreshTokenMigrationTest {
  @Test
  void adoptsAnExistingRefreshTokenTableWithoutRemovingTokens() throws Exception {
    String migration;
    try (var resource =
        getClass().getResourceAsStream("/db/migration/V3__create_auth_refresh_tokens.sql")) {
      migration =
          new String(resource.readAllBytes(), StandardCharsets.UTF_8)
              .replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE")
              // Normalize PostgreSQL's UUID default syntax for H2's parser.
              .replace(
                  "PRIMARY KEY DEFAULT gen_random_uuid()", "DEFAULT RANDOM_UUID() PRIMARY KEY");
    }
    try (var connection =
            DriverManager.getConnection(
                "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL", "sa", "");
        var statement = connection.createStatement()) {
      statement.execute("CREATE TABLE users (id UUID PRIMARY KEY)");
      for (String sql : migration.split(";")) {
        if (!sql.isBlank()) {
          statement.execute(sql);
        }
      }
      statement.execute("INSERT INTO users VALUES ('00000000-0000-0000-0000-000000000001')");
      statement.execute(
          """
          INSERT INTO auth_refresh_tokens (user_id, token_hash, expires_at)
          VALUES ('00000000-0000-0000-0000-000000000001', 'existing-token', CURRENT_TIMESTAMP)
          """);
      for (String sql : migration.split(";")) {
        if (!sql.isBlank()) {
          statement.execute(sql);
        }
      }
      try (var rows = statement.executeQuery("SELECT token_hash FROM auth_refresh_tokens")) {
        rows.next();
        assertEquals("existing-token", rows.getString(1));
        assertFalse(rows.next());
      }
    }
  }
}
