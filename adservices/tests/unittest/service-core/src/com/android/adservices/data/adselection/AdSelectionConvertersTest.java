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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.net.Uri;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public class AdSelectionConvertersTest {

    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);

    @Test
    public void testSerializeDeserializeInstant() {
        Instant instant = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);

        Long fromInstant = AdSelectionDatabase.Converters.serializeInstant(instant);

        Instant fromLong = AdSelectionDatabase.Converters.deserializeInstant(fromInstant);

        assertEquals(instant, fromLong);
    }

    @Test
    public void testConvertersNullInputs() {
        assertNull(AdSelectionDatabase.Converters.serializeInstant(null));
        assertNull(AdSelectionDatabase.Converters.deserializeInstant(null));
        assertNull(AdSelectionDatabase.Converters.serializeUrl(null));
        assertNull(AdSelectionDatabase.Converters.deserializeUrl(null));
    }

    @Test
    public void testSerializeDeserializeUri() {
        Uri uri = Uri.parse("http://www.domain.com/adverts/123");

        String fromUri = AdSelectionDatabase.Converters.serializeUrl(uri);

        Uri fromString = AdSelectionDatabase.Converters.deserializeUrl(fromUri);

        assertEquals(uri, fromString);
    }
}
