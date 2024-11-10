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

import static android.adservices.common.CommonFixture.FIXED_NOW;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudience.PRIORITY_DEFAULT;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_PRIORITY_1;

import static com.android.adservices.service.Flags.FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpsClient.DEFAULT_TIMEOUT_MS;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ACTIVATION_TIME;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.BUYER_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_CUSTOM_AUDIENCE_TO_LEAVE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_CUSTOM_AUDIENCE_TO_LEAVE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_PARTIAL_CUSTOM_AUDIENCE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_PARTIAL_CUSTOM_AUDIENCE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.DB_PARTIAL_CUSTOM_AUDIENCE_3;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.UPDATE_ID;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.VALID_BIDDING_SIGNALS;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayload;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadInvalidJoinCA;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithInvalidExpirationTime;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithScheduleRequests;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithoutJoinCA;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayloadWithoutLeaveCA;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createRequestBody;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createRequestBodyWithOnlyPartialCustomAudiences;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.eqJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.eqJsonObject;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateCustomAudienceWithName;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateScheduleRequestFromCustomAudienceNames;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateScheduleRequestFromCustomAudienceNamesWithInvalidPartialCA;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateScheduleRequestMissingUpdateUriKey;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.STALE_DELAYED_UPDATE_AGE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CLIENT_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CONTENT_SIZE_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_IO_EXCEPTION;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_REDIRECTION;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_SERVER_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_TOO_MANY_REQUESTS;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.PartialCustomAudience;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceToLeave;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdateRequest;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.cache.CacheDatabase;
import com.android.adservices.service.common.cache.CacheEntryDao;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.HttpContentSizeException;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdateBackgroundJobStats;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedFailureStats;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdatePerformedStats;
import com.android.adservices.service.stats.ScheduledCustomAudienceUpdateScheduleAttemptedStats;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

