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

package android.adservices.adselection;

import static android.adservices.adselection.GetAdSelectionDataResponseFixture.getAdSelectionDataResponseWithAssetFileDescriptor;
import static android.adservices.adselection.GetAdSelectionDataResponseFixture.getAdSelectionDataResponseWithByteArray;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.net.Uri;
import android.os.Build;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import com.google.common.base.Strings;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** Unit tests for {@link AdSelectionManager} */
public final class AdSelectionManagerTest extends AdServicesMockitoTestCase {
    // AdId constants
    private static final String AD_ID = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";

    // reportEvent constants
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();
    private static final long AD_SELECTION_ID = 1234L;
    private static final String EVENT_KEY = "click";
    private static final String CALLER_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private static final int REPORTING_DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;

    private static final long SLEEP_TIME_MS = 200;
    private static final int TYPICAL_PAYLOAD_SIZE_BYTES = 1024; // 1kb
    private static final int EXCESSIVE_PAYLOAD_SIZE_BYTES =
            TYPICAL_PAYLOAD_SIZE_BYTES * 2 * 1024; // 2Mb

    private final String mEventData;
    private final ReportEventRequest mReportEventRequest;

    @Mock private AdSelectionService mMockAdSelectionService;

    @Mock private AdIdManager mMockAdIdManager;

    @Captor private ArgumentCaptor<ReportInteractionInput> mCaptorReportInteractionInput;

    private AdSelectionManager mAdSelectionManager;

