/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.signals;

import static com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall.Any;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_UPDATE_SCHEMA_VERSION;
import static com.android.adservices.service.signals.SignalsFixture.DEV_CONTEXT;
import static com.android.adservices.service.signals.UpdatesDownloader.CONVERSION_ERROR_MSG;
import static com.android.adservices.service.signals.UpdatesDownloader.DEFAULT_UPDATE_SCHEMA_VERSION;
import static com.android.adservices.service.signals.UpdatesDownloader.INVALID_VERSION_ERROR_MSG;
import static com.android.adservices.service.signals.UpdatesDownloader.PACKAGE_NAME_HEADER;
import static com.android.adservices.service.signals.UpdatesDownloader.UNSUPPORTED_VERSION_ERROR_MSG;
import static com.android.adservices.service.signals.UpdatesDownloader.UPDATE_SCHEMA_VERSION_HEADER;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_CONVERTING_UPDATE_SIGNALS_RESPONSE_TO_JSON_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetIntegerFlag;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RunWith(MockitoJUnitRunner.class)
@SetErrorLogUtilDefaultParams(
        throwable = Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS)
@SetIntegerFlag(name = KEY_PROTECTED_SIGNALS_UPDATE_SCHEMA_VERSION, value = 2)
@RequiresSdkLevelAtLeastT(reason = "PAS is only supported on T+")
public class UpdatesDownloaderTest extends AdServicesExtendedMockitoTestCase {

    private static final Uri URI = Uri.parse("https://example.com");
    private static final String JSON = "{\"a\":\"b\"}";

    @Mock private AdServicesHttpsClient mMockAdServicesHttpsClient;

    private UpdatesDownloader mUpdatesDownloader;

    @Before
    public void setup() {
        mUpdatesDownloader =
                new UpdatesDownloader(
                        AdServicesExecutors.getLightWeightExecutor(),
                        mMockAdServicesHttpsClient,
                        mFakeFlags.getProtectedSignalsUpdateSchemaVersion());
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_CONVERTING_UPDATE_SIGNALS_RESPONSE_TO_JSON_ERROR)
    public void testGetSignalUpdates_invalidJson() {
        String invalidJson = "{abc";

        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                getSignalUpdates(
                                        invalidJson,
                                        String.valueOf(
                                                mFakeFlags
                                                        .getProtectedSignalsUpdateSchemaVersion())));
        expect.withMessage("Exception type")
                .that(e)
                .hasCauseThat()
                .hasCauseThat()
                .isInstanceOf(JSONException.class);
        expect.withMessage("Exception message")
                .that(e)
                .hasCauseThat()
                .hasMessageThat()
                .isEqualTo(CONVERSION_ERROR_MSG);
    }

    @Test
    public void testGetSignalUpdates_payloadSizeTooLarge() {
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(
                        PACKAGE_NAME_HEADER,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        UPDATE_SCHEMA_VERSION_HEADER,
                        String.valueOf(mFakeFlags.getProtectedSignalsUpdateSchemaVersion()));
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DEV_CONTEXT)
                        .build();
        when(mMockAdServicesHttpsClient.fetchPayload(request))
                .thenReturn(Futures.immediateFailedFuture(new IOException()));

        Exception e =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mUpdatesDownloader
                                        .getSignalUpdates(
                                                URI, CommonFixture.TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                                        .get());

        expect.withMessage("Exception type").that(e).hasCauseThat().isInstanceOf(IOException.class);
    }

    @Test
    public void testGetSignalUpdates_validJson_currentVersionInResponse() throws Exception {
        SignalUpdates signalUpdates =
                getSignalUpdates(
                        JSON, String.valueOf(mFakeFlags.getProtectedSignalsUpdateSchemaVersion()));

        expect.withMessage("updateJson")
                .that(signalUpdates.getUpdateJson().toString())
                .isEqualTo(JSON);
        expect.withMessage("updateSchemaVersion")
                .that(signalUpdates.getUpdateSchemaVersion())
                .isEqualTo(mFakeFlags.getProtectedSignalsUpdateSchemaVersion());
    }

    @Test
    public void testGetSignalUpdates_validJson_supportedVersionInResponse() throws Exception {
        int updateSchemaVersion = 1;

        SignalUpdates signalUpdates = getSignalUpdates(JSON, String.valueOf(updateSchemaVersion));

        expect.withMessage("updateJson")
                .that(signalUpdates.getUpdateJson().toString())
                .isEqualTo(JSON);
        expect.withMessage("updateSchemaVersion")
                .that(signalUpdates.getUpdateSchemaVersion())
                .isEqualTo(updateSchemaVersion);
    }

    @Test
    public void testGetSignalUpdates_validJson_noVersionInResponse() throws Exception {
        String noVersion = null;
        SignalUpdates signalUpdates = getSignalUpdates(JSON, noVersion);

        expect.withMessage("updateJson")
                .that(signalUpdates.getUpdateJson().toString())
                .isEqualTo(JSON);
        expect.withMessage("updateSchemaVersion")
                .that(signalUpdates.getUpdateSchemaVersion())
                .isEqualTo(DEFAULT_UPDATE_SCHEMA_VERSION);
    }

    @Test
    public void testGetSignalUpdates_validJson_invalidVersionInResponse() {
        String invalidVersion = "NotAVersion";

        Exception e =
                assertThrows(
                        ExecutionException.class, () -> getSignalUpdates(JSON, invalidVersion));

        expect.withMessage("Exception type")
                .that(e)
                .hasCauseThat()
                .isInstanceOf(IllegalArgumentException.class);
        expect.withMessage("Exception message")
                .that(e)
                .hasCauseThat()
                .hasMessageThat()
                .contains(INVALID_VERSION_ERROR_MSG);
    }

    @Test
    public void testGetSignalUpdates_validJson_unsupportedVersionInResponse() {
        String unsupportedVersion = "123";

        Exception e =
                assertThrows(
                        ExecutionException.class, () -> getSignalUpdates(JSON, unsupportedVersion));

        expect.withMessage("Exception type")
                .that(e)
                .hasCauseThat()
                .isInstanceOf(IllegalArgumentException.class);
        expect.withMessage("Exception message")
                .that(e)
                .hasCauseThat()
                .hasMessageThat()
                .contains(UNSUPPORTED_VERSION_ERROR_MSG);
    }

    private SignalUpdates getSignalUpdates(String updateJson, String versionToReturn)
            throws Exception {
        AdServicesHttpClientResponse.Builder response =
                AdServicesHttpClientResponse.builder().setResponseBody(updateJson);

        if (versionToReturn != null) {
            response =
                    response.setResponseHeaders(
                            ImmutableMap.of(
                                    UPDATE_SCHEMA_VERSION_HEADER,
                                    ImmutableList.of(versionToReturn)));
        }
        SettableFuture<AdServicesHttpClientResponse> returnValue = SettableFuture.create();
        returnValue.set(response.build());

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(
                        PACKAGE_NAME_HEADER,
                        CommonFixture.TEST_PACKAGE_NAME_1,
                        UPDATE_SCHEMA_VERSION_HEADER,
                        String.valueOf(mFakeFlags.getProtectedSignalsUpdateSchemaVersion()));
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DEV_CONTEXT)
                        .build();
        when(mMockAdServicesHttpsClient.fetchPayload(request)).thenReturn(returnValue);

        return mUpdatesDownloader
                .getSignalUpdates(URI, CommonFixture.TEST_PACKAGE_NAME_1, DEV_CONTEXT)
                .get();
    }
}
