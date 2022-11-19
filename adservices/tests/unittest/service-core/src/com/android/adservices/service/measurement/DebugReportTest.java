/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.filters.SmallTest;

import com.android.adservices.service.measurement.reporting.DebugReport;

import org.junit.Test;

import java.util.Set;

/** Unit tests for {@link DebugReport} */
@SmallTest
public final class DebugReportTest {

    private static final String TYPE = "trigger-event-deduplicated";
    private static final String BODY =
            " {\n"
                    + "      \"attribution_destination\": \"https://destination.example\",\n"
                    + "      \"source_event_id\": \"45623\"\n"
                    + "    }";

    @Test
    public void creation_success() {
        DebugReport debugReport = createExample1();
        assertEquals("1", debugReport.getId());
        assertEquals(TYPE, debugReport.getType());
        assertEquals(BODY, debugReport.getBody());
        assertEquals("2", debugReport.getEnrollmentId());
    }

    @Test
    public void testHashCode_equals() {
        final DebugReport debugReport1 = createExample1();
        final DebugReport debugReport2 = createExample1();
        final Set<DebugReport> debugReportSet1 = Set.of(debugReport1);
        final Set<DebugReport> debugReportSet2 = Set.of(debugReport2);
        assertEquals(debugReport1.hashCode(), debugReport2.hashCode());
        assertEquals(debugReport1, debugReport2);
        assertEquals(debugReportSet1, debugReportSet2);
    }

    @Test
    public void testHashCode_notEquals() {
        final DebugReport debugReport1 = createExample1();
        final DebugReport debugReport2 = createExample2();
        final Set<DebugReport> debugReportSet1 = Set.of(debugReport1);
        final Set<DebugReport> debugReportSet2 = Set.of(debugReport2);
        assertNotEquals(debugReport1.hashCode(), debugReport2.hashCode());
        assertNotEquals(debugReport1, debugReport2);
        assertNotEquals(debugReportSet1, debugReportSet2);
    }

    @Test
    public void testEqualsPass() {
        assertEquals(
                createExample1(),
                new DebugReport.Builder()
                        .setId("1")
                        .setType(TYPE)
                        .setBody(BODY)
                        .setEnrollmentId("2")
                        .build());
    }

    @Test
    public void testEqualsFail() {
        assertNotEquals(createExample1(), createExample2());
    }

    private DebugReport createExample1() {
        return new DebugReport.Builder()
                .setId("1")
                .setType(TYPE)
                .setBody(BODY)
                .setEnrollmentId("2")
                .build();
    }

    private DebugReport createExample2() {
        return new DebugReport.Builder()
                .setId("3")
                .setType(TYPE)
                .setBody(BODY)
                .setEnrollmentId("4")
                .build();
    }
}
