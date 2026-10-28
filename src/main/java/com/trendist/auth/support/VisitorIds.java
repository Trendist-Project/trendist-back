package com.trendist.auth.support;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

/** 방문자 ID: yyyyMMddHHmmss(UTC)-UUID v4, 51자(9.3). 클라이언트가 만든 값은 받지 않는다. */
public final class VisitorIds {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern VALID = Pattern.compile(
            "^\\d{14}-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private VisitorIds() {}

    public static String create(Clock clock) {
        return ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC).format(FMT) + "-" + UUID.randomUUID();
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }
}
