package com.aloc.sai.timetable;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

// 학교 건물·과목 동기화. 과목이 바뀌는 건 수강신청·정정 때뿐이라 학기 시작일 앞뒤 2주 동안만 매일 05시(KST)에 맞춘다.
// 계절학기도 같다. 현재 학기 과목이 하나도 없으면(첫 배포) 서버가 뜬 직후에도 한 번 동기화한다.
// 테스트에서는 uos.sync.enabled=false로 끈다.
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "uos.sync.enabled", havingValue = "true")
public class CourseSyncScheduler {
    private static final int SYNC_WINDOW_WEEKS = 2;

    private final UosApiClient uosApiClient;
    private final CourseSyncService courseSyncService;

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Seoul")
    public void syncDaily() {
        List<AcademicTerm> terms = termsToSync(LocalDate.now(AcademicTerm.KST));
        if (!terms.isEmpty()) {
            sync(terms);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void syncIfEmpty() {
        AcademicTerm current = AcademicTerm.current();
        if (!courseSyncService.hasCourses(current)) {
            List<AcademicTerm> terms = new ArrayList<>(List.of(current));
            termsToSync(LocalDate.now(AcademicTerm.KST)).stream().filter(term -> !term.equals(current)).forEach(terms::add);
            sync(terms);
        }
    }

    // 시작일 2주 전 ~ 2주 후 사이에 있는 학기. 현재 학기와 그 뒤 두 학기 중에서 고른다.
    static List<AcademicTerm> termsToSync(LocalDate today) {
        AcademicTerm current = AcademicTerm.of(today);
        return Stream.of(current, current.next(), current.next().next())
                .filter(term -> !today.isBefore(term.startDate().minusWeeks(SYNC_WINDOW_WEEKS))
                        && today.isBefore(term.startDate().plusWeeks(SYNC_WINDOW_WEEKS)))
                .toList();
    }

    // 학기별로 따로 처리해 한 학기가 실패해도 다른 학기는 동기화한다.
    private void sync(List<AcademicTerm> terms) {
        try {
            courseSyncService.syncBuildings();
        } catch (RuntimeException e) {
            log.error("건물 동기화에 실패했습니다.", e);
        }

        for (AcademicTerm term : terms) {
            try {
                List<UosApiClient.UosCourse> rows = uosApiClient.getCourses(term);
                CourseSyncService.SyncResult result = courseSyncService.saveCourses(term, rows, LocalDateTime.now());
                log.info("{}년 {} 과목 동기화: 추가 {}, 갱신 {}, 제외 {}, 수업 시간 없음 {}",
                        term.academicYear(), term.semester(), result.created(), result.updated(),
                        result.skipped(), result.withoutSchedule());
            } catch (RuntimeException e) {
                log.error("{}년 {} 과목 동기화에 실패했습니다: {}", term.academicYear(), term.semester(), e.getMessage());
            }
        }
    }
}
