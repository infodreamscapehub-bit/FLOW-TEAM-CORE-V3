package com.flowcity.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class TimeFormat {

    private TimeFormat() {
    }

    /** 0 -> "0m", 4500 -> "1h 15m", 200000 -> "2d 7h". */
    public static String duration(long seconds) {
        long s = Math.max(0, seconds);
        long days = s / 86400;
        long hours = (s % 86400) / 3600;
        long minutes = (s % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static String date(long millis, String pattern) {
        if (millis <= 0) {
            return "-";
        }
        return new SimpleDateFormat(pattern).format(new Date(millis));
    }
}
