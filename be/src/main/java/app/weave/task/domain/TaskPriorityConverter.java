package app.weave.task.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** enum 상수(대문자) ↔ DB 소문자 값 — chk_tasks_priority 준수. */
@Converter(autoApply = true)
public class TaskPriorityConverter implements AttributeConverter<Task.Priority, String> {

    @Override
    public String convertToDatabaseColumn(Task.Priority attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Task.Priority convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Task.Priority.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
