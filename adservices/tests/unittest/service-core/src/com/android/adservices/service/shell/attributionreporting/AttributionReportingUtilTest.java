/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.adservices.service.shell.attributionreporting;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.AdServicesMockitoTestCase;

import org.junit.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

public class AttributionReportingUtilTest extends AdServicesMockitoTestCase {
    private static final String SCHEMA_FULL = "full";
    private static final String SCHEMA_PARTIAL = "partial";
    private static final String SCHEMA_SUB_COMMAND = "--schema";
    private static final StringWriter stringWriter = new StringWriter();
    private static final PrintWriter out = new PrintWriter(stringWriter);

    @Test
    public void testParseAttributionReportingSchema_defaultSchema() {
        String[] args = {};
        String schema = AttributionReportingUtil.parseAttributionReportingSchema(args, 0, out);
        assertThat(schema).isEqualTo(SCHEMA_PARTIAL);
    }

    @Test
    public void testParseAttributionReportingSchema_partialSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL};
        String schema = AttributionReportingUtil.parseAttributionReportingSchema(args, 0, out);
        assertThat(schema).isEqualTo(SCHEMA_PARTIAL);
    }

    @Test
    public void testParseAttributionReportingSchema_fullSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        String schema = AttributionReportingUtil.parseAttributionReportingSchema(args, 0, out);
        assertThat(schema).isEqualTo(SCHEMA_FULL);
    }

    @Test
    public void testParseAttributionReportingSchema_invalidSchema() {
        String[] args = {SCHEMA_SUB_COMMAND, "invalid"};
        assertThrows(
                IllegalArgumentException.class,
                () -> AttributionReportingUtil.parseAttributionReportingSchema(args, 0, out));
    }

    @Test
    public void testParseAttributionReportingSchema_multipleSchemaArgs() {
        String[] args = {SCHEMA_SUB_COMMAND, SCHEMA_PARTIAL, SCHEMA_SUB_COMMAND, SCHEMA_FULL};
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    AttributionReportingUtil.parseAttributionReportingSchema(args, 0, out);
                });
    }
}
