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

package com.android.adservices.service.devapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.service.proto.DevSessionStorage.State;

import org.junit.Test;

import java.util.Arrays;

public final class DevSessionStateTest extends AdServicesUnitTestCase {

    @Test
    public void testEnumValuesMatchProto() {
        for (DevSessionState devSessionState : DevSessionState.values()) {
            State storageSessionState = State.forNumber(devSessionState.ordinal());

            assertNotNull(storageSessionState);
            assertEquals(storageSessionState.name(), devSessionState.name());
        }
    }

    @Test
    public void testProtoValuesMatchEnum() {
        for (State state : State.values()) {
            if (state == State.UNRECOGNIZED) {
                continue; // Skip the check for corrupted proto states.
            }
            boolean foundMatch =
                    Arrays.stream(DevSessionState.values())
                            .anyMatch(
                                    devSessionState ->
                                            devSessionState.getOrdinal() == state.getNumber()
                                                    && devSessionState.name().equals(state.name()));
            expect.withMessage("No matching DevSessionState enum value found for " + state.name())
                    .that(foundMatch)
                    .isTrue();
        }
    }
}
