package com.harsh.interviewassistant.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Typed binding of app.jwt.* — validated at startup, so a missing secret fails fast
 * instead of on the first login.
 *
 * @param secret     Base64-encoded HMAC key, at least 256 bits for HS256
 * @param expiration access token lifetime, e.g. 24h
 * @param issuer     "iss" claim written into and required on every token
 */
@Validated
@ConfigurationProperties("app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @NotNull Duration expiration,
        @NotBlank String issuer) {
}
