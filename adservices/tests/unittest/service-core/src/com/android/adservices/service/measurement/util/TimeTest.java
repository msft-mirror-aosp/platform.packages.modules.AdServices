/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.adservices.service.measurement.util;

import static com.android.adservices.service.measurement.util.Time.roundDownToDay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class TimeTest {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    public void roundDownToDay_oneSecondAfterStartOfDay() {
        LocalDateTime dateTime = getUtcLocalDateTime("2022-01-15 00:00:01");

        // 2022-01-15T00:00:01Z -> 1642204801000
        long timestamp = TimeUnit.SECONDS.toMillis(dateTime.toEpochSecond(ZoneOffset.UTC));
        long timestampRounded = roundDownToDay(timestamp);

        assertEquals(1642204800000L, timestampRounded);
    }

    @Test
    public void roundDownToDay_oneSecondBeforeNextDay() {
        LocalDateTime dateTime = getUtcLocalDateTime("2022-01-15 23:59:59");

        // 2022-01-15T23:59:59Z -> 1642319999000
        long timestamp = TimeUnit.SECONDS.toMillis(dateTime.toEpochSecond(ZoneOffset.UTC));
        long timestampRounded = roundDownToDay(timestamp);

        assertEquals(1642204800000L, timestampRounded);
    }

    private static LocalDateTime getUtcLocalDateTime(String text) {
        return LocalDateTime.parse(text, FORMATTER).atOffset(ZoneOffset.UTC).toLocalDateTime();
    }
}
