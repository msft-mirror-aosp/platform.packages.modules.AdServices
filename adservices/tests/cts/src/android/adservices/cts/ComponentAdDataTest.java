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

import android.adservices.common.AdDataFixture;
import android.adservices.common.ComponentAdData;
import android.net.Uri;

import org.junit.Test;

public class ComponentAdDataTest extends CtsAdServicesDeviceTestCase {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();

    @Test
    public void testBuildComponentAdData() {
        ComponentAdData validComponentAdData =
                new ComponentAdData(VALID_RENDER_URI, AdDataFixture.VALID_RENDER_ID);

        expect.withMessage("validComponentAdData.getRenderUri()")
                .that(validComponentAdData.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("validComponentAdData.getAdRenderId()")
                .that(validComponentAdData.getAdRenderId())
                .isEqualTo(AdDataFixture.VALID_RENDER_ID);
    }
}
