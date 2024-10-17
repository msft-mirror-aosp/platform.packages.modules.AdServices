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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ACTIVATION_TIME;
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
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createRequestBody;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.eqJsonArray;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.eqJsonObject;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateCustomAudienceWithName;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.STALE_DELAYED_UPDATE_AGE;

import static com.google.common.util.concurrent.Futures.immediateVoidFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
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
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public final class ScheduledUpdatesHandlerTest {

    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final String OWNER_2 = "com.android.test.2";
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String CUSTOM_AUDIENCE_NAME_1 = "custom_audience_1";
    private static final String CUSTOM_AUDIENCE_NAME_2 = "custom_audience_2";
    private static final String CUSTOM_AUDIENCE_NAME_3 = "custom_audience_3";
    private static final Uri UPDATE_URI = CommonFixture.getUri(BUYER, "/updateUri");
    private static final Instant CREATION_TIME = FIXED_NOW;
    private static final Instant SCHEDULED_TIME = CREATION_TIME.plus(180, ChronoUnit.MINUTES);
    private static final DBScheduledCustomAudienceUpdate UPDATE =
            DBScheduledCustomAudienceUpdate.builder()
                    .setUpdateId(UPDATE_ID)
                    .setUpdateUri(UPDATE_URI)
                    .setCreationTime(CREATION_TIME)
                    .setScheduledTime(SCHEDULED_TIME)
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .build();
    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true, true);

    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Captor ArgumentCaptor<AdServicesHttpClientRequest> mRequestCaptor;
    @Captor ArgumentCaptor<DBCustomAudience> mInsertCustomAudienceCaptor;
    private boolean mFledgeFrequencyCapFilteringEnabled;
    private boolean mFledgeAppInstallFilteringEnabled;
    private boolean mFledgeAuctionServerAdRenderIdEnabled;
    private boolean mAuctionServerRequestFlags;
    private boolean mSellerConfigurationEnabled;
    private long mFledgeAuctionServerAdRenderIdMaxLength;
    private Flags mFlags;
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

    @Before
    public void setup() {
        mFlags = new ScheduleCustomAudienceUpdateFlags();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDaoMock, mFrequencyCapDaoMock, mFlags);

        mHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDaoMock,
                        mAdServicesHttpsClientMock,
                        mFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock);

        mFledgeFrequencyCapFilteringEnabled = mFlags.getFledgeFrequencyCapFilteringEnabled();
        mFledgeAppInstallFilteringEnabled = mFlags.getFledgeAppInstallFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength =
                mFlags.getFledgeAuctionServerAdRenderIdMaxLength();
        mAuctionServerRequestFlags = mFlags.getFledgeAuctionServerRequestFlagsEnabled();
        mSellerConfigurationEnabled =
                mFlags.getFledgeGetAdSelectionDataSellerConfigurationEnabled();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();
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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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
                        mFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock);

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
                        any(DevContext.class)))
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
                        mStrategyMock);
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
                        mStrategyMock);
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
                        mStrategyMock);
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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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
                expectedRequestBody.toString(),
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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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
                expectedRequestBody.toString(),
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

        LogUtil.e(partialCustomAudienceList.toString());
        LogUtil.e(partialCustomAudienceJsonArray.toString());

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

        String expectedRequestBody = partialCustomAudienceJsonArray.toString();

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
                expectedRequestBody.toString(),
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
                        mStrategyMock);
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
        when(mStrategyMock.scheduleRequests(any(), anyBoolean(), any(), any(DevContext.class)))
                .thenReturn(FluentFuture.from(immediateVoidFuture()));
        when(mStrategyMock.prepareFetchUpdateRequestBody(any(), any()))
                .thenReturn(new JSONObject().toString());

        Void ignored = mHandler.performScheduledUpdates(invocationTime).get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceDaoMock)
                .deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(
                        invocationTime.minus(STALE_DELAYED_UPDATE_AGE));

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
        mFlags =
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
                        mFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock);
    }

    private void enableSellerConfigurationFlag() {
        // Enable seller configuration flag
        mFlags =
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
                        mFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock);
    }

    private void disableSellerConfigurationFlag() {
        // Disable seller configuration flag
        // Safeguard to ensure that tests continue to work as intended when flag is turned on
        mFlags =
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
                        mFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                        mAdRenderIdValidator,
                        AD_DATA_CONVERSION_STRATEGY,
                        mCustomAudienceImplMock,
                        mCustomAudienceQuantityCheckerMock,
                        mStrategyMock);
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
                        any(DevContext.class)))
                .thenReturn(FluentFuture.from(immediateVoidFuture()));
        when(mStrategyMock.prepareFetchUpdateRequestBody(
                        eqJsonArray(partialCustomAudienceJsonArray), eq(Collections.emptyList())))
                .thenReturn(partialCustomAudienceJsonArray.toString());
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
                        eq(update.getOwner()), eq(false), eqJsonObject(responseJson), any());
    }
}
