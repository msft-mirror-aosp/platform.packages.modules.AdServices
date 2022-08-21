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

package com.android.adservices.data.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.data.common.DBAdData;

import org.json.JSONException;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

public class CustomAudienceConvertersTest {

    @Test
    public void testDeserialize_invalidString() {
        RuntimeException runtimeException = assertThrows(RuntimeException.class,
                () -> DBCustomAudience.Converters.fromJson("   "));
        assertTrue(runtimeException.getCause() instanceof JSONException);
    }

    @Test
    public void testSerializeAndDeserialize_runNormally() {
        List<DBAdData> input =
                AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1).stream()
                        .map(DBAdData::fromServiceObject)
                        .collect(Collectors.toList());
        String serializedString = DBCustomAudience.Converters.toJson(input);
        List<DBAdData> output = DBCustomAudience.Converters.fromJson(serializedString);
        assertEquals(input, output);
    }

    @Test
    public void testSerialize_nullInput() {
        assertNull(DBCustomAudience.Converters.toJson(null));
    }

    @Test
    public void testDeserialize_nullInput() {
        assertNull(DBCustomAudience.Converters.fromJson(null));
    }
}
