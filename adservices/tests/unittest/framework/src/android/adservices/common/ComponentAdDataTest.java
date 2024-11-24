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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.net.Uri;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

public final class ComponentAdDataTest extends AdServicesUnitTestCase {
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

    @Test
    public void testComponentAdDataToString() {
        ComponentAdData validComponentAdData =
                new ComponentAdData(VALID_RENDER_URI, AdDataFixture.VALID_RENDER_ID);
        String actualString = validComponentAdData.toString();

        expect.withMessage("Starts with ComponentAdData")
                .that(actualString)
                .startsWith("ComponentAdData{");
        expect.withMessage("Contains mRenderUri")
                .that(actualString)
                .contains("mRenderUri=" + VALID_RENDER_URI);
        expect.withMessage("Contains mAdRenderId")
                .that(actualString)
                .contains("mAdRenderId='" + AdDataFixture.VALID_RENDER_ID + '\'');
        expect.withMessage("Ends with }").that(actualString).endsWith("}");
    }

    @Test
    public void testComponentAdDataDescribeContent() {
        ComponentAdData validComponentAdData =
                new ComponentAdData(VALID_RENDER_URI, AdDataFixture.VALID_RENDER_ID);

        assertThat(validComponentAdData.describeContents()).isEqualTo(0);
    }
}
