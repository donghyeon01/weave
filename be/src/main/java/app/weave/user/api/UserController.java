package app.weave.user.api;

import app.weave.auth.domain.User;
import app.weave.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserMeResponse me(@AuthenticationPrincipal Long userId) {
        User u = userService.getMe(userId);
        return new UserMeResponse(u.getId(), u.getEmail(), u.getName(), u.getNickname(),
                u.getProfileImage(), u.getProvider().name().toLowerCase());
    }

    public record UserMeResponse(Long id, String email, String name, String nickname,
            String profileImage, String provider) {
    }
}
