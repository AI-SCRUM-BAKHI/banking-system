package com.scrumai.database.config;

import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatabaseConnectionConfig {

  @Bean
  @ConfigurationProperties("spring.datasource.hikari")
  public HikariDataSource dataSource(@Value("${spring.datasource.url}") String connectionUrl) {
    ParsedConnection parsedConnection = parseConnection(connectionUrl);
    return DataSourceBuilder.create()
        .type(HikariDataSource.class)
        .driverClassName("org.postgresql.Driver")
        .url(parsedConnection.url())
        .username(parsedConnection.username())
        .password(parsedConnection.password())
        .build();
  }

  private ParsedConnection parseConnection(String connectionUrl) {
    try {
      URI uri = URI.create(connectionUrl);
      if (!"postgresql".equals(uri.getScheme()) && !"postgres".equals(uri.getScheme())) {
        throw new IllegalArgumentException();
      }
      if (uri.getHost() == null
          || uri.getRawPath() == null
          || uri.getRawPath().length() < 2
          || uri.getRawUserInfo() == null
          || uri.getRawFragment() != null) {
        throw new IllegalArgumentException();
      }

      String rawUserInfo = uri.getRawUserInfo();
      int credentialSeparator = rawUserInfo.indexOf(':');
      if (credentialSeparator < 1 || credentialSeparator == rawUserInfo.length() - 1) {
        throw new IllegalArgumentException();
      }
      String username = decodeCredential(rawUserInfo.substring(0, credentialSeparator));
      String password = decodeCredential(rawUserInfo.substring(credentialSeparator + 1));
      if (username.isBlank() || password.isBlank()) {
        throw new IllegalArgumentException();
      }

      String jdbcUrl =
          "jdbc:postgresql://"
              + uri.getRawAuthority()
                  .substring(uri.getRawUserInfo() == null ? 0 : uri.getRawUserInfo().length() + 1)
              + uri.getRawPath()
              + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
      return new ParsedConnection(jdbcUrl, username, password);
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException(
          "DATABASE_URL must be a PostgreSQL URI with embedded username and password");
    }
  }

  private String decodeCredential(String value) {
    return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
  }

  private record ParsedConnection(String url, String username, String password) {}
}
