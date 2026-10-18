package com.harsh.interviewassistant.user;

import com.harsh.interviewassistant.common.exception.ResourceNotFoundException;
import com.harsh.interviewassistant.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users")
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user")
    @Transactional(readOnly = true)
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        // The token is trusted for identity, but profile data comes fresh from the DB.
        // A token for a deleted user still authenticates (stateless trade-off) but gets a 404 here.
        return userRepository.findById(principal.getId())
                .map(UserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("User", principal.getId()));
    }
}
