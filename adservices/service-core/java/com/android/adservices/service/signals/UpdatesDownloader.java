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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_CONVERTING_UPDATE_SIGNALS_RESPONSE_TO_JSON_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.signals.SignalUpdates.UpdateSchemaVersion;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.Executor;

/** Downloads signal updates for the updateSignals API. */
public class UpdatesDownloader {

    public static final String PACKAGE_NAME_HEADER = "X-PROTECTED-SIGNALS-PACKAGE";
    public static final String UPDATE_SCHEMA_VERSION_HEADER =
            "X-PROTECTED-SIGNALS-UPDATE-SCHEMA-VERSION";
    public static final @UpdateSchemaVersion int DEFAULT_UPDATE_SCHEMA_VERSION =
            UpdateSchemaVersion.V0;
    public static final @UpdateSchemaVersion int MINIMUM_UPDATE_SCHEMA_VERSION =
            UpdateSchemaVersion.V0;
    public static final String CONVERSION_ERROR_MSG = "Error converting response body to JSON";
    public static final String INVALID_VERSION_ERROR_MSG = "Invalid update schema version";
    public static final String UNSUPPORTED_VERSION_ERROR_MSG = "Unsupported update schema version";
    private static final String SUPPORTED_VERSIONS_ERROR_MSG = "Supported versions";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final Executor mLightweightExecutor;
    @NonNull private final AdServicesHttpsClient mHttpClient;

    private final int mUpdateSchemaVersion;

    public UpdatesDownloader(
            @NonNull Executor lightweightExecutor,
            @NonNull AdServicesHttpsClient httpClient,
            int updateSchemaVersion) {
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(httpClient);
        mLightweightExecutor = lightweightExecutor;
        mHttpClient = httpClient;
        mUpdateSchemaVersion = updateSchemaVersion;
    }

    /**
     * Gets the signal updates from the remote server.
     *
     * @param validatedUri Validated URI from which to fetch JSON.
     * @param packageName The package name of the calling app.
     * @param devContext Development context for testing the network call.
     * @return A future containing the fetched {@link SignalUpdates}.
     */
    public FluentFuture<SignalUpdates> getSignalUpdates(
            Uri validatedUri, String packageName, DevContext devContext) {
        sLogger.v("Fetching signals from " + validatedUri);

        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(
                        PACKAGE_NAME_HEADER,
                        packageName,
                        UPDATE_SCHEMA_VERSION_HEADER,
                        String.valueOf(mUpdateSchemaVersion));
        ImmutableSet<String> responseHeaderKeys = ImmutableSet.of(UPDATE_SCHEMA_VERSION_HEADER);
        AdServicesHttpClientRequest clientRequest =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setResponseHeaderKeys(responseHeaderKeys)
                        .setUri(validatedUri)
                        .setDevContext(devContext)
                        .build();

        FluentFuture<AdServicesHttpClientResponse> clientResponse =
                FluentFuture.from(mHttpClient.fetchPayload(clientRequest));
        return clientResponse.transform(
                response ->
                        SignalUpdates.builder()
                                .setUpdateJson(getUpdateJsonFromResponse(response))
                                .setUpdateSchemaVersion(
                                        getUpdateSchemaVersionFromResponse(response))
                                .build(),
                mLightweightExecutor);
    }

    private JSONObject getUpdateJsonFromResponse(AdServicesHttpClientResponse response) {
        try {
            return new JSONObject(response.getResponseBody());
        } catch (JSONException e) {
            sLogger.e(e, "Error converting updateSignals response body to JSON");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PAS_CONVERTING_UPDATE_SIGNALS_RESPONSE_TO_JSON_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PAS);
            throw new IllegalArgumentException(CONVERSION_ERROR_MSG, e);
        }
    }

    private int getUpdateSchemaVersionFromResponse(AdServicesHttpClientResponse response) {
        @UpdateSchemaVersion int version = DEFAULT_UPDATE_SCHEMA_VERSION;

        if (response.getResponseHeaders() == null) {
            return version;
        }

        List<String> responseHeader =
                response.getResponseHeaders().get(UPDATE_SCHEMA_VERSION_HEADER);
        if (responseHeader == null || responseHeader.isEmpty()) {
            return version;
        }

        String versionString = responseHeader.get(0);
        try {
            version = Integer.parseInt(versionString);
        } catch (NumberFormatException e) {
            String errorMessage =
                    String.format(
                            Locale.ENGLISH, "%s: %s", INVALID_VERSION_ERROR_MSG, versionString);
            sLogger.e(errorMessage);
            throw new IllegalArgumentException(errorMessage, e);
        }

        if (version < MINIMUM_UPDATE_SCHEMA_VERSION || version > mUpdateSchemaVersion) {
            String errorMessage =
                    String.format(
                            Locale.ENGLISH,
                            "%s: %s. %s: [%d..%d]",
                            UNSUPPORTED_VERSION_ERROR_MSG,
                            versionString,
                            SUPPORTED_VERSIONS_ERROR_MSG,
                            MINIMUM_UPDATE_SCHEMA_VERSION,
                            mUpdateSchemaVersion);
            sLogger.e(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }

        return version;
    }
}
