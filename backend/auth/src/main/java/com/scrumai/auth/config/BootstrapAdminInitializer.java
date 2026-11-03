package com.scrumai.auth.config;

import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {
  private final UserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final String username;
  private final String password;
  private final String email;
  private final String fullName;

  public BootstrapAdminInitializer(
      UserRepository users,
      PasswordEncoder passwordEncoder,
      @Value("${app.auth.bootstrap-admin.username:}") String username,
      @Value("${app.auth.bootstrap-admin.password:}") String password,
      @Value("${app.auth.bootstrap-admin.email:}") String email,
      @Value("${app.auth.bootstrap-admin.full-name:}") String fullName) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.username = username;
    this.password = password;
    this.email = email;
    this.fullName = fullName;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    boolean configured =
        StringUtils.hasText(username)
            && StringUtils.hasText(password)
            && StringUtils.hasText(email)
            && StringUtils.hasText(fullName);
    boolean partiallyConfigured =
        StringUtils.hasText(username)
            || StringUtils.hasText(password)
            || StringUtils.hasText(email)
            || StringUtils.hasText(fullName);
    if (!configured) {
      if (partiallyConfigured) {
        throw new IllegalStateException(
            "All APP_BOOTSTRAP_ADMIN_* values must be configured together");
      }
      return;
    }
    if (password.length() < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
      throw new IllegalStateException("Bootstrap admin password must be 12-72 UTF-8 bytes");
    }
    if (users.count() > 0) {
      return;
    }
    users.saveAndFlush(
        UserEntity.create(
            username.trim(),
            passwordEncoder.encode(password),
            fullName.trim(),
            email.trim().toLowerCase(java.util.Locale.ROOT),
            UserRole.ADMIN));
  }
}
