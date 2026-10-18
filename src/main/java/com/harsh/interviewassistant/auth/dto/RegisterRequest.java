package com.harsh.interviewassistant.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        // BCrypt only uses the first 72 bytes of a password, so longer ones are rejected rather than silently truncated
        @NotBlank @Size(min = 8, max = 72, message = "must be between 8 and 72 characters")
        String password,

        @NotBlank @Size(max = 100)
        String fullName) {

    /** Trim before bean validation runs, so " jane@example.com " is accepted rather than failing @Email. */
    public RegisterRequest {
        email = email == null ? null : email.trim();
        fullName = fullName == null ? null : fullName.trim();
    }

    @Override
    public String toString() {
        // Records print every component by default; keep the password out of logs
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + ", password=***]";
    }
}