@MockStatic(BackgroundFetchJob.class)
public final class ScheduledUpdatesHandlerTest extends AdServicesExtendedMockitoTestCase {

    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final String OWNER_2 = "com.android.test.2";
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String CUSTOM_AUDIENCE_NAME_1 = "custom_audience_1";
    private static final String CUSTOM_AUDIENCE_NAME_2 = "custom_audience_2";
    private static final String CUSTOM_AUDIENCE_NAME_3 = "custom_audience_3";
    private static final Uri UPDATE_URI = CommonFixture.getUri(BUYER, "/updateUri");
    private static final Instant CREATION_TIME = FIXED_NOW;
    private static final Instant SCHEDULED_TIME = CREATION_TIME.plus(180, ChronoUnit.MINUTES);
    private static final long MAX_AGE_SECONDS = 120;
    private static final long MAX_ENTRIES = 20;
    private static final int INVALID_MIN_DELAY = 10000;
    private static final int VALID_MIN_DELAY = 100;
    private static final String INVALID_CA_NAME = "";
    private static final DBScheduledCustomAudienceUpdate UPDATE =
            DBScheduledCustomAudienceUpdate.builder()
                    .setUpdateId(UPDATE_ID)
                    .setUpdateUri(UPDATE_URI)
                    .setCreationTime(CREATION_TIME)
                    .setScheduledTime(SCHEDULED_TIME)
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .build();
    private final ExecutorService mExecutorService = MoreExecutors.newDirectExecutorService();

    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true, true);

    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    @Captor ArgumentCaptor<AdServicesHttpClientRequest> mRequestCaptor;
    @Captor ArgumentCaptor<DBCustomAudience> mInsertCustomAudienceCaptor;

    @Captor
    ArgumentCaptor<ScheduledCustomAudienceUpdatePerformedFailureStats>
            mScheduleCAFailureStatsCaptor;

    @Captor
    ArgumentCaptor<ScheduledCustomAudienceUpdatePerformedStats>
            mScheduleCAUpdatePerformedStatsCaptor;

    @Captor
    ArgumentCaptor<ScheduledCustomAudienceUpdateBackgroundJobStats>
            mScheduleCABackgroundJobStatsCaptor;

    private boolean mFledgeFrequencyCapFilteringEnabled;
    private boolean mFledgeAppInstallFilteringEnabled;
    private boolean mFledgeAuctionServerAdRenderIdEnabled;
    private boolean mAuctionServerRequestFlags;
    private boolean mSellerConfigurationEnabled;
    private long mFledgeAuctionServerAdRenderIdMaxLength;
    private Flags mFakeFlags;
    @NonNull private CustomAudienceDao mCustomAudienceDao;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private CustomAudienceImpl mCustomAudienceImplMock;
    @Mock private CustomAudienceQuantityChecker mCustomAudienceQuantityCheckerMock;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;
    private ScheduledUpdatesHandler mHandler;
    @Mock private ScheduleCustomAudienceUpdateStrategy mStrategyMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Mock
    private ScheduledCustomAudienceUpdateScheduleAttemptedStats.Builder mScheduleAttemptedBuilder;

    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private HttpCache mCache;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private AdditionalScheduleRequestsEnabledStrategyHelper mHelper;
    private AdditionalScheduleRequestsEnabledStrategy mEnabledStrategy;

    @Before
    public void setup() throws Exception {
        mFakeFlags = new ScheduleCustomAudienceUpdateFlags();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDaoMock, mFrequencyCapDaoMock, mFakeFlags);

        mHelper =
                new AdditionalScheduleRequestsEnabledStrategyHelper(
                        ApplicationProvider.getApplicationContext(),
                        mFledgeAuthorizationFilterMock,
                        FLEDGE_SCHEDULE_CUSTOM_AUDIENCE_UPDATE_MIN_DELAY_MINS_OVERRIDE,
                        /* disableFledgeEnrollmentCheck */ true);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);

        mFledgeFrequencyCapFilteringEnabled = mFakeFlags.getFledgeFrequencyCapFilteringEnabled();
        mFledgeAppInstallFilteringEnabled = mFakeFlags.getFledgeAppInstallFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled =
                mFakeFlags.getFledgeAuctionServerAdRenderIdEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength =
                mFakeFlags.getFledgeAuctionServerAdRenderIdMaxLength();
        mAuctionServerRequestFlags = mFakeFlags.getFledgeAuctionServerRequestFlagsEnabled();
        mSellerConfigurationEnabled =
                mFakeFlags.getFledgeGetAdSelectionDataSellerConfigurationEnabled();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
        CacheEntryDao cacheEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(), CacheDatabase.class)
                        .build()
                        .getCacheEntryDao();

        mCache = new FledgeHttpCache(cacheEntryDao, MAX_AGE_SECONDS, MAX_ENTRIES);
        mAdServicesHttpsClient = new AdServicesHttpsClient(mExecutorService, mCache);

        mEnabledStrategy =
                new AdditionalScheduleRequestsEnabledStrategy(
                        mCustomAudienceDao,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mHelper,
                        mAdServicesLoggerMock);
    }

    @Test
    public void testPerformScheduledUpdates_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        Instant beforeTime = Instant.now();

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void
            testPerformScheduledUpdates_AdditionalScheduleRequestsFlagEnabled_WithCustomAudiencesToLeave_Success()
                    throws JSONException,
                            ExecutionException,
                            InterruptedException,
                            TimeoutException {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        List<DBCustomAudienceToLeave> customAudienceToLeaveList =
                List.of(DB_CUSTOM_AUDIENCE_TO_LEAVE_1, DB_CUSTOM_AUDIENCE_TO_LEAVE_2);

        Instant beforeTime = Instant.now();

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .setCustomAudienceToLeaveList(customAudienceToLeaveList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        customAudienceToLeaveList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBody(partialCustomAudienceJsonArray, customAudienceToLeaveList)
                        .toString();

        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        when(mStrategyMock.getScheduledCustomAudienceUpdateRequestList(beforeTime))
                .thenReturn(List.of(scheduledUpdateRequest));

        when(mStrategyMock.scheduleRequests(
                        eq(scheduledUpdateRequest.getUpdate().getOwner()),
                        eq(scheduledUpdateRequest.getUpdate().getAllowScheduleInResponse()),
                        eqJsonObject(responseJson),
                        any(DevContext.class),
                        any()))
                .thenReturn(FluentFuture.from(immediateVoidFuture()));

        when(mStrategyMock.prepareFetchUpdateRequestBody(
                        eqJsonArray(partialCustomAudienceJsonArray), eq(customAudienceToLeaveList)))
                .thenReturn(expectedRequestBody);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        verify(mStrategyMock).getScheduledCustomAudienceUpdateRequestList(beforeTime);
        verify(mStrategyMock)
                .prepareFetchUpdateRequestBody(
                        eqJsonArray(partialCustomAudienceJsonArray), eq(customAudienceToLeaveList));
        verify(mStrategyMock)
                .scheduleRequests(
                        eq(UPDATE.getOwner()),
                        eq(UPDATE.getAllowScheduleInResponse()),
                        eqJsonObject(responseJson),
                        any(),
                        any());
    }

    @Test
    public void testPerformScheduledUpdates_withQuantityChecker_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        // We set the flag values such that the max number of CA per owner is 2.
        // We return 3 CA from the server response and assert that only two has been joined.
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(
                        DB_PARTIAL_CUSTOM_AUDIENCE_1,
                        DB_PARTIAL_CUSTOM_AUDIENCE_2,
                        DB_PARTIAL_CUSTOM_AUDIENCE_3);

        Flags flagsWithCAQuantityCheckerValues =
                new FakeFlagsFactory.TestFlags() {
                    @Override
                    public long getFledgeCustomAudienceMaxOwnerCount() {
                        return 2;
                    }

                    @Override
                    public long getFledgeCustomAudienceMaxCount() {
                        return 2;
                    }
                };
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, flagsWithCAQuantityCheckerValues);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        flagsWithCAQuantityCheckerValues,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        customAudienceQuantityChecker,
                        mStrategyMock,
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        Instant beforeTime = UPDATE.getScheduledTime().plusSeconds(1000);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(UPDATE.getBuyer()), FIXED_NOW, 10000);
        assertTrue(
                "There should be only 2 joined Custom Audiences",
                joinedCustomAudiences.stream()
                        .map(DBCustomAudience::getName)
                        .collect(Collectors.toList())
                        .containsAll(List.of(PARTIAL_CA_1, PARTIAL_CA_2)));

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_twoOwners_oneCaPerOwner_joinsOneCAForEachOwner()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        // We set the flag values such that the max number of CA per owner is 1.
        // We return 3 CA from the server response: CA1 & CA2 from OWNER_1 and CA3 from OWNER_2  and
        // assert that CA1 and CA3 has joined.

        Flags flagsWithCAQuantityCheckerValues =
                new FakeFlagsFactory.TestFlags() {
                    @Override
                    public long getFledgeCustomAudienceMaxOwnerCount() {
                        return 100;
                    }

                    @Override
                    public long getFledgeCustomAudienceMaxCount() {
                        return 100;
                    }

                    @Override
                    public long getFledgeCustomAudiencePerAppMaxCount() {
                        return 1;
                    }
                };
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, flagsWithCAQuantityCheckerValues);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        flagsWithCAQuantityCheckerValues,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        customAudienceQuantityChecker,
                        mStrategyMock,
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        JSONObject responseJson = new JSONObject();
        JSONArray joinCustomAudienceArray = new JSONArray();
        joinCustomAudienceArray.put(
                0, generateCustomAudienceWithName(BUYER, OWNER, CUSTOM_AUDIENCE_NAME_1));
        joinCustomAudienceArray.put(
                1, generateCustomAudienceWithName(BUYER, OWNER, CUSTOM_AUDIENCE_NAME_2));
        joinCustomAudienceArray.put(
                2, generateCustomAudienceWithName(BUYER, OWNER_2, CUSTOM_AUDIENCE_NAME_3));
        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);

        Instant beforeTime = UPDATE.getScheduledTime().plusSeconds(1000);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder().setUpdate(UPDATE).build();

        mockDisabledStrategy(beforeTime, scheduledUpdateRequest, responseJson, new JSONArray());

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(UPDATE.getBuyer()), FIXED_NOW, 10000);
        assertTrue(
                "There should be only 2 joined Custom Audiences",
                joinedCustomAudiences.stream()
                        .map(DBCustomAudience::getName)
                        .collect(Collectors.toList())
                        .containsAll(List.of(CUSTOM_AUDIENCE_NAME_1, CUSTOM_AUDIENCE_NAME_3)));

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, new JSONArray());
    }

    @Test
    public void testPerformScheduledUpdates_withQuantityCheckerLimitZero_doesNotJoinAnyCA()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        // We set the flag values such that the max number of CA per owner is 0.
        // We return 3 CA from the server response and assert that no custom audience has been
        // joined.
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(
                        DB_PARTIAL_CUSTOM_AUDIENCE_1,
                        DB_PARTIAL_CUSTOM_AUDIENCE_2,
                        DB_PARTIAL_CUSTOM_AUDIENCE_3);

        Flags flagsWithCAQuantityCheckerValues =
                new FakeFlagsFactory.TestFlags() {
                    @Override
                    public long getFledgeCustomAudienceMaxOwnerCount() {
                        return 0;
                    }

                    @Override
                    public long getFledgeCustomAudienceMaxCount() {
                        return 0;
                    }
                };
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, flagsWithCAQuantityCheckerValues);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        flagsWithCAQuantityCheckerValues,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        customAudienceQuantityChecker,
                        mStrategyMock,
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        Instant beforeTime = UPDATE.getScheduledTime().plusSeconds(1000);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(UPDATE.getBuyer()), FIXED_NOW, 10000);
        assertTrue(
                "There should be only 0 joined Custom Audiences", joinedCustomAudiences.isEmpty());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithAuctionServerRequestFlagsEnabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        enableAuctionServerRequestFlags();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true,
                        /* sellerConfigurationEnabled= */ false);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Auction server flags should not equal 0",
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                joinedCustomAudiences.get(0).getAuctionServerRequestFlags());
        assertEquals(
                "Auction server flags should not equal 0",
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                joinedCustomAudiences.get(1).getAuctionServerRequestFlags());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithAuctionServerRequestFlagsDisabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true,
                        /* sellerConfigurationEnabled= */ false);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Auction server flags should equal 0 since flag is disabled",
                0,
                joinedCustomAudiences.get(0).getAuctionServerRequestFlags());
        assertEquals(
                "Auction server flags should equal 0 since flag is disabled",
                0,
                joinedCustomAudiences.get(1).getAuctionServerRequestFlags());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void
            testPerformScheduledUpdates_SuccessWithAuctionServerRequestFlagsEnabledButNoFlagsInResponse()
                    throws JSONException,
                            ExecutionException,
                            InterruptedException,
                            TimeoutException {
        enableAuctionServerRequestFlags();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        false,
                        /* sellerConfigurationEnabled= */ false);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Auction server flags should equal 0 since json response did not have flags",
                0,
                joinedCustomAudiences.get(0).getAuctionServerRequestFlags());
        assertEquals(
                "Auction server flags should equal 0 since json response did not have flags",
                0,
                joinedCustomAudiences.get(1).getAuctionServerRequestFlags());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdatesDoesNotPersistCAsWithMissingField() throws Exception {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        // Remove ADS_KEY from both of the CAs to join
        responseJson.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(0).remove(ADS_KEY);
        responseJson.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(1).remove(ADS_KEY);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        // Verify custom audiences were not inserted since field is missing
        verify(mCustomAudienceDaoMock, never())
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void
            testPerformScheduledUpdatesThrowsExceptionForMissingFieldWithAuctionServerRequestFlagsEnabled()
                    throws Exception {
        enableAuctionServerRequestFlags();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        // Remove ADS_KEY from both of the CAs to join
        responseJson.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(0).remove(ADS_KEY);
        responseJson.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(1).remove(ADS_KEY);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        // Verify custom audiences were not inserted since field is missing
        verify(mCustomAudienceDaoMock, never())
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithSellerConfigurationEnabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        enableSellerConfigurationFlag();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        false,
                        /* sellerConfigurationEnabled= */ true);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Priority should not equal 0.0, should be 1.0",
                0,
                Double.compare(VALID_PRIORITY_1, joinedCustomAudiences.get(0).getPriority()));
        assertEquals(
                "Priority should not equal 0.0, should be 1.0",
                0,
                Double.compare(VALID_PRIORITY_1, joinedCustomAudiences.get(1).getPriority()));

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithSellerConfigurationDisabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        disableSellerConfigurationFlag();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        false,
                        /* sellerConfigurationEnabled= */ false);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Priority should equal 0.0 since flag is disabled",
                0,
                Double.compare(PRIORITY_DEFAULT, joinedCustomAudiences.get(0).getPriority()));

        assertEquals(
                "Priority should equal 0.0 since flag is disabled",
                0,
                Double.compare(PRIORITY_DEFAULT, joinedCustomAudiences.get(1).getPriority()));

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void
            testPerformScheduledUpdates_doesNotUpdatePriorityValueWhenSellerConfigurationDisabled()
                    throws JSONException,
                            ExecutionException,
                            InterruptedException,
                            TimeoutException {
        disableSellerConfigurationFlag();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        // simulate a persisted custom audience with a priority value by setting seller
        // configuration to true
        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        false,
                        /* sellerConfigurationEnabled= */ true);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());

        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        verify(mCustomAudienceDaoMock).deleteScheduledCustomAudienceUpdate(UPDATE);

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList())));
        assertEquals(
                "Bidding signals should have been overridden",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        assertEquals(
                "Priority should equal 0.0 since flag is disabled",
                0,
                Double.compare(PRIORITY_DEFAULT, joinedCustomAudiences.get(0).getPriority()));

        assertEquals(
                "Priority should equal 0.0 since flag is disabled",
                0,
                Double.compare(PRIORITY_DEFAULT, joinedCustomAudiences.get(1).getPriority()));

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_PartialCaDifferentNames_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        String nonOverriddenCaName = "non_overridden_ca";

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        List.of(nonOverriddenCaName, PARTIAL_CA_1, PARTIAL_CA_2),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(3))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        assertTrue(
                "Joined Custom Audiences should have all the CAs in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(List.of(nonOverriddenCaName, PARTIAL_CA_1, PARTIAL_CA_2)));

        for (DBCustomAudience joinedCa : joinedCustomAudiences) {
            if (joinedCa.getName().equals(nonOverriddenCaName)) {
                assertEquals(
                        "Bidding signals should not have been overridden for this CA with no "
                                + "matching Partial CA in request",
                        AdSelectionSignals.EMPTY.toString(),
                        joinedCa.getUserBiddingSignals().toString());
            } else {
                assertEquals(
                        "Bidding signals should have been overridden",
                        VALID_BIDDING_SIGNALS.toString(),
                        joinedCa.getUserBiddingSignals().toString());
            }
            assertFalse(
                    "CA created by non debuggable updates should also be non-debuggable",
                    joinedCa.isDebuggable());
        }

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_InvalidPartialCa_PartialSuccess()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {

        DBPartialCustomAudience invalidPartialCustomAudience2 =
                DBPartialCustomAudience.builder()
                        .setUpdateId(UPDATE_ID)
                        .setName(PARTIAL_CA_2)
                        .setActivationTime(ACTIVATION_TIME)
                        .setExpirationTime(ACTIVATION_TIME.minus(20, ChronoUnit.DAYS))
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, invalidPartialCustomAudience2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1));


        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());

        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(2))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        assertEquals("Both CAs should have been persisted", 2, joinedCustomAudiences.size());
        assertTrue(
                "Joined Custom Audiences should have persisted only valid the CA",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .contains(PARTIAL_CA_1));
        assertEquals(
                "Bidding signals should have been overridden for valid CA",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.stream()
                        .filter(ca -> ca.getName().equals(PARTIAL_CA_1))
                        .findFirst()
                        .get()
                        .getUserBiddingSignals()
                        .toString());
        assertEquals(
                "Bidding signals should not have been overridden for invalid Partial CA",
                AdSelectionSignals.EMPTY.toString(),
                joinedCustomAudiences.stream()
                        .filter(ca -> ca.getName().equals(PARTIAL_CA_2))
                        .findFirst()
                        .get()
                        .getUserBiddingSignals()
                        .toString());
    }

    @Test
    public void testPerformScheduledUpdates_NoOverrides_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList = Collections.emptyList();

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        String nonOverriddenCaName = "non_overridden_ca";

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        List.of(nonOverriddenCaName),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        String expectedRequestBody =
                createRequestBodyWithOnlyPartialCustomAudiences(partialCustomAudienceJsonArray);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload should have been empty",
                expectedRequestBody,
                new String(mRequestCaptor.getValue().getBodyInBytes()));

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(1))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        assertTrue(
                "Joined Custom Audiences should have one CA in response",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .containsAll(List.of(nonOverriddenCaName)));
        assertEquals(
                "Bidding signals should not have been overridden",
                AdSelectionSignals.EMPTY.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_LargeInsertFail_LeaveSuccess()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        JSONObject responseJson =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false,
                        /* sellerConfigurationEnabled= */ false);

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        // The small permissible size of incoming CA would prevent any new CA to be inserted
        ScheduledUpdatesHandler handlerWithSmallSizeLimits =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        new FlagsWithSmallSizeLimits(),
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);
        Void ignored =
                handlerWithSmallSizeLimits
                        .performScheduledUpdates(beforeTime)
                        .get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(0))
                .insertOrOverwriteCustomAudience(
                        any(DBCustomAudience.class), any(Uri.class), anyBoolean());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_EmptyResponse_SilentPass()
            throws ExecutionException, InterruptedException, TimeoutException, JSONException {

        List<DBPartialCustomAudience> partialCustomAudienceList = Collections.emptyList();

        DBScheduledCustomAudienceUpdateRequest scheduledUpdateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        Instant beforeTime = Instant.now();

        JSONArray partialCustomAudienceJsonArray =
                createJsonArrayFromPartialCustomAudienceList(partialCustomAudienceList);

        JSONObject responseJson = new JSONObject();

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responseJson.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        mockDisabledStrategy(
                beforeTime, scheduledUpdateRequest, responseJson, partialCustomAudienceJsonArray);

        Void ignored = mHandler.performScheduledUpdates(beforeTime).get(10, TimeUnit.SECONDS);

        verifyNoMoreInteractions(mCustomAudienceImplMock);
        verify(mCustomAudienceDaoMock, times(0))
                .insertOrOverwriteCustomAudience(
                        any(DBCustomAudience.class), any(Uri.class), anyBoolean());

        verifyDisabledStrategy(beforeTime, UPDATE, responseJson, partialCustomAudienceJsonArray);
    }

    @Test
    public void testPerformScheduledUpdates_ClearsStaleUpdates_Success()
            throws ExecutionException, InterruptedException, TimeoutException, JSONException {
        Instant invocationTime = FIXED_NOW;

        when(mStrategyMock.getScheduledCustomAudienceUpdateRequestList(invocationTime))
                .thenReturn(Collections.emptyList());
        when(mStrategyMock.scheduleRequests(
                        any(), anyBoolean(), any(), any(DevContext.class), any()))
                .thenReturn(FluentFuture.from(immediateVoidFuture()));
        when(mStrategyMock.prepareFetchUpdateRequestBody(any(), any()))
                .thenReturn(new JSONObject().toString());

        Void ignored = mHandler.performScheduledUpdates(invocationTime).get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceDaoMock)
                .deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(
                        invocationTime.minus(STALE_DELAYED_UPDATE_AGE));
    }


    @Test
    public void testPerformScheduledUpdates_withNoJoinField_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        String responsePayload =
                createJsonResponsePayloadWithoutJoinCA(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        // "join" is an optional field in the response. Absence of it should not be logged as an
        // error
        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedFailureStats(any());

        // Background job should not be scheduled as no custom audiences are joined
        verify(() -> BackgroundFetchJob.schedule(any()), never());
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_invalidMinDelay_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue);

        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        INVALID_MIN_DELAY,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        false);

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats actualStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action type")
                .that(actualStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
        assertWithMessage("Failure type")
                .that(actualStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);
        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(0);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_missingURI_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue);

        JSONObject scheduleRequest = generateScheduleRequestMissingUpdateUriKey();

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats actualStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action type")
                .that(actualStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
        assertWithMessage("Failure type")
                .that(actualStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);
        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(0);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_invalidPartialCAInScheduledCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        List<PartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(DBPartialCustomAudience::getPartialCustomAudience)
                        .collect(Collectors.toList());
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue,
                dbPartialCustomAudienceList,
                Collections.emptyList(),
                true,
                mScheduleAttemptedBuilder);

        JSONObject invalidScheduleRequest =
                generateScheduleRequestFromCustomAudienceNamesWithInvalidPartialCA(
                        UPDATE.getBuyer(),
                        VALID_MIN_DELAY,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);
        JSONObject validScheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        VALID_MIN_DELAY,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);

        JSONArray scheduleRequests =
                new JSONArray(List.of(validScheduleRequest, invalidScheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(1, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats actualStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action type")
                .that(actualStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
        assertWithMessage("Failure type")
                .that(actualStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);
        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(2);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(1);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_invalidLeaveCAInScheduledCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        List<PartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(DBPartialCustomAudience::getPartialCustomAudience)
                        .collect(Collectors.toList());
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue,
                dbPartialCustomAudienceList,
                Collections.emptyList(),
                true,
                mScheduleAttemptedBuilder);

        JSONObject invalidScheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        VALID_MIN_DELAY,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(INVALID_CA_NAME),
                        true);
        JSONObject validScheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        VALID_MIN_DELAY,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);

        JSONArray scheduleRequests =
                new JSONArray(List.of(validScheduleRequest, invalidScheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(1, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats actualStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action type")
                .that(actualStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
        assertWithMessage("Failure type")
                .that(actualStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);
        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(2);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(1);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_withScheduledCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue);

        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        40,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);

        JSONArray scheduleRequests = new JSONArray(List.of(scheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // A new scheduled custom audience update will be inserted in the database.
        assertEquals(1, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(1);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_withScheduledCAAndJoinCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue);

        String responsePayload =
                createJsonResponsePayloadWithoutLeaveCA(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                false,
                                false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // A new scheduled custom audience update will be inserted in the database.
        assertEquals(1, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(2);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(1);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedFailureStats(any());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdateBackgroundJobStats(
                        mScheduleCABackgroundJobStatsCaptor.capture());
        ScheduledCustomAudienceUpdateBackgroundJobStats backgroundJobStats =
                mScheduleCABackgroundJobStatsCaptor.getValue();

        assertWithMessage("Number of total updates")
                .that(backgroundJobStats.getNumberOfUpdatesFound())
                .isEqualTo(1);
        assertWithMessage("Number of updates successful")
                .that(backgroundJobStats.getNumberOfSuccessfulUpdates())
                .isEqualTo(1);
    }

    @Test
    public void testPerformScheduledUpdatesV2_firstHop_bothValidAndInvalidSCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mEnabledStrategy,
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithAllowScheduleInResponseTrue =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setAllowScheduleInResponse(true)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithAllowScheduleInResponseTrue);

        JSONObject scheduleRequest =
                generateScheduleRequestFromCustomAudienceNames(
                        UPDATE.getBuyer(),
                        40,
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        true);

        JSONObject invalidScheduleRequest = generateScheduleRequestMissingUpdateUriKey();

        JSONArray scheduleRequests =
                new JSONArray(List.of(scheduleRequest, invalidScheduleRequest));
        String responsePayload =
                createJsonResponsePayloadWithScheduleRequests(scheduleRequests).toString();

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(
                                updateWithAllowScheduleInResponseTrue
                                        .getScheduledTime()
                                        .plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // A new scheduled custom audience update will be inserted in the database.
        assertEquals(1, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();

        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
        assertWithMessage("Number of schedule custom audience updates in response")
                .that(performedStats.getNumberOfScheduleUpdatesInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of custom audience updates scheduled")
                .that(performedStats.getNumberOfUpdatesScheduled())
                .isEqualTo(1);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats failureStats =
                mScheduleCAFailureStatsCaptor.getValue();
        assertWithMessage("Failure action type")
                .that(failureStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_SCHEDULE_CA_UPDATE);
    }

    @Test
    public void testPerformScheduledUpdates_httpErrorTooManyRequests_logsCorrectly()
            throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        MockResponse mockResponse = new MockResponse().setResponseCode(429);
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_TOO_MANY_REQUESTS);
    }

    @Test
    public void testPerformScheduledUpdates_oneSuccessfulOneUnsuccessfulUpdate_logsStatsCorrectly()
            throws Exception {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);
        String invalidUrl = "/invalid";
        String correctUrl = "/correct";
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                true,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            if (request.getPath().equals(invalidUrl)) {
                                return new MockResponse().setResponseCode(429);
                            }
                            if (request.getPath().equals(correctUrl)) {
                                return new MockResponse().setBody(responsePayload);
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(server.getUrl(correctUrl).toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        DBScheduledCustomAudienceUpdate updateWithInvalidUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID + 1)
                        .setUpdateUri(Uri.parse(server.getUrl(invalidUrl).toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithInvalidUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateBackgroundJobStats(
                        mScheduleCABackgroundJobStatsCaptor.capture());
        ScheduledCustomAudienceUpdateBackgroundJobStats loggedStats =
                mScheduleCABackgroundJobStatsCaptor.getValue();

        assertWithMessage("Number of updates found")
                .that(loggedStats.getNumberOfUpdatesFound())
                .isEqualTo(2);
        assertWithMessage("Number of successful updates")
                .that(loggedStats.getNumberOfSuccessfulUpdates())
                .isEqualTo(1);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats failureStats =
                mScheduleCAFailureStatsCaptor.getValue();
        assertWithMessage("Failure action")
                .that(failureStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(failureStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_TOO_MANY_REQUESTS);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(2);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(2);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
    }

    @Test
    public void testPerformScheduledUpdates_withMultipleSuccessfulUpdates_logsCorrectly()
            throws Exception {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_3);
        List<DBPartialCustomAudience> partialCustomAudienceList2 = Collections.emptyList();
        String correctUrl = "/correct";
        String correctUrl2 = "/correct2";
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                List.of(
                                        DB_PARTIAL_CUSTOM_AUDIENCE_1.getName(),
                                        DB_PARTIAL_CUSTOM_AUDIENCE_3.getName()),
                                List.of(),
                                true,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        String responsePayload2 =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_2.getName()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                true,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        request -> {
                            if (request.getPath().equals(correctUrl)) {
                                return new MockResponse().setBody(responsePayload);
                            }
                            if (request.getPath().equals(correctUrl2)) {
                                return new MockResponse().setBody(responsePayload2);
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(server.getUrl(correctUrl).toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        DBScheduledCustomAudienceUpdate updateWithCorrectUri2 =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID + 1)
                        .setUpdateUri(Uri.parse(server.getUrl(correctUrl2).toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER_2)
                        .build();
        List<PartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(DBPartialCustomAudience::getPartialCustomAudience)
                        .collect(Collectors.toList());
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                updateWithCorrectUri,
                dbPartialCustomAudienceList,
                Collections.emptyList(),
                false,
                mScheduleAttemptedBuilder);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri2);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateBackgroundJobStats(
                        mScheduleCABackgroundJobStatsCaptor.capture());
        ScheduledCustomAudienceUpdateBackgroundJobStats loggedStats =
                mScheduleCABackgroundJobStatsCaptor.getValue();

        assertWithMessage("Number of updates found")
                .that(loggedStats.getNumberOfUpdatesFound())
                .isEqualTo(2);
        assertWithMessage("Number of successful updates")
                .that(loggedStats.getNumberOfSuccessfulUpdates())
                .isEqualTo(2);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        verify(mAdServicesLoggerMock, times(2))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedStats expectedFirstStats =
                ScheduledCustomAudienceUpdatePerformedStats.builder()
                        .setNumberOfPartialCustomAudienceInRequest(2)
                        .setNumberOfJoinCustomAudienceInResponse(2)
                        .setNumberOfCustomAudienceJoined(2)
                        .setNumberOfLeaveCustomAudienceInResponse(0)
                        .setNumberOfCustomAudienceLeft(0)
                        .build();

        ScheduledCustomAudienceUpdatePerformedStats expectedSecondStats =
                ScheduledCustomAudienceUpdatePerformedStats.builder()
                        .setNumberOfPartialCustomAudienceInRequest(0)
                        .setNumberOfJoinCustomAudienceInResponse(1)
                        .setNumberOfCustomAudienceJoined(1)
                        .setNumberOfLeaveCustomAudienceInResponse(2)
                        .setNumberOfCustomAudienceLeft(2)
                        .build();

        assertWithMessage("Expected scheduled custom audience performed stats")
                .that(mScheduleCAUpdatePerformedStatsCaptor.getAllValues())
                .containsExactly(expectedFirstStats, expectedSecondStats);
    }

    @Test
    public void testPerformScheduledUpdates_httpError_logsBackgroundJobStatsCorrectly()
            throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        MockResponse mockResponse = new MockResponse().setResponseCode(429);
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateBackgroundJobStats(
                        mScheduleCABackgroundJobStatsCaptor.capture());
        ScheduledCustomAudienceUpdateBackgroundJobStats loggedStats =
                mScheduleCABackgroundJobStatsCaptor.getValue();

        assertWithMessage("Number of updates found")
                .that(loggedStats.getNumberOfUpdatesFound())
                .isEqualTo(1);
        assertWithMessage("Number of successful updates")
                .that(loggedStats.getNumberOfSuccessfulUpdates())
                .isEqualTo(0);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedStats(any());
    }

    @Test
    public void testPerformScheduledUpdates_httpBadRequest_logsCorrectly() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        // Setting the response code to 400 for bad request client error.
        MockResponse mockResponse = new MockResponse().setResponseCode(400);
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CLIENT_ERROR);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedStats(any());
    }

    @Test
    public void testPerformScheduledUpdates_httpServerError_logsCorrectly() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        // Setting the response code to 500 for server error.
        MockResponse mockResponse = new MockResponse().setResponseCode(500);
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_SERVER_ERROR);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedStats(any());
    }

    @Test
    public void testPerformScheduledUpdates_IOException_logsCorrectly() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFailedFuture(new IOException());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_IO_EXCEPTION);
    }

    @Test
    public void testPerformScheduledUpdates_ContentSizeException_logsCorrectly() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(UPDATE_URI)
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFailedFuture(
                        new HttpContentSizeException("Content size exceeds limit"));
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CONTENT_SIZE_ERROR);
    }

    @Test
    public void testPerformScheduledUpdates_withNoLeaveField_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        String responsePayload =
                createJsonResponsePayloadWithoutLeaveCA(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(),
                                /* auctionServerRequestFlagsEnabled= */ false,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(UPDATE.getBuyer()), FIXED_NOW, 10000);
        assertTrue(
                "There should be only 2 joined Custom Audiences",
                joinedCustomAudiences.stream()
                        .map(DBCustomAudience::getName)
                        .collect(Collectors.toList())
                        .containsAll(List.of(PARTIAL_CA_1, PARTIAL_CA_2)));

        // "leave" is an optional field in the response. Absence of it should not be logged as an
        // error
        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedFailureStats(any());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(2);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
    }

    @Test
    public void testPerformScheduledUpdates_httpRedirectionError_logsCorrectly() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClient,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        // Setting the response code to 500 for server error.
        MockResponse mockResponse = new MockResponse().setResponseCode(300);
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");
        DBScheduledCustomAudienceUpdate updateWithCorrectUri =
                DBScheduledCustomAudienceUpdate.builder()
                        .setUpdateId(UPDATE_ID)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setCreationTime(CREATION_TIME)
                        .setScheduledTime(SCHEDULED_TIME)
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .build();

        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(updateWithCorrectUri);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_REDIRECTION);

        verify(mAdServicesLoggerMock, never())
                .logScheduledCustomAudienceUpdatePerformedStats(any());
    }

    @Test
    public void testPerformScheduledUpdates_withQualityCheckerException_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1);

        DBScheduledCustomAudienceUpdateRequest updateRequest =
                DBScheduledCustomAudienceUpdateRequest.builder()
                        .setUpdate(UPDATE)
                        .setPartialCustomAudienceList(partialCustomAudienceList)
                        .build();

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDaoMock),
                        mAdServicesLoggerMock);

        when(mCustomAudienceDaoMock.getScheduledCustomAudienceUpdateRequests(any(Instant.class)))
                .thenReturn(List.of(updateRequest));

        doThrow(new IllegalArgumentException())
                .when(mCustomAudienceQuantityCheckerMock)
                .check(any(), any());

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedFailureStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedFailureStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA);
        assertWithMessage("Failure type")
                .that(loggedFailureStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdateBackgroundJobStats(
                        mScheduleCABackgroundJobStatsCaptor.capture());
        ScheduledCustomAudienceUpdateBackgroundJobStats loggedBackgroundStats =
                mScheduleCABackgroundJobStatsCaptor.getValue();

        assertWithMessage("Number of updates found")
                .that(loggedBackgroundStats.getNumberOfUpdatesFound())
                .isEqualTo(1);
        assertWithMessage("Number of successful updates")
                .that(loggedBackgroundStats.getNumberOfSuccessfulUpdates())
                .isEqualTo(1);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(2);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(1);
    }

    @Test
    public void testPerformScheduledUpdates_JsonExceptionForJoin_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1, DB_PARTIAL_CUSTOM_AUDIENCE_2);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);

        List<PartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(DBPartialCustomAudience::getPartialCustomAudience)
                        .collect(Collectors.toList());
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                UPDATE,
                dbPartialCustomAudienceList,
                Collections.emptyList(),
                false,
                mScheduleAttemptedBuilder);

        String responsePayload =
                createJsonResponsePayloadInvalidJoinCA(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(),
                                /* auctionServerRequestFlagsEnabled= */ false,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());

        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_JSON_ERROR);

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(UPDATE.getBuyer()), FIXED_NOW, 10000);
        assertTrue(
                "There should be only 1 joined Custom Audiences",
                joinedCustomAudiences.stream()
                        .map(DBCustomAudience::getName)
                        .collect(Collectors.toList())
                        .containsAll(List.of(PARTIAL_CA_1)));

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(1);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(2);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(partialCustomAudienceList.size());
    }

    @Test
    public void testPerformScheduledUpdates_withInvalidObjectExceptionDuringJoinCA_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1);

        Flags flagsWithCASizeZero =
                new ScheduleCustomAudienceUpdateFlags() {
                    @Override
                    public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
                        return 0;
                    }
                };

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        flagsWithCASizeZero,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        List<PartialCustomAudience> dbPartialCustomAudienceList =
                partialCustomAudienceList.stream()
                        .map(DBPartialCustomAudience::getPartialCustomAudience)
                        .collect(Collectors.toList());
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                UPDATE,
                dbPartialCustomAudienceList,
                Collections.emptyList(),
                false,
                mScheduleAttemptedBuilder);

        String responsePayload =
                createJsonResponsePayloadWithoutLeaveCA(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(),
                                /* auctionServerRequestFlagsEnabled= */ false,
                                /* sellerConfigurationEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());
        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();
        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(partialCustomAudienceList.size());
    }

    @Test
    public void testPerformScheduledUpdates_exceptionDueToInvalidExpirationTime_logsCorrectly()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(DB_PARTIAL_CUSTOM_AUDIENCE_1);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        new AdditionalScheduleRequestsDisabledStrategy(mCustomAudienceDao),
                        mAdServicesLoggerMock);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(
                UPDATE,
                Collections.emptyList(),
                Collections.emptyList(),
                false,
                mScheduleAttemptedBuilder);

        String responsePayload =
                createJsonResponsePayloadWithInvalidExpirationTime(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()))
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored =
                mHandler.performScheduledUpdates(UPDATE.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock).performRequestGetResponseInPlainString(any());

        List<DBScheduledCustomAudienceUpdate> customAudienceScheduledUpdatesInDB =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(UPDATE.getOwner());
        // Scheduled updates should be deleted from the database.
        assertEquals(0, customAudienceScheduledUpdatesInDB.size());

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();
        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_JOIN_CA);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock, times(1))
                .logScheduledCustomAudienceUpdatePerformedStats(
                        mScheduleCAUpdatePerformedStatsCaptor.capture());
        ScheduledCustomAudienceUpdatePerformedStats performedStats =
                mScheduleCAUpdatePerformedStatsCaptor.getValue();
        assertWithMessage("Number of custom audience joined")
                .that(performedStats.getNumberOfCustomAudienceJoined())
                .isEqualTo(0);
        assertWithMessage("Number of join custom audience in response")
                .that(performedStats.getNumberOfJoinCustomAudienceInResponse())
                .isEqualTo(1);
        assertWithMessage("Number of leave custom audience in response")
                .that(performedStats.getNumberOfLeaveCustomAudienceInResponse())
                .isEqualTo(0);
        assertWithMessage("Number of custom audiences left")
                .that(performedStats.getNumberOfCustomAudienceLeft())
                .isEqualTo(0);
        assertWithMessage("Number of number of partial custom audience in request")
                .that(performedStats.getNumberOfPartialCustomAudienceInRequest())
                .isEqualTo(0);
    }

    @Test
    public void testFetchUpdate_maxBytes_Failure() throws Exception {
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                DEFAULT_TIMEOUT_MS,
                                DEFAULT_TIMEOUT_MS,
                                /* maxBytes= */ 1),
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);

        MockResponse mockResponse =
                new MockResponse().setBody("larger than 1 byte").setResponseCode(200);

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(ImmutableList.of(mockResponse));
        URL updateUri = server.getUrl("/update");

        DBScheduledCustomAudienceUpdate update =
                DBScheduledCustomAudienceUpdate.builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setUpdateUri(Uri.parse(updateUri.toString()))
                        .setScheduledTime(Instant.now())
                        .setCreationTime(Instant.now())
                        .build();

        DBScheduledCustomAudienceUpdateRequest request =
                DBScheduledCustomAudienceUpdateRequest.builder().setUpdate(update).build();

        when(mStrategyMock.prepareFetchUpdateRequestBody(new JSONArray(), List.of()))
                .thenReturn("");

        when(mStrategyMock.getScheduledCustomAudienceUpdateRequestList(any()))
                .thenReturn(List.of(request));

        Void ignored =
                mHandler.performScheduledUpdates(update.getScheduledTime().plusSeconds(1000))
                        .get(10, TimeUnit.SECONDS);

        verify(mAdServicesLoggerMock)
                .logScheduledCustomAudienceUpdatePerformedFailureStats(
                        mScheduleCAFailureStatsCaptor.capture());

        ScheduledCustomAudienceUpdatePerformedFailureStats loggedStats =
                mScheduleCAFailureStatsCaptor.getValue();

        assertWithMessage("Failure action")
                .that(loggedStats.getFailureAction())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_ACTION_HTTP_CALL);
        assertWithMessage("Failure type")
                .that(loggedStats.getFailureType())
                .isEqualTo(SCHEDULE_CA_UPDATE_PERFORMED_FAILURE_TYPE_HTTP_CONTENT_SIZE_ERROR);
    }

    private JSONArray createJsonArrayFromPartialCustomAudienceList(
            List<DBPartialCustomAudience> partialCustomAudienceList) throws JSONException {
        List<CustomAudienceBlob> validBlobs = new ArrayList<>();

        for (DBPartialCustomAudience partialCustomAudience : partialCustomAudienceList) {
            CustomAudienceBlob blob =
                    new CustomAudienceBlob(
                            mFledgeFrequencyCapFilteringEnabled,
                            mFledgeAppInstallFilteringEnabled,
                            mFledgeAuctionServerAdRenderIdEnabled,
                            mFledgeAuctionServerAdRenderIdMaxLength,
                            mAuctionServerRequestFlags,
                            mSellerConfigurationEnabled);
            blob.overrideFromPartialCustomAudience(
                    OWNER,
                    BUYER,
                    DBPartialCustomAudience.getPartialCustomAudience(partialCustomAudience));
            validBlobs.add(blob);
        }

        JSONArray jsonArray = new JSONArray();
        int i = 0;
        for (CustomAudienceBlob blob : validBlobs) {
            jsonArray.put(i, blob.asJSONObject());
            i++;
        }

        return jsonArray;
    }

    private static class ScheduleCustomAudienceUpdateFlags implements Flags {
        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }
    }

    private static class FlagsWithSmallSizeLimits implements Flags {
        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }

        @Override
        public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
            return 0;
        }
    }

    private void enableAuctionServerRequestFlags() {
        // Enable auction server request flags
        mFakeFlags =
                new ScheduleCustomAudienceUpdateFlags() {
                    @Override
                    public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                        return true;
                    }
                };
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);
    }

    private void enableSellerConfigurationFlag() {
        // Enable seller configuration flag
        mFakeFlags =
                new ScheduleCustomAudienceUpdateFlags() {
                    @Override
                    public boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
                        return true;
                    }
                };
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);
    }

    private void disableSellerConfigurationFlag() {
        // Disable seller configuration flag
        // Safeguard to ensure that tests continue to work as intended when flag is turned on
        mFakeFlags =
                new ScheduleCustomAudienceUpdateFlags() {
                    @Override
                    public boolean getFledgeGetAdSelectionDataSellerConfigurationEnabled() {
                        return false;
                    }
                };
        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFakeFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock,
                        mAdServicesLoggerMock);
    }

    private void mockDisabledStrategy(
            Instant beforeTime,
            DBScheduledCustomAudienceUpdateRequest request,
            JSONObject responseJson,
            JSONArray partialCustomAudienceJsonArray)
            throws JSONException {
        when(mStrategyMock.getScheduledCustomAudienceUpdateRequestList(beforeTime))
                .thenReturn(List.of(request));
        when(mStrategyMock.scheduleRequests(
                        eq(request.getUpdate().getOwner()),
                        eq(request.getUpdate().getAllowScheduleInResponse()),
                        eqJsonObject(responseJson),
                        any(DevContext.class),
                        any()))
                .thenReturn(FluentFuture.from(immediateVoidFuture()));
        when(mStrategyMock.prepareFetchUpdateRequestBody(
                        eqJsonArray(partialCustomAudienceJsonArray), eq(Collections.emptyList())))
                .thenReturn(
                        createRequestBodyWithOnlyPartialCustomAudiences(
                                partialCustomAudienceJsonArray));
    }

    private void verifyDisabledStrategy(
            Instant beforeTime,
            DBScheduledCustomAudienceUpdate update,
            JSONObject responseJson,
            JSONArray partialCustomAudienceJsonArray)
            throws JSONException {
        verify(mStrategyMock).getScheduledCustomAudienceUpdateRequestList(beforeTime);
        verify(mStrategyMock)
                .prepareFetchUpdateRequestBody(
                        eqJsonArray(partialCustomAudienceJsonArray), eq(Collections.emptyList()));
        verify(mStrategyMock)
                .scheduleRequests(
                        eq(update.getOwner()), eq(false), eqJsonObject(responseJson), any(), any());
    }
}
