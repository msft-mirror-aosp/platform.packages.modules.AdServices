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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

@SmallTest
public class AdDataArgumentTest {
    public static final AdData AD_DATA =
            AdDataFixture.getValidAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);

    public static JSONObject aValidAdDataJson() throws JSONException {
        return new JSONObject()
                .put(AdDataArgument.RENDER_URI_FIELD_NAME, AD_DATA.getRenderUri())
                .put(AdDataArgument.METADATA_FIELD_NAME, new JSONObject(AD_DATA.getMetadata()));
    }

    @Test
    public void testShouldReadValidJSON() throws Exception {
        assertThat(AdDataArgument.parseJsonResponse(aValidAdDataJson())).isEqualTo(AD_DATA);
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadata() throws JSONException {
        JSONObject adDataWithInvalidMetadata =
                aValidAdDataJson().put(AdDataArgument.METADATA_FIELD_NAME, 10);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdWithBidArgument.parseJsonResponse(adDataWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingMetadata() throws JSONException {
        JSONObject adDataWithoutMetadata = aValidAdDataJson();
        adDataWithoutMetadata.remove(AdDataArgument.METADATA_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdDataArgument.parseJsonResponse(adDataWithoutMetadata));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingRenderUri() throws JSONException {
        JSONObject adDataWithoutRenderUri = aValidAdDataJson();
        adDataWithoutRenderUri.remove(AdDataArgument.RENDER_URI_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdDataArgument.parseJsonResponse(adDataWithoutRenderUri));
    }

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(AdDataArgument.asScriptArgument("name", AD_DATA))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        AdDataArgument.RENDER_URI_FIELD_NAME,
                                        AD_DATA.getRenderUri().toString()),
                                jsonArg(
                                        AdDataArgument.METADATA_FIELD_NAME,
                                        AD_DATA.getMetadata())));
    }
}
