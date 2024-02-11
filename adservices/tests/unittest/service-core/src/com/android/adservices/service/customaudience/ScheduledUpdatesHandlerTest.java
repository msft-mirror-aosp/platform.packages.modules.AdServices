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

import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.JOIN_CUSTOM_AUDIENCE_KEY;
import static com.android.adservices.service.customaudience.ScheduledUpdatesHandler.LEAVE_CUSTOM_AUDIENCE_KEY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBTrustedBiddingDataFixture;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

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
    private static final Long UPDATE_ID = 1L;
    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final Uri UPDATE_URI = CommonFixture.getUri(BUYER, "/updateUri");
    private static final Instant CREATION_TIME = CommonFixture.FIXED_NOW;
    private static final Instant SCHEDULED_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    private static final DBScheduledCustomAudienceUpdate UPDATE =
            DBScheduledCustomAudienceUpdate.builder()
                    .setUpdateId(1L)
                    .setUpdateUri(UPDATE_URI)
                    .setCreationTime(CREATION_TIME)
                    .setScheduledTime(SCHEDULED_TIME)
                    .setOwner(OWNER)
                    .setBuyer(BUYER)
                    .build();
    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private static final String PARTIAL_CA_1 = "partial_ca_1";
    private static final String PARTIAL_CA_2 = "partial_ca_2";
    private static final String LEAVE_CA_1 = "leave_ca_1";
    private static final String LEAVE_CA_2 = "leave_ca_2";
    private static final Instant ACTIVATION_TIME = CommonFixture.FIXED_NOW;
    private static final Instant EXPIRATION_TIME = CommonFixture.FIXED_NEXT_ONE_DAY;
    private static final String SIGNALS_STRING = "{\"a\":\"b\"}";
    private static final AdSelectionSignals VALID_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(SIGNALS_STRING);
    private static final DBPartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_1 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_1)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();

    private static final DBPartialCustomAudience PARTIAL_CUSTOM_AUDIENCE_2 =
            DBPartialCustomAudience.builder()
                    .setUpdateId(UPDATE_ID)
                    .setName(PARTIAL_CA_2)
                    .setActivationTime(ACTIVATION_TIME)
                    .setExpirationTime(EXPIRATION_TIME)
                    .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                    .build();
    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    @Rule public MockitoRule rule = MockitoJUnit.rule();
    @Captor ArgumentCaptor<AdServicesHttpClientRequest> mRequestCaptor;
    @Captor ArgumentCaptor<DBCustomAudience> mInsertCustomAudienceCaptor;
    private boolean mFledgeAdSelectionFilteringEnabled;
    private boolean mFledgeAuctionServerAdRenderIdEnabled;
    private long mFledgeAuctionServerAdRenderIdMaxLength;
    private Flags mFlags;
    private DevContext mDevContext =
            DevContext.builder()
                    .setDevOptionsEnabled(false)
                    .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME_1)
                    .build();
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    @Mock private CustomAudienceImpl mCustomAudienceImplMock;
    @Mock private DevContextFilter mDevContextFilterMock;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;
    private ScheduledUpdatesHandler mHandler;

    @Before
    public void setup() {
        mFlags = new ScheduleCustomAudienceUpdateFlags();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDaoMock, mFrequencyCapDaoMock, mFlags);
        when(mDevContextFilterMock.createDevContext()).thenReturn(mDevContext);

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
                        mDevContextFilterMock);

        mFledgeAdSelectionFilteringEnabled = mFlags.getFledgeAdSelectionFilteringEnabled();
        mFledgeAuctionServerAdRenderIdEnabled = mFlags.getFledgeAuctionServerAdRenderIdEnabled();
        mFledgeAuctionServerAdRenderIdMaxLength =
                mFlags.getFledgeAuctionServerAdRenderIdMaxLength();
    }

    @Test
    public void testPerformScheduledUpdates_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2));
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
    public void testPerformScheduledUpdates_PartialCaDifferentNames_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

        String nonOverriddenCaName = "non_overridden_ca";

        String responsePayload =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        List.of(nonOverriddenCaName, PARTIAL_CA_1, PARTIAL_CA_2),
                        List.of(LEAVE_CA_1, LEAVE_CA_2));
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
        }
    }

    @Test
    public void testPerformScheduledUpdates_InvalidPartialCa_PartialSuccess()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

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
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2));
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

        verify(mCustomAudienceDaoMock, times(1))
                .insertOrOverwriteCustomAudience(
                        mInsertCustomAudienceCaptor.capture(), any(Uri.class), anyBoolean());

        List<DBCustomAudience> joinedCustomAudiences = mInsertCustomAudienceCaptor.getAllValues();

        assertEquals("Only 1 CA should been valid and persisted", 1, joinedCustomAudiences.size());
        assertTrue(
                "Joined Custom Audiences should have persisted only valid the CA",
                joinedCustomAudiences.stream()
                        .map(ca -> ca.getName())
                        .collect(Collectors.toList())
                        .contains(PARTIAL_CA_1));
        assertEquals(
                "Bidding signals should have been overridden for valid CA",
                VALID_BIDDING_SIGNALS.toString(),
                joinedCustomAudiences.get(0).getUserBiddingSignals().toString());
    }

    @Test
    public void testPerformScheduledUpdates_NoOverrides_Success()
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

        List<DBPartialCustomAudience> partialCustomAudienceList = Collections.emptyList();
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

        byte[] expectedRequestBody =
                createJsonRequestPayloadFromPartialCustomAudience(partialCustomAudienceList);

        String nonOverriddenCaName = "non_overridden_ca";

        String responsePayload =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        List.of(nonOverriddenCaName),
                        List.of(LEAVE_CA_1, LEAVE_CA_2));
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
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

        List<DBPartialCustomAudience> partialCustomAudienceList =
                List.of(PARTIAL_CUSTOM_AUDIENCE_1, PARTIAL_CUSTOM_AUDIENCE_2);
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

        String responsePayload =
                createJsonResponsePayload(
                        UPDATE.getBuyer(),
                        UPDATE.getOwner(),
                        partialCustomAudienceList.stream()
                                .map(ca -> ca.getName())
                                .collect(Collectors.toList()),
                        List.of(LEAVE_CA_1, LEAVE_CA_2));
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
                        mDevContextFilterMock);
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
            throws JSONException, ExecutionException, InterruptedException, TimeoutException {
        when(mCustomAudienceDaoMock.getCustomAudienceUpdatesScheduledBeforeTime(any(Instant.class)))
                .thenReturn(List.of(UPDATE));

        List<DBPartialCustomAudience> partialCustomAudienceList = Collections.emptyList();
        when(mCustomAudienceDaoMock.getPartialAudienceListForUpdateId(UPDATE.getUpdateId()))
                .thenReturn(partialCustomAudienceList);

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

    byte[] createJsonRequestPayloadFromPartialCustomAudience(
            List<DBPartialCustomAudience> partialCustomAudienceList) throws JSONException {
        List<CustomAudienceBlob> validBlobs = new ArrayList<>();

        for (DBPartialCustomAudience partialCustomAudience : partialCustomAudienceList) {
            CustomAudienceBlob blob =
                    new CustomAudienceBlob(
                            mFledgeAdSelectionFilteringEnabled,
                            mFledgeAuctionServerAdRenderIdEnabled,
                            mFledgeAuctionServerAdRenderIdMaxLength);
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

    private String createJsonResponsePayload(
            AdTechIdentifier buyer,
            String owner,
            List<String> joinCustomAudienceNames,
            List<String> leaveCustomAudienceNames)
            throws JSONException {

        JSONObject responseJson = new JSONObject();

        JSONArray joinCustomAudienceArray = new JSONArray();
        for (int i = 0; i < joinCustomAudienceNames.size(); i++) {
            JSONObject generatedCa =
                    generateCustomAudienceWithName(buyer, owner, joinCustomAudienceNames.get(i));
            joinCustomAudienceArray.put(i, generatedCa);
        }

        JSONArray leaveCustomAudienceArray = new JSONArray();
        for (int i = 0; i < leaveCustomAudienceNames.size(); i++) {
            leaveCustomAudienceArray.put(i, leaveCustomAudienceNames.get(i));
        }

        responseJson.put(JOIN_CUSTOM_AUDIENCE_KEY, joinCustomAudienceArray);
        responseJson.put(LEAVE_CUSTOM_AUDIENCE_KEY, leaveCustomAudienceArray);

        return responseJson.toString();
    }

    private JSONObject generateCustomAudienceWithName(
            AdTechIdentifier buyer, String owner, String name) throws JSONException {

        CustomAudience ca =
                CustomAudienceFixture.getValidBuilderForBuyer(buyer).setName(name).build();
        return CustomAudienceBlobFixture.asJSONObject(
                owner,
                ca.getBuyer(),
                name,
                ca.getActivationTime(),
                ca.getExpirationTime(),
                ca.getDailyUpdateUri(),
                ca.getBiddingLogicUri(),
                AdSelectionSignals.EMPTY.toString(),
                DBTrustedBiddingDataFixture.getValidBuilderByBuyer(buyer).build(),
                Collections.emptyList(),
                false);
    }

    private static class ScheduleCustomAudienceUpdateFlags implements Flags {
        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }
    }

    private static class FlagsWithSmallSizeLimits implements Flags {
        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
            return 0;
        }
    }
}
