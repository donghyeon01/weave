package app.weave.scheduling.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * api-contract §3.8 공통 Slot — 추천 결과·제안 생성·알림 payload가 같은 형태를 쓴다.
 * "type" 프로퍼티로 다형 직렬화: TIME={type,date,startTime,endTime}, DATE={type,startDate,endDate}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Slot.Time.class, name = "TIME"),
    @JsonSubTypes.Type(value = Slot.Days.class, name = "DATE")
})
public sealed interface Slot {

    record Time(LocalDate date,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime) implements Slot {
    }

    record Days(LocalDate startDate, LocalDate endDate) implements Slot {
    }
}
