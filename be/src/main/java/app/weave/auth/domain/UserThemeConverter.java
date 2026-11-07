package app.weave.auth.domain;

import app.weave.auth.domain.User.Theme;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** enum 상수(대문자) ↔ DB 소문자 값 — chk_users_theme 준수. */
@Converter(autoApply = true)
public class UserThemeConverter implements AttributeConverter<Theme, String> {

    @Override
    public String convertToDatabaseColumn(Theme attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Theme convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Theme.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
