package app.weave.auth.domain;

import app.weave.auth.domain.User.Provider;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

/** enum 상수(대문자) ↔ DB 소문자 값 — chk_users_provider 준수. */
@Converter(autoApply = true)
public class ProviderConverter implements AttributeConverter<Provider, String> {

    @Override
    public String convertToDatabaseColumn(Provider attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public Provider convertToEntityAttribute(String dbData) {
        return dbData == null ? null : Provider.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
