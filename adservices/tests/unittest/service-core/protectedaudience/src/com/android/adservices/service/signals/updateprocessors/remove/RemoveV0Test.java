/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals.updateprocessors.remove;

import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.ID_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.NOW;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.createSignal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;

import org.json.JSONArray;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class RemoveV0Test extends AdServicesUnitTestCase {
    private final RemoveV0 mRemoveV0 = new RemoveV0();

    @Test
    public void testRemoveSingleNotPresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        UpdateOutput output = mRemoveV0.processUpdates(updatesJson, Collections.emptyMap());

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertTrue(output.getToRemove().isEmpty());
        assertTrue(output.getToAdd().isEmpty());
    }

    @Test
    public void testRemoveSinglePresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toRemove = createSignal(KEY_1, VALUE_1, ID_1, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toRemove)));

        UpdateOutput output = mRemoveV0.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toRemove), output.getToRemove());
        assertTrue(output.getToAdd().isEmpty());
    }

    @Test
    public void testRemoveMultiplePresent() throws Exception {
        JSONArray updatesJson = new JSONArray();
        updatesJson.put(BASE64_KEY_1);

        Map<ByteBuffer, Set<DBProtectedSignal>> existingSignals = new HashMap<>();
        DBProtectedSignal toRemove = createSignal(KEY_1, VALUE_1, ID_1, NOW);
        existingSignals.put(BB_KEY_1, new HashSet<>(Arrays.asList(toRemove)));

        UpdateOutput output = mRemoveV0.processUpdates(updatesJson, existingSignals);

        assertEquals(Collections.singleton(BB_KEY_1), output.getKeysTouched());
        assertEquals(Arrays.asList(toRemove), output.getToRemove());
        assertTrue(output.getToAdd().isEmpty());
    }
}
