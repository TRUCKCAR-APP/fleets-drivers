package escuelaing.edu.co.truckcar.fleets_drivers.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** Jornada calculada con la telemetría: tiempo a más de 5 km/h, sin huecos largos, reiniciada a medianoche. */
class DrivingTimeTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 16);
    private static final LocalDateTime T0 = DAY.atTime(8, 0);

    @Test
    void timeMovingAboveFiveKmhIsAdded() {
        DrivingTime t = DrivingTime.next(DAY, 3600L, T0, 60.0, T0.plusMinutes(2), 9.0);

        assertThat(t.seconds()).isEqualTo(3600 + 120);
        assertThat(t.hoursToday()).isEqualTo(1.0);
        assertThat(t.remainingHours()).isEqualTo(8.0);
        assertThat(t.restRequired()).isFalse();
    }

    @Test
    void stoppedOrCrawlingDoesNotCount() {
        assertThat(DrivingTime.next(DAY, 600L, T0, 0.0, T0.plusMinutes(2), 9.0).seconds()).isEqualTo(600);
        assertThat(DrivingTime.next(DAY, 600L, T0, 5.0, T0.plusMinutes(2), 9.0).seconds()).isEqualTo(600);
        assertThat(DrivingTime.next(DAY, 600L, T0, null, T0.plusMinutes(2), 9.0).seconds()).isEqualTo(600);
    }

    @Test
    void aLongGapWithoutTelemetryIsNotAssumedToBeDriving() {
        assertThat(DrivingTime.next(DAY, 600L, T0, 80.0, T0.plusMinutes(5), 9.0).seconds()).isEqualTo(600 + 300);
        assertThat(DrivingTime.next(DAY, 600L, T0, 80.0, T0.plusMinutes(6), 9.0).seconds()).isEqualTo(600);
    }

    @Test
    void anOutOfOrderPointAddsNothing() {
        assertThat(DrivingTime.next(DAY, 600L, T0, 80.0, T0.minusMinutes(1), 9.0).seconds()).isEqualTo(600);
    }

    @Test
    void atMidnightTheCountRestartsAndOnlyTheNewDayPartCounts() {
        LocalDateTime beforeMidnight = DAY.atTime(23, 58);
        LocalDateTime after = DAY.plusDays(1).atTime(0, 1);

        DrivingTime t = DrivingTime.next(DAY, 8 * 3600L, beforeMidnight, 70.0, after, 9.0);

        assertThat(t.day()).isEqualTo(DAY.plusDays(1));
        assertThat(t.seconds()).isEqualTo(60);
    }

    @Test
    void withTwoHoursOrLessLeftRestIsRequired() {
        DrivingTime t = DrivingTime.next(DAY, 7 * 3600L - 60, T0, 70.0, T0.plusMinutes(1), 9.0);

        assertThat(t.hoursToday()).isEqualTo(7.0);
        assertThat(t.remainingHours()).isEqualTo(2.0);
        assertThat(t.restRequired()).isTrue();
        assertThat(DrivingTime.next(DAY, 12 * 3600L, T0, 0.0, T0.plusMinutes(1), 9.0).remainingHours()).isZero();
    }

    @Test
    void aCountFromAnotherDayReadsAsZeroToday() {
        DrivingTime yesterday = DrivingTime.asOf(DAY.minusDays(1), 8 * 3600L, DAY, 9.0);

        assertThat(yesterday.hoursToday()).isZero();
        assertThat(yesterday.remainingHours()).isEqualTo(9.0);
        assertThat(yesterday.restRequired()).isFalse();
        assertThat(DrivingTime.asOf(DAY, 3600L, DAY, 9.0).hoursToday()).isEqualTo(1.0);
        assertThat(DrivingTime.asOf(null, null, DAY, 9.0).seconds()).isZero();
    }
}
