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

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;

import org.junit.Test;

@RequiresSdkLevelAtLeastS
public final class PersistAdSelectionResultRequestTest extends CtsAdServicesDeviceTestCase {
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final long AD_SELECTION_ID = 123456789L;
    private static final byte[] AD_SELECTION_RESULT = new byte[10];

    @Test
    public void testPersistAdSelectionResultRequest_validInput_successWithDeprecatedField() {
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(SELLER)
                        // using deprecated method
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionDataId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionResult()).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void testPersistAdSelectionResultRequest_validInput_successWithUpdatedField() {
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionDataId(AD_SELECTION_ID)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionDataId()).isEqualTo(AD_SELECTION_ID);
        expect.that(request.getAdSelectionResult()).isEqualTo(AD_SELECTION_RESULT);
    }

    @Test
    public void
            testPersistAdSelectionResultRequest_validInput_valueOfIdGettersIsSameAfterCallingBothSetters() {
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionDataId(AD_SELECTION_ID + 1)
                        .setAdSelectionResult(AD_SELECTION_RESULT)
                        .build();

        expect.that(request.getSeller()).isEqualTo(SELLER);
        expect.that(request.getAdSelectionId()).isEqualTo(AD_SELECTION_ID + 1);
        expect.that(request.getAdSelectionDataId()).isEqualTo(AD_SELECTION_ID + 1);
        expect.that(request.getAdSelectionResult()).isEqualTo(AD_SELECTION_RESULT);
    }
}
