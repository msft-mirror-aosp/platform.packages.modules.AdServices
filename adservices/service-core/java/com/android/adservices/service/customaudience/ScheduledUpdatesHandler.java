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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.AUCTION_SERVER_REQUEST_FLAGS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.PRIORITY_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.adservices.common.AdData;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdateRequest;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AdTechIdentifierValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.JsonValidator;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.shared.common.ApplicationContextSingleton;

import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InvalidObjectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ScheduledUpdatesHandler {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    public static final String JOIN_CUSTOM_AUDIENCE_KEY = "join";
    public static final String LEAVE_CUSTOM_AUDIENCE_KEY = "leave";
    private static final String ACTION_SCHEDULE_CA_COMPLETE_INTENT =
            "ACTION_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_FINISHED";
    public static final Duration STALE_DELAYED_UPDATE_AGE = Duration.of(24, ChronoUnit.HOURS);
    public static final String FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE =
            "Fused custom audience is incomplete.";
    public static final String FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE =
            "Fused custom audience exceeds size limit.";
    public static final ImmutableMap<String, String> JSON_REQUEST_PROPERTIES =
            ImmutableMap.of(
                    "Content-Type", "application/json",
                    "Accept", "application/json");
    // Placeholder value to be used with the CustomAudienceQuantityChecker
    private static final CustomAudience PLACEHOLDER_CUSTOM_AUDIENCE =
            new CustomAudience.Builder()
                    .setName("placeholder")
                    .setBuyer(AdTechIdentifier.fromString("buyer.com"))
                    .setDailyUpdateUri(Uri.parse("https://www.buyer.com/update"))
                    .setBiddingLogicUri(Uri.parse("https://www.buyer.com/bidding"))
                    .build();

    @NonNull private final AdServicesHttpsClient mHttpClient;
    @NonNull private final Clock mClock;
    @NonNull private final Flags mFlags;
    @NonNull private final ListeningExecutorService mBackgroundExecutor;
    @NonNull private final ListeningExecutorService mLightWeightExecutor;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final CustomAudienceBlobValidator mCustomAudienceBlobValidator;
    @NonNull private final CustomAudienceImpl mCustomAudienceImpl;
    @NonNull private final CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    @NonNull private final int mMaxNameSizeB;
    @NonNull private final int mMaxUserBiddingSignalsSizeB;
    @NonNull private final long mMaxActivationDelayInMs;
    @NonNull private final long mMaxExpireInMs;
    @NonNull private final int mMaxBiddingLogicUriSizeB;
    @NonNull private final int mMaxDailyUpdateUriSizeB;
    @NonNull private final int mMaxTrustedBiddingDataSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxAdsSizeB;
    @NonNull private final int mFledgeCustomAudienceMaxNumAds;
    @NonNull private final int mFledgeCustomAudienceMaxCustomAudienceSizeB;
    private final boolean mFledgeFrequencyCapFilteringEnabled;
    private final boolean mFledgeAppInstallFilteringEnabled;
    private final boolean mFledgeAuctionServerAdRenderIdEnabled;
    private final boolean mAuctionServerRequestFlagsEnabled;
    private final boolean mSellerConfigurationEnabled;
    private final long mFledgeAuctionServerAdRenderIdMaxLength;

    @NonNull
    private final ScheduleCustomAudienceUpdateStrategy mScheduleCustomAudienceUpdateStrategy;

    @VisibleForTesting
    protected ScheduledUpdatesHandler(
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull Flags flags,
            @NonNull Clock clock,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ListeningExecutorService lightWeightExecutor,
            @NonNull FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            @NonNull AdRenderIdValidator adRenderIdValidator,
            @NonNull AdDataConversionStrategy adDataConversionStrategy,
            @NonNull CustomAudienceImpl customAudienceImpl,
            @NonNull CustomAudienceQuantityChecker customAudienceQuantityChecker,
            @NonNull ScheduleCustomAudienceUpdateStrategy scheduleCustomAudienceUpdateStrategy) {
        mCustomAudienceDao = customAudienceDao;
        mHttpClient = adServicesHttpsClient;
        mFlags = flags;
        mClock = clock;
        mBackgroundExecutor = backgroundExecutor;
        mLightWeightExecutor = lightWeightExecutor;

        mMaxNameSizeB = mFlags.getFledgeCustomAudienceMaxNameSizeB();
        mMaxActivationDelayInMs = mFlags.getFledgeCustomAudienceMaxActivationDelayInMs();
        mMaxExpireInMs = mFlags.getFledgeCustomAudienceMaxExpireInMs();
        mMaxUserBiddingSignalsSizeB =
                mFlags.getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB();
        mMaxBiddingLogicUriSizeB = mFlags.getFledgeCustomAudienceMaxBiddingLogicUriSizeB();
        mMaxDailyUpdateUriSizeB = mFlags.getFledgeCustomAudienceMaxDailyUpdateUriSizeB();
        mMaxTrustedBiddingDataSizeB = mFlags.getFledgeCustomAudienceMaxTrustedBiddingDataSizeB();
        mFledgeCustomAudienceMaxAdsSizeB = mFlags.getFledgeCustomAudienceMaxAdsSizeB();
        mFledgeCustomAudienceMaxNumAds = mFlags.getFledgeCustomAudienceMaxNumAds();
        mFledgeFrequencyCapFilteringEnabled = mFlags.getFledgeFrequencyCapFilteringEnabled();
        mFledgeAppInstallFilteringEnabled = mFlags.getFledgeAppInstallFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        mAuctionServerRequestFlagsEnabled = mFlags.getFledgeAuctionServerRequestFlagsEnabled();
        mSellerConfigurationEnabled =
                mFlags.getFledgeGetAdSelectionDataSellerConfigurationEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength =
                mFlags.getFledgeAuctionServerAdRenderIdMaxLength();
        mFledgeCustomAudienceMaxCustomAudienceSizeB =
                mFlags.getFledgeFetchCustomAudienceMaxCustomAudienceSizeB();
        mCustomAudienceQuantityChecker = customAudienceQuantityChecker;
        mCustomAudienceBlobValidator =
                new CustomAudienceBlobValidator(
                        clock,
                        new CustomAudienceNameValidator(mMaxNameSizeB),
                        new CustomAudienceUserBiddingSignalsValidator(
                                new JsonValidator(
                                        CustomAudienceBlobValidator.CLASS_NAME,
                                        USER_BIDDING_SIGNALS_KEY),
                                mMaxUserBiddingSignalsSizeB),
                        new CustomAudienceActivationTimeValidator(
                                clock, Duration.ofMillis(mMaxActivationDelayInMs)),
                        new CustomAudienceExpirationTimeValidator(
                                clock, Duration.ofMillis(mMaxExpireInMs)),
                        new AdTechIdentifierValidator(
                                CustomAudienceBlobValidator.CLASS_NAME, AD_TECH_ROLE_BUYER),
                        new CustomAudienceBiddingLogicUriValidator(mMaxBiddingLogicUriSizeB),
                        new CustomAudienceDailyUpdateUriValidator(mMaxDailyUpdateUriSizeB),
                        new TrustedBiddingDataValidator(mMaxTrustedBiddingDataSizeB),
                        new CustomAudienceAdsValidator(
                                frequencyCapAdDataValidator,
                                adRenderIdValidator,
                                adDataConversionStrategy,
                                mFledgeCustomAudienceMaxAdsSizeB,
                                mFledgeCustomAudienceMaxNumAds));
        mCustomAudienceImpl = customAudienceImpl;
        mScheduleCustomAudienceUpdateStrategy = scheduleCustomAudienceUpdateStrategy;
    }

    public ScheduledUpdatesHandler(@NonNull Context context) {
        this(
                CustomAudienceDatabase.getInstance().customAudienceDao(),
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache()),
                FlagsFactory.getFlags(),
                Clock.systemUTC(),
                AdServicesExecutors.getBackgroundExecutor(),
                AdServicesExecutors.getLightWeightExecutor(),
                new AdFilteringFeatureFactory(
                                SharedStorageDatabase.getInstance().appInstallDao(),
                                SharedStorageDatabase.getInstance().frequencyCapDao(),
                                FlagsFactory.getFlags())
                        .getFrequencyCapAdDataValidator(),
                AdRenderIdValidator.createInstance(FlagsFactory.getFlags()),
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                        FlagsFactory.getFlags().getFledgeFrequencyCapFilteringEnabled(),
                        FlagsFactory.getFlags().getFledgeAppInstallFilteringEnabled(),
                        FlagsFactory.getFlags().getFledgeAuctionServerAdRenderIdEnabled()),
                CustomAudienceImpl.getInstance(),
                new CustomAudienceQuantityChecker(
                        CustomAudienceDatabase.getInstance().customAudienceDao(),
                        FlagsFactory.getFlags()),
                ScheduleCustomAudienceUpdateStrategyFactory.createStrategy(
                        CustomAudienceDatabase.getInstance().customAudienceDao(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FlagsFactory.getFlags()
                                .getFledgeScheduleCustomAudienceMinDelayMinsOverride(),
                        FlagsFactory.getFlags()
                                .getFledgeEnableScheduleCustomAudienceUpdateAdditionalScheduleRequests()));
    }

    /** Performs Custom Audience Updates for delayed events in the schedule */
    public FluentFuture<Void> performScheduledUpdates(@NonNull Instant beforeTime) {
        FluentFuture<List<DBScheduledCustomAudienceUpdateRequest>> updateRequests =
                FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () -> {
                                    mCustomAudienceDao
                                            .deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(
                                                    beforeTime.minus(STALE_DELAYED_UPDATE_AGE));

                                    return mScheduleCustomAudienceUpdateStrategy
                                            .getScheduledCustomAudienceUpdateRequestList(
                                                    beforeTime);
                                }));
        return updateRequests.transformAsync(this::handleUpdates, mLightWeightExecutor);
    }

    private FluentFuture<Void> handleUpdates(
            List<DBScheduledCustomAudienceUpdateRequest> scheduledCustomAudienceUpdateRequests) {
        List<ListenableFuture<Void>> handledUpdates = new ArrayList<>();

        ExecutionSequencer sequencer = ExecutionSequencer.create();

        for (DBScheduledCustomAudienceUpdateRequest updateRequest :
                scheduledCustomAudienceUpdateRequests) {
            handledUpdates.add(
                    sequencer.submitAsync(
                            () ->
                                    handleSingleUpdate(
                                            updateRequest.getUpdate(),
                                            updateRequest.getPartialCustomAudienceList(),
                                            updateRequest.getCustomAudienceToLeaveList()),
                            mBackgroundExecutor));
        }
        return FluentFuture.from(Futures.successfulAsList(handledUpdates))
                .transform(
                        ignored -> {
                            sendBroadcastIntentIfEnabled();
                            return null;
                        },
                        mLightWeightExecutor);
    }

    private FluentFuture<Void> handleSingleUpdate(
            @NonNull DBScheduledCustomAudienceUpdate update,
            List<DBPartialCustomAudience> customAudienceOverrides,
            List<DBCustomAudienceToLeave> customAudienceToLeaveList) {
        List<CustomAudienceBlob> validatedPartialCustomAudienceBlobs = new ArrayList<>();

        for (DBPartialCustomAudience partialCustomAudience : customAudienceOverrides) {

            CustomAudienceBlob blob =
                    new CustomAudienceBlob(
                            mFledgeFrequencyCapFilteringEnabled,
                            mFledgeAppInstallFilteringEnabled,
                            mFledgeAuctionServerAdRenderIdEnabled,
                            mFledgeAuctionServerAdRenderIdMaxLength,
                            mAuctionServerRequestFlagsEnabled,
                            mSellerConfigurationEnabled);

            blob.overrideFromPartialCustomAudience(
                    update.getOwner(),
                    update.getBuyer(),
                    DBPartialCustomAudience.getPartialCustomAudience(partialCustomAudience));

            try {
                mCustomAudienceBlobValidator.validate(blob);
                validatedPartialCustomAudienceBlobs.add(blob);
            } catch (IllegalArgumentException e) {
                sLogger.w(e, "Blob failed validation skipping this override");
            }
        }
        sLogger.v(
                "Override blobs validation complete: %s",
                validatedPartialCustomAudienceBlobs.size());
        return fetchUpdate(update, validatedPartialCustomAudienceBlobs, customAudienceToLeaveList);
    }

    private FluentFuture<Void> fetchUpdate(
            DBScheduledCustomAudienceUpdate update,
            List<CustomAudienceBlob> validBlobs,
            List<DBCustomAudienceToLeave> customAudienceToLeaveList) {
        JSONArray partialCustomAudienceJsonArray = new JSONArray();

        for (int i = 0; i < validBlobs.size(); i++) {
            try {
                partialCustomAudienceJsonArray.put(i, validBlobs.get(i).asJSONObject());
            } catch (JSONException e) {
                sLogger.w(e, "Invalid Partial Custom Audience Object, skipping join");
            }
        }

        String bodyInString;

        try {
            bodyInString =
                    mScheduleCustomAudienceUpdateStrategy.prepareFetchUpdateRequestBody(
                            partialCustomAudienceJsonArray, customAudienceToLeaveList);
        } catch (JSONException e) {
            sLogger.w(
                    e,
                    "Exception found when preparing the request body, skipping the update request");
            throw new RuntimeException(e);
        }

        sLogger.v("Request payload: %s", bodyInString);

        // If an Update was created in a debuggable context, set the developer context to assume
        // developer options are enabled (as they were at the time of Update creation).
        // This allows for testing against localhost servers outside the context of a binder
        // connection from a debuggable app.
        DevContext devContext =
                update.getIsDebuggable()
                        ? DevContext.builder().setDeviceDevOptionsEnabled(true).build()
                        : DevContext.createForDevOptionsDisabled();

        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setUri(update.getUpdateUri())
                        .setRequestProperties(JSON_REQUEST_PROPERTIES)
                        .setHttpMethodType(AdServicesHttpUtil.HttpMethodType.POST)
                        .setBodyInBytes(bodyInString.getBytes(UTF_8))
                        .setDevContext(devContext)
                        .build();
        sLogger.v("Making scheduled update POST request");
        FluentFuture<AdServicesHttpClientResponse> response =
                FluentFuture.from(mHttpClient.performRequestGetResponseInPlainString(request));
        return response.transformAsync(
                r -> parseFetchUpdateResponse(r, update, validBlobs, devContext),
                mLightWeightExecutor);
    }

    private FluentFuture<Void> parseFetchUpdateResponse(
            AdServicesHttpClientResponse response,
            DBScheduledCustomAudienceUpdate update,
            List<CustomAudienceBlob> overrideCustomAudienceBlobs,
            DevContext devContext)
            throws JSONException {
        String responseBody = response.getResponseBody();

        JSONObject jsonResponse = new JSONObject(responseBody);

        // Extract and leave custom Audiences First to make potential space
        return FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () -> {
                                    leaveCustomAudiences(
                                            update.getOwner(),
                                            update.getBuyer(),
                                            extractLeaveCustomAudiencesFromResponse(jsonResponse));
                                }))
                .transformAsync(
                        ignoredVoid ->
                                joinCustomAudiences(
                                        overrideCustomAudienceBlobs,
                                        extractJoinCustomAudiencesFromResponse(jsonResponse),
                                        devContext),
                        mLightWeightExecutor)
                .transformAsync(
                        ignoredVoid ->
                                mScheduleCustomAudienceUpdateStrategy.scheduleRequests(
                                        update.getOwner(),
                                        update.getAllowScheduleInResponse(),
                                        jsonResponse,
                                        devContext),
                        mLightWeightExecutor)
                .transformAsync(ignoredVoid -> removeHandledUpdate(update), mLightWeightExecutor);
    }

    private void leaveCustomAudiences(
            String owner, AdTechIdentifier buyer, List<String> leaveCustomAudienceList) {

        leaveCustomAudienceList.stream()
                .forEach(leave -> mCustomAudienceImpl.leaveCustomAudience(owner, buyer, leave));
    }

    private FluentFuture<Void> removeHandledUpdate(DBScheduledCustomAudienceUpdate handledUpdate) {
        return FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () ->
                                        mCustomAudienceDao.deleteScheduledCustomAudienceUpdate(
                                                handledUpdate)))
                .transformAsync(ignored -> null, mLightWeightExecutor);
    }

    private FluentFuture<Void> joinCustomAudiences(
            @NonNull List<CustomAudienceBlob> overrideBlobs,
            @NonNull List<JSONObject> joinCustomAudienceList,
            @NonNull DevContext devContext) {

        List<ListenableFuture<Void>> persistCustomAudienceList = new ArrayList<>();

        Map<String, CustomAudienceBlob> customAudienceOverrideMap =
                overrideBlobs.stream().collect(Collectors.toMap(b -> b.getName(), b -> b));

        ExecutionSequencer sequencer = ExecutionSequencer.create();

        for (JSONObject customAudience : joinCustomAudienceList) {
            CustomAudienceBlob fusedBlob =
                    new CustomAudienceBlob(
                            mFledgeFrequencyCapFilteringEnabled,
                            mFledgeAppInstallFilteringEnabled,
                            mFledgeAuctionServerAdRenderIdEnabled,
                            mFledgeAuctionServerAdRenderIdMaxLength,
                            mAuctionServerRequestFlagsEnabled,
                            mSellerConfigurationEnabled);
            try {
                fusedBlob.overrideFromJSONObject(customAudience);
                if (customAudienceOverrideMap.containsKey(fusedBlob.getName())) {
                    fusedBlob.overrideFromJSONObject(
                            customAudienceOverrideMap.get(fusedBlob.getName()).asJSONObject());
                }

                if (!isComplete(fusedBlob)) {
                    InvalidObjectException e =
                            new InvalidObjectException(FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE);
                    sLogger.e(e, FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE);
                    throw e;
                }

                mCustomAudienceBlobValidator.validate(fusedBlob);

                if (fusedBlob.asJSONObject().toString().getBytes(UTF_8).length
                        > mFledgeCustomAudienceMaxCustomAudienceSizeB) {
                    throw new InvalidObjectException(
                            FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE);
                }
                persistCustomAudienceList.add(
                        sequencer.submitAsync(
                                () -> persistCustomAudience(fusedBlob, devContext),
                                mBackgroundExecutor));
            } catch (JSONException e) {
                sLogger.e(e, "Cannot convert response json to Custom Audience");
            } catch (InvalidObjectException e) {
                sLogger.e(e, "Cannot combine response Custom Audience with override");
            }
        }

        return FluentFuture.from(Futures.successfulAsList(persistCustomAudienceList))
                .transform(ignored -> null, mLightWeightExecutor);
    }

    // TODO(b/324474350) Refactor common code with FetchAndJoinCA API
    private FluentFuture<Void> persistCustomAudience(
            CustomAudienceBlob fusedCustomAudienceBlob, DevContext devContext) {
        sLogger.d("Persisting Custom Audience obtained from delayed update");
        return FluentFuture.from(
                        mBackgroundExecutor.submit(
                                () -> {
                                    mCustomAudienceQuantityChecker.check(
                                            PLACEHOLDER_CUSTOM_AUDIENCE,
                                            fusedCustomAudienceBlob.getOwner());
                                    boolean isDebuggableCustomAudience =
                                            devContext.getDeviceDevOptionsEnabled();
                                    sLogger.v(
                                            "Is debuggable custom audience: %b",
                                            isDebuggableCustomAudience);
                                    DBCustomAudience.Builder customAudienceBuilder =
                                            new DBCustomAudience.Builder()
                                                    .setOwner(fusedCustomAudienceBlob.getOwner())
                                                    .setBuyer(fusedCustomAudienceBlob.getBuyer())
                                                    .setName(fusedCustomAudienceBlob.getName())
                                                    .setActivationTime(
                                                            fusedCustomAudienceBlob
                                                                    .getActivationTime())
                                                    .setExpirationTime(
                                                            fusedCustomAudienceBlob
                                                                    .getExpirationTime())
                                                    .setBiddingLogicUri(
                                                            fusedCustomAudienceBlob
                                                                    .getBiddingLogicUri())
                                                    .setUserBiddingSignals(
                                                            fusedCustomAudienceBlob
                                                                    .getUserBiddingSignals())
                                                    .setTrustedBiddingData(
                                                            DBTrustedBiddingData.fromServiceObject(
                                                                    fusedCustomAudienceBlob
                                                                            .getTrustedBiddingData()))
                                                    .setDebuggable(isDebuggableCustomAudience)
                                                    .setAuctionServerRequestFlags(
                                                            fusedCustomAudienceBlob
                                                                    .getAuctionServerRequestFlags())
                                                    .setPriority(
                                                            fusedCustomAudienceBlob.getPriority());

                                    List<DBAdData> ads = new ArrayList<>();
                                    for (AdData ad : fusedCustomAudienceBlob.getAds()) {
                                        ads.add(
                                                new DBAdData.Builder()
                                                        .setRenderUri(ad.getRenderUri())
                                                        .setMetadata(ad.getMetadata())
                                                        .setAdCounterKeys(ad.getAdCounterKeys())
                                                        .setAdFilters(ad.getAdFilters())
                                                        .build());
                                    }

                                    customAudienceBuilder.setAds(ads);

                                    Instant currentTime = mClock.instant();
                                    customAudienceBuilder.setCreationTime(currentTime);
                                    customAudienceBuilder.setLastAdsAndBiddingDataUpdatedTime(
                                            currentTime);
                                    DBCustomAudience customAudience = customAudienceBuilder.build();

                                    // Persist response
                                    mCustomAudienceDao.insertOrOverwriteCustomAudience(
                                            customAudience,
                                            fusedCustomAudienceBlob.getDailyUpdateUri(),
                                            isDebuggableCustomAudience);
                                }))
                .transform(ignored -> null, mLightWeightExecutor);
    }

    private List<String> extractLeaveCustomAudiencesFromResponse(
            @NonNull JSONObject updateResponseJson) {
        List<String> customAudienceList = new ArrayList<>();
        try {
            JSONArray jsonArray = updateResponseJson.getJSONArray(LEAVE_CUSTOM_AUDIENCE_KEY);

            for (int i = 0; i < jsonArray.length(); i++) {
                customAudienceList.add(jsonArray.getString(i));
            }
            sLogger.d("No of CAs to leave obtained from update: %s", customAudienceList.size());
        } catch (JSONException e) {
            sLogger.e(e, "Unable to parse any Custom Audiences To Leave");
        }
        return customAudienceList;
    }

    private List<JSONObject> extractJoinCustomAudiencesFromResponse(
            @NonNull JSONObject updateResponseJson) {
        List<JSONObject> customAudienceJsonList = new ArrayList<>();
        try {
            JSONArray jsonArray = updateResponseJson.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY);

            for (int i = 0; i < jsonArray.length(); i++) {
                customAudienceJsonList.add(jsonArray.getJSONObject(i));
            }
            sLogger.d("No of CAs to join obtained from update: %s", customAudienceJsonList.size());
        } catch (JSONException e) {
            sLogger.e(e, "Unable to parse any Custom Audiences To Join");
        }
        return customAudienceJsonList;
    }

    private boolean isComplete(CustomAudienceBlob fusedCustomAudience) {
        HashSet<String> expectedKeysSet = new HashSet<>(CustomAudienceBlob.mKeysSet);
        HashSet<String> currentKeySet = new HashSet<>(fusedCustomAudience.mFieldsMap.keySet());

        if (mAuctionServerRequestFlagsEnabled) {
            currentKeySet.remove(AUCTION_SERVER_REQUEST_FLAGS_KEY);
        }

        if (mSellerConfigurationEnabled) {
            currentKeySet.remove(PRIORITY_KEY);
        }
        return currentKeySet.size() == expectedKeysSet.size();
    }

    private void sendBroadcastIntentIfEnabled() {
        if (DebugFlags.getInstance().getFledgeScheduleCACompleteBroadcastEnabled()) {
            Context context = ApplicationContextSingleton.get();
            sLogger.d(
                    "Sending a broadcast intent with intent action: %s",
                    ACTION_SCHEDULE_CA_COMPLETE_INTENT);
            context.sendBroadcast(new Intent(ACTION_SCHEDULE_CA_COMPLETE_INTENT));
        }
    }
}
