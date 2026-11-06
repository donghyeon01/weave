package app.weave.friend.api;

import app.weave.friend.service.FriendService;
import app.weave.friend.service.FriendService.FriendView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendService service;

    public FriendController(FriendService service) {
        this.service = service;
    }

    @GetMapping
    public List<FriendshipResponse> list(@AuthenticationPrincipal Long me) {
        return service.listFriends(me).stream().map(this::toResponse).toList();
    }

    @GetMapping("/requests/received")
    public List<FriendshipResponse> received(@AuthenticationPrincipal Long me) {
        return service.listReceived(me).stream().map(this::toResponse).toList();
    }

    @GetMapping("/requests/sent")
    public List<FriendshipResponse> sent(@AuthenticationPrincipal Long me) {
        return service.listSent(me).stream().map(this::toResponse).toList();
    }

    @PostMapping("/requests")
    public ResponseEntity<FriendshipResponse> request(@AuthenticationPrincipal Long me,
            @Valid @RequestBody SendRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(service.request(me, req.email())));
    }

    @PostMapping("/requests/{id}/accept")
    public FriendshipResponse accept(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        return toResponse(service.accept(me, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long me, @PathVariable Long id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    private FriendshipResponse toResponse(FriendView view) {
        var u = view.counterparty();
        return new FriendshipResponse(view.friendship().getId(),
                view.friendship().getStatus().name(),
                new UserSummary(u.getId(), u.getNickname(), u.getEmail(), u.getProfileImage()),
                view.friendship().getCreatedAt().toString());
    }

    public record SendRequest(@NotBlank String email) {
    }

    public record UserSummary(Long id, String nickname, String email, String profileImage) {
    }

    public record FriendshipResponse(Long friendshipId, String status, UserSummary user,
            String createdAt) {
    }
}
