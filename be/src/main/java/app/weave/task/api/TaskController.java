package app.weave.task.api;

import app.weave.task.domain.Task;
import app.weave.task.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateRequest req,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Task task = service.create(userId, req.title(), req.dueDate(), idempotencyKey);
        return ResponseEntity.created(URI.create("/api/tasks/" + task.getId()))
                .body(toResponse(task));
    }

    @GetMapping
    public List<TaskResponse> list(@AuthenticationPrincipal Long userId) {
        return service.list(userId).stream().map(this::toResponse).toList();
    }

    @PatchMapping("/{id}")
    public TaskResponse update(@AuthenticationPrincipal Long userId,
            @PathVariable Long id, @RequestBody UpdateRequest req) {
        return toResponse(service.update(userId, id, req.title(), req.completed(), req.dueDate()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        service.delete(userId, id);
        return ResponseEntity.noContent().build();
    }

    private TaskResponse toResponse(Task t) {
        return new TaskResponse(t.getId(), t.getTitle(), t.isCompleted(), t.getDueDate().toString(),
                t.getCreatedAt().toString());
    }

    public record CreateRequest(@NotBlank @Size(max = 100) String title, LocalDate dueDate) {
    }

    public record UpdateRequest(@Size(max = 100) String title, Boolean completed,
            LocalDate dueDate) {
    }

    public record TaskResponse(Long id, String title, boolean completed, String dueDate,
            String createdAt) {
    }
}
