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

package com.android.adservices.service.measurement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class KeyValueDataTest {

    private static KeyValueData getKeyValueData() {
        return new KeyValueData.Builder()
                .setDataType(KeyValueData.DataType.AGGREGATE_REPORT_RETRY_COUNT)
                .setKey("key")
                .build();
    }

    @Test
    public void setReportingJobNextExecutionTime_nullValue_setsNullValue() throws Exception {
        KeyValueData keyValueData = getKeyValueData();
        keyValueData.setReportingJobNextExecutionTime(null);

        assertNull(keyValueData.getValue());
        assertNull(keyValueData.getReportingJobNextExecutionTime());
    }

    @Test
    public void setReportingJobNextExecutionTime_nonNullValue_setsStringValue() throws Exception {
        Long value = Long.valueOf(13457L);
        KeyValueData keyValueData = getKeyValueData();
        keyValueData.setReportingJobNextExecutionTime(value);

        assertEquals(String.valueOf(value), keyValueData.getValue());
        assertEquals(value, keyValueData.getReportingJobNextExecutionTime());
    }
}
