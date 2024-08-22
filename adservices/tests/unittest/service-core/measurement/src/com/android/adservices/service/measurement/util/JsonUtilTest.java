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

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONException;
import org.junit.Test;

import java.util.Optional;

public class JsonUtilTest {
    @Test
    public void testMaybeGetJsonArray_success() throws JSONException {
        String validJsonArrayString =
                "[{\"values\":{\"campaignCounts\":32768, \"geoValue\":1664}},{\"values\":{\"a\":1,"
                        + " \"b\":2, \"c\":3}}]";
        assertThat(JsonUtil.maybeGetJsonArray(validJsonArrayString)).isNotNull();
    }

    @Test
    public void testMaybeGetJsonArray_fails() throws JSONException {
        String invalidJsonArrayString = "{\"campaignCounts\":32768, \"geoValue\":1664}";
        assertThat(JsonUtil.maybeGetJsonArray(invalidJsonArrayString))
                .isEqualTo(Optional.empty());
    }
}
