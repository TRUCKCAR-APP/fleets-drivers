package escuelaing.edu.co.truckcar.fleets_drivers.service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Horas de conducción del día calculadas en el servidor a partir de la telemetría (Lote 3), en lugar de confiar en el
 * número que envíe la app.
 * <p>
 * Con cada posición se suma el intervalo desde la anterior si en la anterior el camión iba a más de
 * {@value #MOVING_KMH} km/h: es el tiempo que pasó en movimiento. Un hueco de más de {@link #MAX_GAP} sin telemetría no
 * se cuenta (sin datos no se puede afirmar que siguió manejando). La cuenta se reinicia a medianoche en la hora local
 * de la operación: si el intervalo cruza la medianoche, solo cuenta la parte del día nuevo. Las horas van en
 * LocalDateTime de la zona del servicio, igual que lastUpdate.
 */
public record DrivingTime(LocalDate day, long seconds, double hoursToday, double remainingHours, boolean restRequired) {

    static final double MOVING_KMH = 5.0;
    static final Duration MAX_GAP = Duration.ofMinutes(5);
    /** Con 2 horas o menos de jornada disponible se avisa que debe descansar (igual que antes). */
    static final double REST_ALERT_HOURS = 2.0;

    /**
     * @param previousDay     día de la cuenta guardada (null si nunca se contó)
     * @param previousSeconds segundos de conducción guardados para ese día
     * @param previousAt      hora de la posición anterior (null si no hay)
     * @param previousSpeed   velocidad en la posición anterior, en km/h
     * @param now             hora de la posición nueva
     * @param maxDailyHours   jornada máxima diaria
     */
    public static DrivingTime next(LocalDate previousDay, Long previousSeconds, LocalDateTime previousAt,
                                   Double previousSpeed, LocalDateTime now, double maxDailyHours) {
        LocalDate today = now.toLocalDate();
        long seconds = today.equals(previousDay) && previousSeconds != null ? previousSeconds : 0L;
        if (previousAt != null && previousSpeed != null && previousSpeed > MOVING_KMH && previousAt.isBefore(now)
                && Duration.between(previousAt, now).compareTo(MAX_GAP) <= 0) {
            LocalDateTime from = previousAt.isBefore(today.atStartOfDay()) ? today.atStartOfDay() : previousAt;
            seconds += Duration.between(from, now).getSeconds();
        }
        return of(today, seconds, maxDailyHours);
    }

    /** La cuenta guardada vista en {@code today}: si es de otro día, la jornada de hoy empieza en cero. */
    public static DrivingTime asOf(LocalDate storedDay, Long storedSeconds, LocalDate today, double maxDailyHours) {
        long seconds = today.equals(storedDay) && storedSeconds != null ? storedSeconds : 0L;
        return of(today, seconds, maxDailyHours);
    }

    private static DrivingTime of(LocalDate day, long seconds, double maxDailyHours) {
        double hours = round1(seconds / 3600.0);
        double remaining = round1(Math.max(0.0, maxDailyHours - seconds / 3600.0));
        return new DrivingTime(day, seconds, hours, remaining, remaining <= REST_ALERT_HOURS);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
