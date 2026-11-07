package app.weave.task.service;

import app.weave.common.error.BusinessException;
import app.weave.common.error.ErrorCode;
import app.weave.task.domain.Task;
import app.weave.task.domain.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Task create(Long userId, String title, LocalDate dueDate, Task.Priority priority,
            String memo, String idempotencyKey) {
        // 멱등키 재요청은 기존 리소스 반환 (uk_tasks_idem)
        if (idempotencyKey != null) {
            Optional<Task> existing = repository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        return repository.save(new Task(userId, title,
                dueDate != null ? dueDate : LocalDate.now(), priority, memo, idempotencyKey));
    }

    @Transactional(readOnly = true)
    public List<Task> list(Long userId) {
        return repository.findByUserId(userId);
    }

    @Transactional
    public Task update(Long userId, Long id, String title, Boolean completed, LocalDate dueDate,
            Task.Priority priority, String memo) {
        Task task = findOwned(userId, id);
        task.update(title, completed, dueDate, priority, memo);
        return task;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(findOwned(userId, id));
    }

    /** 소유권 위반은 404로 응답 — 존재 여부를 새지 않는다. */
    private Task findOwned(Long userId, Long id) {
        return repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
    }
}
