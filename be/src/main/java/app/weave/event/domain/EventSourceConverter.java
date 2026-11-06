package app.weave.event.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** DDL CHECK가 소문자 'manual'/'proposal' — enum은 대문자라 변환기로 소문자 저장. */
@Converter(autoApply = true)
public class EventSourceConverter implements AttributeConverter<Event.Source, String> {

    @Override
    public String convertToDatabaseColumn(Event.Source attribute) {
        return attribute == null ? null : attribute.name().toLowerCase();
    }

    @Override
    public Event.Source convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Event.Source.valueOf(dbData.toUpperCase());
    }
}
