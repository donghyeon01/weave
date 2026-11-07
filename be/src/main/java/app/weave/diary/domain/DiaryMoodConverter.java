package app.weave.diary.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** enum 상수(대문자) ↔ DB 소문자 값 — chk_diaries_mood 준수. */
@Converter(autoApply = true)
public class DiaryMoodConverter implements AttributeConverter<Diary.Mood, String> {

    @Override
    public String convertToDatabaseColumn(Diary.Mood attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Diary.Mood convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Diary.Mood.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