    public AdSelectionManagerTest() throws Exception {
        mEventData = new JSONObject().put("key", "value").toString();
        mReportEventRequest =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, EVENT_KEY, mEventData, REPORTING_DESTINATIONS)
                        .build();
    }

    @Before
    public void initializeManagerWithMocks() {
        mAdSelectionManager =
                AdSelectionManager.get(mContext, mMockAdIdManager, mMockAdSelectionService);
    }

    @Test
    @RequiresSdkLevelAtLeastT
    public void testAdSelectionManagerCtor_TPlus() {
        expect.that(AdSelectionManager.get(mContext)).isNotNull();
        expect.that(mContext.getSystemService(AdSelectionManager.class)).isNotNull();
    }

    @Test
    @RequiresSdkRange(atMost = Build.VERSION_CODES.S_V2)
    public void testAdSelectionManagerCtor_SMinus() {
        expect.that(AdSelectionManager.get(mContext)).isNotNull();
        expect.that(mContext.getSystemService(AdSelectionManager.class)).isNull();
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdEnabled() throws Exception {
        // Set expected outcome of AdIdManager#getAdId
        mockGetAdId(new AdId(AD_ID, true));

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new OutcomeReceiverForTests<>());

        // Assert values passed to the service are as expected
        verify(mMockAdSelectionService)
                .reportInteraction(mCaptorReportInteractionInput.capture(), any());
        ReportInteractionInput input = mCaptorReportInteractionInput.getValue();
        expect.that(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        expect.that(input.getInteractionData()).isEqualTo(mEventData);
        expect.that(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        expect.that(input.getInputEvent()).isNull();
        expect.that(input.getAdId()).isEqualTo(AD_ID);
        expect.that(input.getCallerSdkName()).isEmpty();
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdZeroOut() throws Exception {
        // Set expected outcome of AdIdManager#getAdId
        mockGetAdId(new AdId(AdId.ZERO_OUT, true));

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new OutcomeReceiverForTests<>());

        // Assert values passed to the service are as expected
        verify(mMockAdSelectionService)
                .reportInteraction(mCaptorReportInteractionInput.capture(), any());
        ReportInteractionInput input = mCaptorReportInteractionInput.getValue();
        expect.that(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        expect.that(input.getInteractionData()).isEqualTo(mEventData);
        expect.that(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        expect.that(input.getInputEvent()).isNull();
        expect.that(input.getAdId()).isNull();
        expect.that(input.getCallerSdkName()).isEmpty();
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdDisabled() throws Exception {
        // Set expected outcome of AdIdManager#getAdId
        mockGetAdId(new SecurityException());

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new OutcomeReceiverForTests<>());

        // Assert values passed to the service are as expected
        verify(mMockAdSelectionService)
                .reportInteraction(mCaptorReportInteractionInput.capture(), any());
        ReportInteractionInput input = mCaptorReportInteractionInput.getValue();
        expect.that(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        expect.that(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        expect.that(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        expect.that(input.getInteractionData()).isEqualTo(mEventData);
        expect.that(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        expect.that(input.getInputEvent()).isNull();
        expect.that(input.getAdId()).isNull();
        expect.that(input.getCallerSdkName()).isEmpty();
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataWhenResultIsByteArray() throws Exception {
        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockGetAdSelectionData(
                getAdSelectionDataResponseWithByteArray(expectedAdSelectionId, expectedByteArray));
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();
        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        var result = outcomeReceiver.assertResultReceived();
        assertThat(result).isNotNull();
        expect.that(result.getAdSelectionId()).isEqualTo(expectedAdSelectionId);
        assertArrayEquals(expectedByteArray, outcomeReceiver.getResult().getAdSelectionData());
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataCoordinatorWasPassed() throws Exception {
        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        AtomicReference<GetAdSelectionDataInput> inputRef =
                mockGetAdSelectionData(
                        getAdSelectionDataResponseWithByteArray(
                                expectedAdSelectionId, expectedByteArray));

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCoordinatorOriginUri(Uri.parse("https://example.com"))
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        assertThat(outcomeReceiver.assertResultReceived()).isNotNull();
        GetAdSelectionDataInput input = inputRef.get();
        assertThat(input).isNotNull();
        expect.that(wasCoordinatorSet(input)).isTrue();
        expect.that(input.getSellerConfiguration()).isNull();
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionSellerConfigurationWasPassed()
            throws Exception {
        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        AtomicReference<GetAdSelectionDataInput> inputRef =
                mockGetAdSelectionData(
                        getAdSelectionDataResponseWithByteArray(
                                expectedAdSelectionId, expectedByteArray));

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setSellerConfiguration(SellerConfigurationFixture.SELLER_CONFIGURATION)
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        assertThat(outcomeReceiver.assertResultReceived()).isNotNull();
        GetAdSelectionDataInput input = inputRef.get();
        assertThat(input).isNotNull();
        expect.that(wasCoordinatorSet(input)).isFalse();
        expect.that(input.getSellerConfiguration())
                .isEqualTo(SellerConfigurationFixture.SELLER_CONFIGURATION);
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataWhenResultIsAssetFileDescriptor()
            throws Exception {
        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockGetAdSelectionData(
                getAdSelectionDataResponseWithAssetFileDescriptor(
                        expectedAdSelectionId, expectedByteArray, BLOCKING_EXECUTOR));

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        var result = outcomeReceiver.assertResultReceived();
        assertThat(result).isNotNull();
        assertThat(result.getAdSelectionId()).isEqualTo(expectedAdSelectionId);
        assertArrayEquals(expectedByteArray, outcomeReceiver.getResult().getAdSelectionData());
    }

    @Test
    public void
            testAdSelectionManagerGetAdSelectionDataWhenResultIsAssetFileDescriptorWithExcessiveSize()
                    throws Exception {
        byte[] expectedByteArray = getRandomByteArray(EXCESSIVE_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockGetAdSelectionData(
                getAdSelectionDataResponseWithAssetFileDescriptor(
                        expectedAdSelectionId, expectedByteArray, BLOCKING_EXECUTOR));

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();
        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        var result = outcomeReceiver.assertResultReceived();
        assertThat(result).isNotNull();
        assertThat(result.getAdSelectionId()).isEqualTo(expectedAdSelectionId);

        byte[] adSelectionData = result.getAdSelectionData();
        assertThat(adSelectionData).hasLength(expectedByteArray.length);
        assertArrayEquals(expectedByteArray, adSelectionData);
    }

    private static byte[] getRandomByteArray(int size) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] result = new byte[size];
        secureRandom.nextBytes(result);
        return result;
    }

    private void mockGetAdId(AdId adId) {
        doAnswer(
                        inv -> {
                            mLog.d("answering %s", inv);
                            @SuppressWarnings("unchecked")
                            AdServicesOutcomeReceiver<AdId, Exception> callback =
                                    (AdServicesOutcomeReceiver<AdId, Exception>) inv.getArgument(1);
                            callback.onResult(adId);
                            return null;
                        })
                .when(mMockAdIdManager)
                .getAdId(any(), any(AdServicesOutcomeReceiver.class));
    }

    private void mockGetAdId(Exception error) {
        doAnswer(
                        inv -> {
                            mLog.d("answering %s", inv);
                            @SuppressWarnings("unchecked")
                            AdServicesOutcomeReceiver<AdId, Exception> callback =
                                    (AdServicesOutcomeReceiver<AdId, Exception>) inv.getArgument(1);
                            callback.onError(error);
                            return null;
                        })
                .when(mMockAdIdManager)
                .getAdId(any(), any(AdServicesOutcomeReceiver.class));
    }

    private AtomicReference<GetAdSelectionDataInput> mockGetAdSelectionData(
            GetAdSelectionDataResponse response) throws Exception {
        AtomicReference<GetAdSelectionDataInput> input = new AtomicReference<>();
        doAnswer(
                        inv -> {
                            mLog.d("answering %s", inv);
                            input.set((GetAdSelectionDataInput) inv.getArgument(0));
                            GetAdSelectionDataCallback callback =
                                    (GetAdSelectionDataCallback) inv.getArgument(2);
                            callback.onSuccess(response);
                            return null;
                        })
                .when(mMockAdSelectionService)
                .getAdSelectionData(any(), any(), any());
        return input;
    }

    private boolean wasCoordinatorSet(GetAdSelectionDataInput input) {
        return input.getCoordinatorOriginUri() != null
                && !Strings.isNullOrEmpty(input.getCoordinatorOriginUri().toString());
    }
}
