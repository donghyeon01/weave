package app.weave.scheduling.api;

import app.weave.scheduling.service.RecurrenceExpander;
import java.util.List;

/**
 * 정례 요청 — freq는 WEEKLY만, TIME 슬롯 전용. 추천(§3.8)·제안 생성(§3.7)이 같은
 * 형태를 쓴다. 값 검증은 {@link RecurrenceExpander.Spec#of}에 위임해 400으로 변환한다.
 */
public record RecurrenceRequest(String freq, Integer interval,
        List<Integer> byDay, Integer count) {
}
