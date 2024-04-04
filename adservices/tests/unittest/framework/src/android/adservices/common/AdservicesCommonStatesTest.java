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

package android.adservices.common;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public class AdservicesCommonStatesTest extends AdServicesUnitTestCase {
    @Test
    public void testBuildAndGetAdservicesCommonStates_givenRevoked() {
        AdServicesCommonStates states =
                new AdServicesCommonStates.Builder()
                        .setMeasurementState(ConsentStatus.GIVEN)
                        .setPaState(ConsentStatus.REVOKED)
                        .build();

        expect.that(states.getMeasurementState()).isEqualTo(ConsentStatus.GIVEN);
        expect.that(states.getPaState()).isEqualTo(ConsentStatus.REVOKED);
    }

    @Test
    public void testBuildAndGetAdservicesCommonStates_notEnabledReset() {
        AdServicesCommonStates states =
                new AdServicesCommonStates.Builder()
                        .setMeasurementState(ConsentStatus.SERVICE_NOT_ENABLED)
                        .setPaState(ConsentStatus.WAS_RESET)
                        .build();

        expect.that(states.getMeasurementState()).isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        expect.that(states.getPaState()).isEqualTo(ConsentStatus.WAS_RESET);
    }
}
