package app.weave.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 감사 컬럼(created_at/updated_at) 채널은 JPA auditing —
 * DDL 디폴트는 앱 우회 쓰기 대비 방어선이다 (db-schema §4).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
