package app.weave.friend.api;

import app.weave.friend.service.FriendGroupService;
import app.weave.friend.service.FriendGroupService.GroupView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 친구 그룹 CRUD — GROUP 공개 범위의 멤버십 관리 (api-contract §3.6 확장). */
@RestController
@RequestMapping("/api/friend-groups")
public class FriendGroupController {

    private final FriendGroupService service;

    public FriendGroupController(FriendGroupService service) {
        this.service = service;
    }

    @GetMapping
    public List<GroupResponse> list(@AuthenticationPrincipal Long me) {
        return service.myGroups(me).stream().map(this::toResponse).toList();
    }

    @PostMapping
    public ResponseEntity<GroupResponse> create(@AuthenticationPrincipal Long me,
            @Valid @RequestBody GroupRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(service.create(me, req.name(), req.memberIds())));
    }

    @PatchMapping("/{id}")
    public GroupResponse rename(@AuthenticationPrincipal Long me, @PathVariable Long id,
            @Valid @RequestBody RenameRequest req) {
        return toResponse(service.rename(me, id, req.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long me,
            @PathVariable Long id) {
        service.delete(me, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/members")
    public GroupResponse replaceMembers(@AuthenticationPrincipal Long me,
            @PathVariable Long id, @RequestBody MembersRequest req) {
        return toResponse(service.replaceMembers(me, id, req.memberIds()));
    }

    private GroupResponse toResponse(GroupView view) {
        return new GroupResponse(view.groupId(), view.name(), view.memberIds());
    }

    public record GroupRequest(@NotBlank @Size(max = 30) String name,
            List<Long> memberIds) {
    }

    public record RenameRequest(@NotBlank @Size(max = 30) String name) {
    }

    /** memberIds 생략은 빈 배열과 동치 — 전량 교체로 그룹을 비운다. */
    public record MembersRequest(List<Long> memberIds) {
    }

    public record GroupResponse(Long groupId, String name, List<Long> memberIds) {
    }
}
