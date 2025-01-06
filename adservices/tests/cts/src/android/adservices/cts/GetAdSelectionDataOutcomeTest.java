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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.adselection.GetAdSelectionDataOutcome;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.adservices.flags.Flags;

import org.junit.Test;

public final class GetAdSelectionDataOutcomeTest extends CtsAdServicesDeviceTestCase {
    private static final long AD_SELECTION_ID = 123456789L;
    private static final byte[] AD_SELECTION_RESULT = new byte[] {1, 2, 3, 4};

    @RequiresFlagsEnabled(Flags.FLAG_FLEDGE_AUCTION_SERVER_GET_AD_SELECTION_DATA_ID_ENABLED)
    @Test
    public void testGetAdSelectionDataRequest_validInput_success() {
        GetAdSelectionDataOutcome request =
                new GetAdSelectionDataOutcome.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionData(AD_SELECTION_RESULT)
                        .build();

        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionDataId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionData()).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void testMutabilityForAdSelectionData() {
        byte originalValue = 1;
        byte[] adSelectionData = new byte[] {originalValue};
        GetAdSelectionDataOutcome getAdSelectionDataOutcome =
                new GetAdSelectionDataOutcome.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionData(adSelectionData)
                        .build();
        expect.that(getAdSelectionDataOutcome.getAdSelectionData()).isEqualTo(adSelectionData);

        byte newValue = 5;
        adSelectionData[0] = newValue;
        assertThat(getAdSelectionDataOutcome.getAdSelectionData()).isNotNull();
        assertThat(getAdSelectionDataOutcome.getAdSelectionData()).hasLength(1);
        expect.that(getAdSelectionDataOutcome.getAdSelectionData()[0]).isEqualTo(originalValue);
    }
}
