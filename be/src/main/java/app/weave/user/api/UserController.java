package app.weave.user.api;

import app.weave.auth.domain.User;
import app.weave.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
        return toResponse(userService.getMe(userId));
    }

    @PatchMapping("/me")
    public UserMeResponse updateMe(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody MeUpdateRequest req) {
        return toResponse(userService.updateMe(userId, req.nickname(), req.bio(),
                parseTheme(req.theme()), req.shareBusyOnly(), req.shareTitles(),
                req.shareDetails()));
    }

    @GetMapping("/search")
    public List<SearchResult> search(@AuthenticationPrincipal Long userId,
            @RequestParam @Email @NotBlank String email) {
        User found = userService.searchByEmail(email, userId);
        return found == null ? List.of()
                : List.of(new SearchResult(found.getId(), found.getNickname(), found.getEmail(),
                        found.getProfileImage()));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(@AuthenticationPrincipal Long userId) {
        userService.deleteMe(userId);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, ResponseCookie.from("refresh_token", "")
                        .httpOnly(true).secure(true).sameSite("Strict")
                        .path("/api/auth").maxAge(0).build().toString())
                .build();
    }

    private static UserMeResponse toResponse(User u) {
        return new UserMeResponse(u.getId(), u.getEmail(), u.getName(), u.getNickname(),
                u.getProfileImage(), u.getProvider().name().toLowerCase(), u.getBio(),
                u.getTheme().name().toLowerCase(), u.isShareBusyOnly(), u.isShareTitles(),
                u.isShareDetails());
    }

    /** @Pattern이 통과한 값만 들어온다 — 소문자 계약 값을 enum으로 변환. */
    private static User.Theme parseTheme(String theme) {
        return theme == null ? null : User.Theme.valueOf(theme.toUpperCase());
    }

    public record MeUpdateRequest(@Size(min = 2, max = 20) String nickname,
            @Size(max = 60) String bio,
            @Pattern(regexp = "light|dark|system") String theme,
            Boolean shareBusyOnly, Boolean shareTitles, Boolean shareDetails) {
    }

    public record UserMeResponse(Long id, String email, String name, String nickname,
            String profileImage, String provider, String bio, String theme,
            boolean shareBusyOnly, boolean shareTitles, boolean shareDetails) {
    }

    public record SearchResult(Long id, String nickname, String email, String profileImage) {
    }
}
