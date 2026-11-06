package app.weave;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * capability-map의 모듈 경계를 강제한다.
 * 허용 의존: 전 모듈 auth·common 공통 + friend→user·notification + scheduling→friend·event·notification.
 * 이 표에 없는 feature 의존과 순환은 테스트가 깨진다.
 */
@AnalyzeClasses(packages = "app.weave", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final Set<String> FEATURES = Set.of(
            "auth", "user", "notification", "task", "event", "diary", "friend", "scheduling");

    /** 전 모듈 공통 허용 — auth(JWT·User)·common(에러·BaseTime)은 어디서나 소비 가능. */
    private static final Set<String> BASE = Set.of("auth", "common");

    /** feature별 추가 허용 — capability-map 엣지 그대로. */
    private static final Map<String, Set<String>> EXTRA = Map.of(
            "friend", Set.of("user", "notification"),
            "scheduling", Set.of("friend", "event", "notification"));

    private static ArchRule boundaryOf(String feature) {
        Set<String> forbidden = new HashSet<>(FEATURES);
        forbidden.remove(feature);
        forbidden.removeAll(BASE);
        forbidden.removeAll(EXTRA.getOrDefault(feature, Set.of()));
        String[] packages = forbidden.stream()
                .map(f -> "app.weave." + f + "..").toArray(String[]::new);
        return noClasses().that().resideInAPackage("app.weave." + feature + "..")
                .should().dependOnClassesThat().resideInAnyPackage(packages)
                .as(feature + "는 " + forbidden + "에 의존할 수 없다");
    }

    @ArchTest
    static final ArchRule 모듈_순환_없음 =
            slices().matching("app.weave.(*)..").should().beFreeOfCycles();

    @ArchTest
    static final ArchRule auth_경계 = boundaryOf("auth");
    @ArchTest
    static final ArchRule user_경계 = boundaryOf("user");
    @ArchTest
    static final ArchRule notification_경계 = boundaryOf("notification");
    @ArchTest
    static final ArchRule task_경계 = boundaryOf("task");
    @ArchTest
    static final ArchRule event_경계 = boundaryOf("event");
    @ArchTest
    static final ArchRule diary_경계 = boundaryOf("diary");
    @ArchTest
    static final ArchRule friend_경계 = boundaryOf("friend");
    @ArchTest
    static final ArchRule scheduling_경계 = boundaryOf("scheduling");
    @ArchTest
    static final ArchRule common_경계 =
            noClasses().that().resideInAPackage("app.weave.common..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            FEATURES.stream().map(f -> "app.weave." + f + "..")
                                    .toArray(String[]::new));
}
