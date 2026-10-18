package com.harsh.interviewassistant.auth.dto;

import com.harsh.interviewassistant.user.UserResponse;

/** Shape follows OAuth2 token responses (access_token / token_type / expires_in) so clients find it familiar. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {

    public static AuthResponse bearer(String accessToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, "Bearer", expiresInSeconds, user);
    }
}
