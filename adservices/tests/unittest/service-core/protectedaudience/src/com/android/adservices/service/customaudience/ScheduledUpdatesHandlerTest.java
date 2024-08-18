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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ACTIVATION_TIME;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_3;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.UPDATE_ID;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.VALID_BIDDING_SIGNALS;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayload;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.generateCustomAudienceWithName;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.STALE_DELAYED_UPDATE_AGE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;

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
                        mCustomAudienceQuantityCheckerMock);

        mFledgeFrequencyCapFilteringEnabled = mFlags.getFledgeFrequencyCapFilteringEnabled();
        mFledgeAppInstallFilteringEnabled = mFlags.getFledgeAppInstallFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength =
                mFlags.getFledgeAuctionServerAdRenderIdMaxLength();
        mAuctionServerRequestFlags = mFlags.getFledgeAuctionServerRequestFlagsEnabled();
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
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                new String(expectedRequestBody),
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
    }

    @Test
    public void testPerformScheduledUpdates_withQuantityChecker_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        // We set the flag values such that the max number of CA per owner is 2.
        // We return 3 CA from the server response and assert that only two has been joined.
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(
                        PARTIAL_CUSTOM_AUDIENCE_1,
                        PARTIAL_CUSTOM_AUDIENCE_2,
                        PARTIAL_CUSTOM_AUDIENCE_3);

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
                        customAudienceQuantityChecker);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(),
                                /* auctionServerRequestFlagsEnabled= */ false)
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
                        customAudienceQuantityChecker);
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
        String responsePayload = responseJson.toString();

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
                        .containsAll(List.of(CUSTOM_AUDIENCE_NAME_1, CUSTOM_AUDIENCE_NAME_3)));
    }

    @Test
    public void testPerformScheduledUpdates_withQuantityCheckerLimitZero_doesNotJoinAnyCA()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        // We set the flag values such that the max number of CA per owner is 0.
        // We return 3 CA from the server response and assert that no custom audience has been
        // joined.
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(
                        PARTIAL_CUSTOM_AUDIENCE_1,
                        PARTIAL_CUSTOM_AUDIENCE_2,
                        PARTIAL_CUSTOM_AUDIENCE_3);

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
                        customAudienceQuantityChecker);
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(UPDATE);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(),
                                /* auctionServerRequestFlagsEnabled= */ false)
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
                "There should be only 0 joined Custom Audiences", joinedCustomAudiences.isEmpty());
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithAuctionServerRequestFlagsEnabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        enableAuctionServerRequestFlags();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                true)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                new String(expectedRequestBody),
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
    }

    @Test
    public void testPerformScheduledUpdates_SuccessWithAuctionServerRequestFlagsDisabled()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                true)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                new String(expectedRequestBody),
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
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload mismatch",
                new String(expectedRequestBody),
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
    }

    @Test
    public void testPerformScheduledUpdatesDoesNotPersistCAsWithMissingField() throws Exception {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        JSONObject responsePayloadJSON =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false);

        // Remove ADS_KEY from both of the CAs to join
        responsePayloadJSON.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(0).remove(ADS_KEY);
        responsePayloadJSON.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(1).remove(ADS_KEY);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayloadJSON.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        // Verify custom audiences were not inserted since field is missing
        verify(mCustomAudienceDaoMock, never())
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());
    }

    @Test
    public void
            testPerformScheduledUpdatesThrowsExceptionForMissingFieldWithAuctionServerRequestFlagsEnabled()
                    throws Exception {
        enableAuctionServerRequestFlags();

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        JSONObject responsePayloadJSON =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2),
                        /* auctionServerRequestFlagsEnabled= */ false);

        // Remove ADS_KEY from both of the CAs to join
        responsePayloadJSON.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(0).remove(ADS_KEY);
        responsePayloadJSON.getJSONArray(JOIN_CUSTOM_AUDIENCE_KEY).getJSONObject(1).remove(ADS_KEY);

        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayloadJSON.toString())
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);
        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        // Verify custom audiences were not inserted since field is missing
        verify(mCustomAudienceDaoMock, never())
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());
    }

    @Test
    public void testPerformScheduledUpdates_PartialCaDifferentNames_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        String nonOverriddenCaName = "non_overridden_ca";

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                List.of(nonOverriddenCaName, PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

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
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, invalidPartialCustomAudience2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

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

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String nonOverriddenCaName = "non_overridden_ca";

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                List.of(nonOverriddenCaName),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verify(mAdServicesHttpsClientMock)
                .performRequestGetResponseInPlainString(mRequestCaptor.capture());
        assertEquals(
                "Request method should have been POST",
                AdServicesHttpUtil.HttpMethodType.POST,
                mRequestCaptor.getValue().getHttpMethodType());
        assertEquals(
                "Sent payload should have been empty",
                new String(expectedRequestBody),
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
    }

    @Test
    public void testPerformScheduledUpdates_LargeInsertFail_LeaveSuccess()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);

        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        String responsePayload =
                createJsonResponsePayload(
                                UPDATE.getBuyer(),
                                UPDATE.getOwner(),
                                partialCustomAudienceList.stream()
                                        .map(ca -> ca.getName())
                                        .collect(Collectors.toList()),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(responsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

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
                        mCustomAudienceQuantityCheckerMock);
        Void ignored =
                handlerWithSmallSizeLimits
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_1);
        verify(mCustomAudienceImplMock)
                .leaveCustomAudience(UPDATE.getOwner(), UPDATE.getBuyer(), LEAVE_CA_2);

        verify(mCustomAudienceDaoMock, times(0))
                .insertOrOverwriteCustomAudience(
                        any(DBCustomAudience.class), any(Uri.class), anyBoolean());
    }

    @Test
    public void testPerformScheduledUpdates_EmptyResponse_SilentPass()
            throws ExecutionException, InterruptedException, TimeoutException {

        List<DBPartialCustomAudience> partialCustomAudienceList = Collections.emptyList();
        Pair<DBScheduledCustomAudienceUpdate, List<DBPartialCustomAudience>>
                updateAndOverridesPair = new Pair(UPDATE, partialCustomAudienceList);
        when(mCustomAudienceDaoMock.getScheduledUpdatesAndOverridesBeforeTime(any(Instant.class)))
                .thenReturn(List.of(updateAndOverridesPair));

        String emptyResponsePayload = "{}";
        ListenableFuture<AdServicesHttpClientResponse> response =
                Futures.immediateFuture(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(emptyResponsePayload)
                                .build());
        when(mAdServicesHttpsClientMock.performRequestGetResponseInPlainString(any()))
                .thenReturn(response);

        Void ignored = mHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        verifyNoMoreInteractions(mCustomAudienceImplMock);
        verify(mCustomAudienceDaoMock, times(0))
                .insertOrOverwriteCustomAudience(
                        any(DBCustomAudience.class), any(Uri.class), anyBoolean());
    }

    @Test
    public void testPerformScheduledUpdates_ClearsStaleUpdates_Success()
            throws ExecutionException, InterruptedException, TimeoutException {
        Instant invocationTime = FIXED_NOW;
        Void ignored = mHandler.performScheduledUpdates(invocationTime).get(10, TimeUnit.SECONDS);

        verify(mCustomAudienceDaoMock)
                .deleteScheduledCustomAudienceUpdatesCreatedBeforeTime(
                        invocationTime.minus(STALE_DELAYED_UPDATE_AGE));
    }

    private byte[] createJsonRequestPayloadFromPartialCustomAudience(
            List<DBPartialCustomAudience> partialCustomAudienceList) throws JSONException {
        List<CustomAudienceBlob> validBlobs = new ArrayList<>();

        for (DBPartialCustomAudience partialCustomAudience : partialCustomAudienceList) {
            CustomAudienceBlob blob =
                    new CustomAudienceBlob(
                            mFledgeFrequencyCapFilteringEnabled,
                            mFledgeAppInstallFilteringEnabled,
                            mFledgeAuctionServerAdRenderIdEnabled,
                            mFledgeAuctionServerAdRenderIdMaxLength,
                            mAuctionServerRequestFlags);
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

        return jsonArray.toString().getBytes(UTF_8);
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
                        mCustomAudienceQuantityCheckerMock);
    }
}