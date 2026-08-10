package com.gameio.user;

import com.gameio.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/users")
public class UserController {
    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public UserController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        return profileService.me(currentUser.id(authentication));
    }

    @PatchMapping("/me")
    UserResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        return profileService.update(currentUser.id(authentication), request);
    }

    @GetMapping("/{username}")
    ProfileResponse profile(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_]{3,24}") String username) {
        return profileService.findByUsername(username);
    }
}
