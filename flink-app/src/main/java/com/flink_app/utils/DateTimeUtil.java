package com.flink_app.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset; // Or ZoneId.systemDefault() if you prefer local time
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

    // Formatter for "yyyy-MM-dd HH:mm:ss.SSS"
    private static final DateTimeFormatter MS_ACCURACY_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * Gets the current UTC timestamp formatted to millisecond accuracy.
     * @return Formatted current timestamp string.
     */
    public static String getCurrentFormattedUtcTimestampMs() {
        return LocalDateTime.now(ZoneOffset.UTC).format(MS_ACCURACY_FORMATTER);
    }

    /**
     * Converts a timestamp in microseconds since epoch to a formatted UTC datetime string
     * with millisecond accuracy.
     *
     * @param timestampMicroseconds The timestamp in microseconds.
     * @return Formatted datetime string, or null if input is null.
     */
    public static String formatMicrosecondsToUtcTimestampMs(Long timestampMicroseconds) {
        if (timestampMicroseconds == null) {
            return null;
        }
        // Convert microseconds to milliseconds and nanoseconds for Instant

        // Correct way to get nanos ensuring we only care about the millisecond part for formatting
        // For formatting to SSS, we just need the Instant accurately representing the microsecond time.
        // The formatter will handle the truncation to milliseconds.
        long epochSeconds = timestampMicroseconds / 1_000_000L;
        long nanoAdjustment = (timestampMicroseconds % 1_000_000L) * 1_000L; // Convert micro of second to nano of second
        Instant preciseInstant = Instant.ofEpochSecond(epochSeconds, nanoAdjustment);


        return LocalDateTime.ofInstant(preciseInstant, ZoneOffset.UTC).format(MS_ACCURACY_FORMATTER);
    }
    public static long parseFormattedUtcTimestampToEpochMs(String formattedTimestamp) {
        return Instant.parse(formattedTimestamp).toEpochMilli();
    }    
}