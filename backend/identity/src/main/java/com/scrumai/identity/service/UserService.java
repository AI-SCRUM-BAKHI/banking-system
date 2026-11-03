package com.scrumai.identity.service;

import com.scrumai.common.exception.BusinessException;
import com.scrumai.common.exception.ErrorCode;
import com.scrumai.identity.dto.ChangePasswordRequest;
import com.scrumai.identity.dto.CreateUserRequest;
import com.scrumai.identity.dto.ResetPasswordRequest;
import com.scrumai.identity.dto.UpdateUserRequest;
import com.scrumai.identity.dto.UserPageResponse;
import com.scrumai.identity.dto.UserResponse;
import com.scrumai.identity.entity.UserEntity;
import com.scrumai.identity.enums.UserRole;
import com.scrumai.identity.enums.UserStatus;
import com.scrumai.identity.mapper.UserMapper;
import com.scrumai.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {
  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  public UserResponse create(CreateUserRequest request) {
    String username = requiredText(request.username(), "Username");
    String email = requiredText(request.email(), "Email").toLowerCase(java.util.Locale.ROOT);
    String fullName = requiredText(request.fullName(), "Full name");
    validatePassword(request.password());
    if (repository.existsByUsername(username)) {
      throw conflict("Username already exists");
    }
    if (repository.existsByEmailIgnoreCase(email)) {
      throw conflict("Email already exists");
    }
    UserEntity user =
        UserEntity.create(
            username, passwordEncoder.encode(request.password()), fullName, email, request.role());
    try {
      return UserMapper.toResponse(repository.saveAndFlush(user));
    } catch (DataIntegrityViolationException exception) {
      throw conflict("Username or email already exists");
    }
  }

  @Transactional(readOnly = true)
  public UserPageResponse list(int page, int size, UserRole role, UserStatus status) {
    if (page < 0 || size < 1 || size > 100) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST, "Page must be >= 0 and size must be 1–100");
    }
    Specification<UserEntity> filter =
        (root, query, builder) -> {
          var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
          if (role != null) {
            predicates.add(builder.equal(root.get("role"), role));
          }
          if (status != null) {
            predicates.add(builder.equal(root.get("status"), status));
          }
          return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    Page<UserResponse> result =
        repository
            .findAll(filter, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
            .map(UserMapper::toResponse);
    return new UserPageResponse(
        result.getContent(), page, size, result.getTotalElements(), result.getTotalPages());
  }

  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    return UserMapper.toResponse(requireUser(id));
  }

  public UserResponse update(UUID id, UpdateUserRequest request) {
    UserEntity user = requireUser(id);
    if (request.fullName() == null && request.email() == null && request.role() == null) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "At least one field is required");
    }
    if (request.fullName() != null) {
      user.setFullName(requiredText(request.fullName(), "Full name"));
    }
    if (request.email() != null) {
      String email = requiredText(request.email(), "Email").toLowerCase(java.util.Locale.ROOT);
      if (repository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
        throw conflict("Email already exists");
      }
      user.setEmail(email);
    }
    if (request.role() != null) {
      user.setRole(request.role());
    }
    try {
      return UserMapper.toResponse(repository.saveAndFlush(user));
    } catch (DataIntegrityViolationException exception) {
      throw conflict("Email already exists");
    }
  }

  public UserResponse updateStatus(UUID id, UserStatus status) {
    UserEntity user = requireUser(id);
    user.setStatus(status);
    return UserMapper.toResponse(repository.saveAndFlush(user));
  }

  public void changeOwnPassword(UUID id, ChangePasswordRequest request) {
    UserEntity user = requireUser(id);
    if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.UNAUTHORIZED, "Current password is incorrect");
    }
    validatePassword(request.newPassword());
    if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, "New password must be different");
    }
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    repository.saveAndFlush(user);
  }

  public void resetPassword(UUID id, ResetPasswordRequest request) {
    UserEntity user = requireUser(id);
    validatePassword(request.newPassword());
    user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    repository.saveAndFlush(user);
  }

  private UserEntity requireUser(UUID id) {
    return repository
        .findById(id)
        .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "User not found"));
  }

  private static BusinessException conflict(String message) {
    return new BusinessException(ErrorCode.RESOURCE_CONFLICT, message);
  }

  private static String requiredText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, field + " must not be blank");
    }
    return value.trim();
  }

  private static void validatePassword(String password) {
    if (password == null
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72) {
      throw new BusinessException(
          ErrorCode.INVALID_REQUEST,
          "Password must contain at least 12 characters and at most 72 bytes");
    }
  }
}
