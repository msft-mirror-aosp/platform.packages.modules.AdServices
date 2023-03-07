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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.net.Uri;
import android.os.Parcel;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/** Unit tests for {@link AdData} */
@SmallTest
public final class AdDataTest {
    private static final Uri VALID_RENDER_URI =
            new Uri.Builder().path("valid.example.com/testing/hello").build();

    @Test
    public void testBuildValidAdDataSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        assertThat(validAdData.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(validAdData.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
    }

    @Test
    public void testParcelValidAdDataSuccess() {
        AdData validAdData =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        Parcel p = Parcel.obtain();
        validAdData.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdData fromParcel = AdData.CREATOR.createFromParcel(p);

        assertThat(fromParcel.getRenderUri()).isEqualTo(VALID_RENDER_URI);
        assertThat(fromParcel.getMetadata()).isEqualTo(AdDataFixture.VALID_METADATA);
    }

    @Test
    public void testBuildNullUriAdDataFails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdData.Builder()
                                .setRenderUri(null)
                                .setMetadata(AdDataFixture.VALID_METADATA)
                                .build());
    }

    @Test
    public void testBuildNullMetadataAdDataFails() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new AdData.Builder()
                                .setRenderUri(VALID_RENDER_URI)
                                .setMetadata(null)
                                .build());
    }

    @Test
    public void testAdDataToString() {
        AdData obj =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        assertEquals(
                String.format(
                        "AdData{mRenderUri=%s, mMetadata='%s'}",
                        VALID_RENDER_URI, AdDataFixture.VALID_METADATA),
                obj.toString());
    }

    @Test
    public void testAdDataDescribeContent() {
        AdData obj =
                new AdData.Builder()
                        .setRenderUri(VALID_RENDER_URI)
                        .setMetadata(AdDataFixture.VALID_METADATA)
                        .build();

        assertEquals(0, obj.describeContents());
    }
}
