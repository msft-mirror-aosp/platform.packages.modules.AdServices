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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.CallingAppUidSupplier;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implementation of Fetch Custom Audience. */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class FetchCustomAudienceImpl {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int API_NAME = AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
    private static final String CUSTOM_AUDIENCE_HEADER = "X-CUSTOM-AUDIENCE-DATA";
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final ListeningExecutorService mExecutorService;
    @NonNull private final CallingAppUidSupplier mCallingAppUidSupplier;
    @NonNull private final ConsentManager mConsentManager;
    @NonNull private final CustomAudienceServiceFilter mCustomAudienceServiceFilter;
    @NonNull private final AdServicesHttpsClient mHttpClient;
    @NonNull private final Clock mClock;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;

    @NonNull private final boolean mFledgeFetchCustomAudienceEnabled;
    @NonNull private final boolean mEnforceForegroundStatusForFledgeCustomAudience;
    @NonNull private final int mMaxUserBiddingSignalsSizeB;
    @NonNull private final int mMaxTrustedBiddingDataSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxAdsSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxNumAds;
    @NonNull private final boolean mFledgeAdSelectionFilteringEnabled;

    @NonNull private AdTechIdentifier mBuyer;

    @VisibleForTesting
    public FetchCustomAudienceImpl(
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull ExecutorService executor,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull CallingAppUidSupplier callingAppUidSupplier,
            @NonNull ConsentManager consentManager,
            @NonNull CustomAudienceServiceFilter customAudienceServiceFilter,
            @NonNull AdServicesHttpsClient httpClient) {
        Objects.requireNonNull(flags);
        Objects.requireNonNull(clock);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(callingAppUidSupplier);
        Objects.requireNonNull(consentManager);
        Objects.requireNonNull(customAudienceServiceFilter);
        Objects.requireNonNull(httpClient);

        mClock = clock;
        mAdServicesLogger = adServicesLogger;
        mExecutorService = MoreExecutors.listeningDecorator(executor);
        mCustomAudienceDao = customAudienceDao;
        mCallingAppUidSupplier = callingAppUidSupplier;
        mConsentManager = consentManager;
        mCustomAudienceServiceFilter = customAudienceServiceFilter;
        mHttpClient = httpClient;

        // TODO(b/278016820): Revisit handling field limit validation.
        // Ensuring process-stable flag values by assigning to local variables at instantiation.
        mFledgeFetchCustomAudienceEnabled = flags.getFledgeFetchCustomAudienceEnabled();
        mEnforceForegroundStatusForFledgeCustomAudience =
                flags.getEnforceForegroundStatusForFledgeCustomAudience();
        mMaxUserBiddingSignalsSizeB = flags.getFledgeCustomAudienceMaxUserBiddingSignalsSizeB();
        mMaxTrustedBiddingDataSizeB = flags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB();
        mFledgeCustomAudienceMaxAdsSizeB = flags.getFledgeCustomAudienceMaxAdsSizeB();
        mFledgeCustomAudienceMaxNumAds = flags.getFledgeCustomAudienceMaxNumAds();
        mFledgeAdSelectionFilteringEnabled = flags.getFledgeAdSelectionFilteringEnabled();
    }

    /** Adds a user to a fetched custom audience. */
    public void doFetchCustomAudience(
            @NonNull FetchAndJoinCustomAudienceInput input,
            @NonNull FetchAndJoinCustomAudienceCallback callback) {
        try {
            // Failing fast and silently if fetchCustomAudience is disabled.
            if (!mFledgeFetchCustomAudienceEnabled) {
                sLogger.v("fetchCustomAudience is disabled.");
                throw new IllegalStateException("fetchCustomAudience is disabled.");
            } else {
                sLogger.v("fetchCustomAudience is enabled.");
                // TODO(b/282017342): Evaluate correctness of futures chain.
                FluentFuture.from(mExecutorService.submit(() -> filterAndValidateRequest(input)))
                        .transformAsync(ignoreVoid -> performFetch(input), mExecutorService)
                        .transformAsync(
                                httpResponse -> validateResponse(input, httpResponse),
                                mExecutorService)
                        .addCallback(
                                new FutureCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void unusedResult) {
                                        sLogger.v("Completed fetchCustomAudience execution");
                                        notifySuccess(callback);
                                    }

                                    @Override
                                    public void onFailure(Throwable t) {
                                        sLogger.d(
                                                t,
                                                "Error encountered in fetchCustomAudience"
                                                        + " execution");
                                        if (t instanceof FilterException
                                                && t.getCause()
                                                        instanceof
                                                        ConsentManager.RevokedConsentException) {
                                            // Skip logging if a FilterException occurs.
                                            // AdSelectionServiceFilter ensures the failing
                                            // assertion is logged
                                            // internally.

                                            // Fail Silently by notifying success to caller
                                            notifySuccess(callback);
                                        } else {
                                            notifyFailure(callback, t);
                                        }
                                    }
                                },
                                mExecutorService);
            }
        } catch (Throwable t) {
            notifyFailure(callback, t);
        }
    }

    private Void filterAndValidateRequest(@NonNull FetchAndJoinCustomAudienceInput input) {
        sLogger.v("In fetchCustomAudience filterAndValidateRequest");

        // Extract buyer Ad Tech identifier
        // TODO(b/282017511): Use enrollment data to store host eTLD+1.
        String host = input.getFetchUri().getHost();
        AdTechIdentifierValidator adTechIdentifierValidator =
                new AdTechIdentifierValidator("FetchCustomAudienceImpl", "buyer");
        adTechIdentifierValidator.validate(host);
        mBuyer = AdTechIdentifier.fromString(host);

        // Filter request
        try {
            mCustomAudienceServiceFilter.filterRequest(
                    mBuyer,
                    input.getCallerPackageName(),
                    mEnforceForegroundStatusForFledgeCustomAudience,
                    true,
                    mCallingAppUidSupplier.getCallingAppUid(),
                    API_NAME,
                    FLEDGE_API_FETCH_CUSTOM_AUDIENCE);
        } catch (Throwable t) {
            throw new FilterException(t);
        }

        // Validate request
        // TODO(b/282017511): Add implementation.

        sLogger.v("Completed fetchCustomAudience filterAndValidateRequest");

        return null;
    }

    private ListenableFuture<AdServicesHttpClientResponse> performFetch(
            @NonNull FetchAndJoinCustomAudienceInput input) throws JSONException {
        sLogger.v("In fetchCustomAudience performFetch");

        // Optional fields as a json string.
        JSONObject json = new JSONObject();
        if (input.getName() != null) {
            json.put(FetchCustomAudienceReader.NAME_KEY, input.getName());
        }
        if (input.getActivationTime() != null) {
            json.put(FetchCustomAudienceReader.ACTIVATION_TIME_KEY, input.getActivationTime());
        }
        if (input.getExpirationTime() != null) {
            json.put(FetchCustomAudienceReader.EXPIRATION_TIME_KEY, input.getExpirationTime());
        }
        if (input.getUserBiddingSignals() != null) {
            json.put(
                    CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY,
                    input.getUserBiddingSignals());
        }
        String jsonString = json.toString();

        // Custom headers under X-CUSTOM-AUDIENCE-DATA
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(CUSTOM_AUDIENCE_HEADER, jsonString);

        // TODO(b/282017511): Validate size of headers.

        sLogger.v("Sending request from fetchCustomAudience performFetch");

        // GET request
        return mHttpClient.fetchPayload(
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(input.getFetchUri())
                        .build());
    }

    private ListenableFuture<Void> validateResponse(
            @NonNull FetchAndJoinCustomAudienceInput input,
            @NonNull AdServicesHttpClientResponse fetchResponse) {
        return FluentFuture.from(
                mExecutorService.submit(
                        () -> {

                            // Parse + Validate response
                            String responseJsonString = fetchResponse.getResponseBody();
                            JSONObject responseJson = new JSONObject(responseJsonString);

                            FetchCustomAudienceReader reader =
                                    new FetchCustomAudienceReader(
                                            responseJson,
                                            String.valueOf(fetchResponse.hashCode()),
                                            mBuyer,
                                            mMaxUserBiddingSignalsSizeB,
                                            mMaxTrustedBiddingDataSizeB,
                                            mFledgeCustomAudienceMaxAdsSizeB,
                                            mFledgeCustomAudienceMaxNumAds,
                                            mFledgeAdSelectionFilteringEnabled);

                            Uri dailyUpdateUri = reader.getDailyUpdateUriFromJsonObject();

                            // TODO(b/282018172): Validate partial input CA + partial response CA =
                            // full CA
                            // TODO(b/286146443): Document how fields from the server are used.

                            // Add response from server
                            DBCustomAudience.Builder customAudienceBuilder =
                                    new DBCustomAudience.Builder()
                                            .setName(reader.getNameFromJsonObject())
                                            .setBuyer(mBuyer)
                                            .setActivationTime(
                                                    reader.getActivationTimeFromJsonObject())
                                            .setExpirationTime(
                                                    reader.getExpirationTimeFromJsonObject())
                                            .setBiddingLogicUri(
                                                    reader.getBiddingLogicUriFromJsonObject())
                                            .setTrustedBiddingData(
                                                    reader.getTrustedBiddingDataFromJsonObject())
                                            .setAds(reader.getAdsFromJsonObject())
                                            .setUserBiddingSignals(
                                                    reader.getUserBiddingSignalsFromJsonObject());

                            // Override response from server with input fields
                            if (input.getName() != null) {
                                customAudienceBuilder =
                                        customAudienceBuilder.setName(input.getName());
                            }

                            if (input.getActivationTime() != null) {
                                customAudienceBuilder =
                                        customAudienceBuilder.setActivationTime(
                                                input.getActivationTime());
                            }

                            if (input.getExpirationTime() != null) {
                                customAudienceBuilder =
                                        customAudienceBuilder.setExpirationTime(
                                                input.getExpirationTime());
                            }

                            if (input.getUserBiddingSignals() != null) {
                                customAudienceBuilder =
                                        customAudienceBuilder.setUserBiddingSignals(
                                                input.getUserBiddingSignals());
                            }

                            customAudienceBuilder.setOwner(input.getCallerPackageName());
                            Instant currentTime = mClock.instant();
                            customAudienceBuilder.setCreationTime(currentTime);
                            customAudienceBuilder.setLastAdsAndBiddingDataUpdatedTime(currentTime);

                            DBCustomAudience customAudience = customAudienceBuilder.build();

                            // Persist response
                            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                                    customAudience, dailyUpdateUri);

                            return null;
                        }));
    }

    // TODO(b/283857101): Move DB handling to persistResponse using a common CustomAudienceReader.
    // private Void persistResponse (@NonNull DBCustomAudience customAudience) {}

    private void notifyFailure(FetchAndJoinCustomAudienceCallback callback, Throwable t) {
        try {
            int resultCode;

            boolean isFilterException = t instanceof FilterException;

            if (isFilterException) {
                resultCode = FilterException.getResultCode(t);
            } else if (t instanceof IllegalArgumentException) {
                resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
            } else {
                sLogger.d(t, "Unexpected error during operation");
                resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
            }

            // Skip logging if a FilterException occurs.
            // AdSelectionServiceFilter ensures the failing assertion is logged internally.
            // Note: Failure is logged before the callback to ensure deterministic testing.
            if (!isFilterException) {
                mAdServicesLogger.logFledgeApiCallStats(API_NAME, resultCode, 0);
            }

            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(resultCode)
                            .setErrorMessage(t.getMessage())
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new RuntimeException(e);
        }
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void notifySuccess(@NonNull FetchAndJoinCustomAudienceCallback callback) {
        try {
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME, AdServicesStatusUtils.STATUS_SUCCESS, 0);
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            mAdServicesLogger.logFledgeApiCallStats(
                    API_NAME, AdServicesStatusUtils.STATUS_INTERNAL_ERROR, 0);
            throw new RuntimeException(e);
        }
    }
}
