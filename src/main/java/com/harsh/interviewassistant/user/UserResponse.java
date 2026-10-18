package com.harsh.interviewassistant.user;

import java.time.Instant;
import java.util.UUID;

/** Public view of a user. Deliberately has no password hash field, so it can never be serialized. */
public record UserResponse(UUID id, String email, String fullName, Role role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCreatedAt());
    }
}
