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
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdWithBid;

import androidx.test.filters.SmallTest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

@SmallTest
public class AdWithBidArgumentTest {

    public static final int BID_VALUE = 10;
    public static final AdWithBid AD_WITH_BID =
            new AdWithBid(AdDataArgumentTest.AD_DATA, BID_VALUE);

    private JSONObject aValidAdWithBidJson() throws JSONException {
        return new JSONObject()
                .put(AdWithBidArgument.AD_FIELD_NAME, AdDataArgumentTest.aValidAdDataJson())
                .put(AdWithBidArgument.BID_FIELD_NAME, BID_VALUE);
    }

    @Test
    public void testShouldReadValidJSON() throws Exception {
        assertThat(AdWithBidArgument.parseJsonResponse(aValidAdWithBidJson()))
                .isEqualTo(AD_WITH_BID);
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadata() throws Exception {
        final JSONObject adWithInvalidMetadata =
                AdDataArgumentTest.aValidAdDataJson().put(AdDataArgument.METADATA_FIELD_NAME, 10);
        JSONObject adWithBidWithInvalidMetadata =
                new JSONObject()
                        .put(AdWithBidArgument.AD_FIELD_NAME, adWithInvalidMetadata)
                        .put(AdWithBidArgument.BID_FIELD_NAME, BID_VALUE);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdWithBidArgument.parseJsonResponse(adWithBidWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfIsMissingBid() throws Exception {
        JSONObject adWithBidMissingBid = aValidAdWithBidJson();
        adWithBidMissingBid.remove(AdWithBidArgument.BID_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdWithBidArgument.parseJsonResponse(adWithBidMissingBid));
    }

    @Test
    public void testShouldFailIfIsMissingAdData() throws Exception {
        JSONObject adWithBidMissingAdData = aValidAdWithBidJson();
        adWithBidMissingAdData.remove(AdWithBidArgument.AD_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AdWithBidArgument.parseJsonResponse(adWithBidMissingAdData));
    }

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(AdWithBidArgument.asScriptArgument("name", AD_WITH_BID))
                .isEqualTo(
                        recordArg(
                                "name",
                                recordArg(
                                        AdWithBidArgument.AD_FIELD_NAME,
                                        stringArg(
                                                AdDataArgument.RENDER_URL_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getRenderUri().toString()),
                                        jsonArg(
                                                AdDataArgument.METADATA_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getMetadata())),
                                numericArg(
                                        AdWithBidArgument.BID_FIELD_NAME, AD_WITH_BID.getBid())));
    }
}
