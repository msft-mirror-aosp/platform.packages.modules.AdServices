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

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdDataFixture;
import android.adservices.common.ComponentAdData;
import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.android.adservices.flags.Flags;

import org.junit.Test;

@RequiresFlagsEnabled(Flags.FLAG_FLEDGE_ENABLE_CUSTOM_AUDIENCE_COMPONENT_ADS)
public class ComponentAdDataTest extends CtsAdServicesDeviceTestCase {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();

    @Test
    public void testBuildValidAdDataSuccess() {
        ComponentAdData validComponentAdData =
                new ComponentAdData(VALID_RENDER_URI, AdDataFixture.VALID_RENDER_ID);

        expect.withMessage("validComponentAdData.getRenderUri()")
                .that(validComponentAdData.getRenderUri())
                .isEqualTo(VALID_RENDER_URI);
        expect.withMessage("validComponentAdData.getAdRenderId()")
                .that(validComponentAdData.getAdRenderId())
                .isEqualTo(AdDataFixture.VALID_RENDER_ID);
    }

    @Test
    public void testCreateComponentAdDataWithNullRenderIdThrows() {
        assertThrows(NullPointerException.class, () -> new ComponentAdData(VALID_RENDER_URI, null));
    }

    @Test
    public void testCreateComponentAdDataWithNullRenderUriThrows() {
        assertThrows(
                NullPointerException.class,
                () -> new ComponentAdData(null, AdDataFixture.VALID_RENDER_ID));
    }

    @Test
    public void testBuildComponentAdDataWitEmptyRenderIdThrows() {
        assertThrows(
                IllegalArgumentException.class, () -> new ComponentAdData(VALID_RENDER_URI, ""));
    }

    @Test
    public void testParcelComponentAdDataSucceeds() {
        ComponentAdData validComponentAdData =
                new ComponentAdData(VALID_RENDER_URI, AdDataFixture.VALID_RENDER_ID);

        Parcel p = Parcel.obtain();
        try {
            validComponentAdData.writeToParcel(p, 0);
            p.setDataPosition(0);
            ComponentAdData fromParcel = ComponentAdData.CREATOR.createFromParcel(p);

            expect.that(fromParcel).isEqualTo(validComponentAdData);
        } finally {
            p.recycle();
        }
    }
}
