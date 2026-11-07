package app.weave;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class WeaveApplication {

    public static void main(String[] args) {
        // 도메인 전체가 Asia/Seoul wall-clock 의미론 — 배포 환경 JVM TZ(UTC 등)에
        // '오늘' 경계가 흔들리지 않도록 부팅 최초 시점에 고정한다.
        // jackson.time-zone·hibernate.jdbc.time_zone·JDBC serverTimezone과 같은 핀.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
        SpringApplication.run(WeaveApplication.class, args);
    }
}
