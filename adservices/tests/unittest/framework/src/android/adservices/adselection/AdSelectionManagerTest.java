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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.shared.common.exception.AdServicesDeprecationConstants.AD_SELECTION_SERVICE_DEPRECATION_MESSAGE;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import android.adservices.adid.AdIdManager;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.net.Uri;
import android.os.Build;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.testing.OutcomeReceiverForTests;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.RequiresSdkRange;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Unit tests for {@link AdSelectionManager} */
public final class AdSelectionManagerTest extends AdServicesMockitoTestCase {
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final long AD_SELECTION_ID = 1234L;
    private static final String EVENT_KEY = "click";
    private static final int REPORTING_DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;
    private static final int TYPICAL_PAYLOAD_SIZE_BYTES = 1024; // 1kb

    private final String mEventData;
    private final ReportEventRequest mReportEventRequest;

    @Mock private AdSelectionService mMockAdSelectionService;

    @Mock private AdIdManager mMockAdIdManager;
    @Mock private FledgeErrorResponse mMockFledgeErrorResponse;
    @Mock private GetAdSelectionDataResponse mMockGetAdSelectionDataResponse;
    @Mock private PersistAdSelectionResultResponse mMockPersistAdSelectionResultResponse;
    @Mock private AdSelectionResponse mMockAdSelectionResponse;

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
    public void testAdSelectionManager_getAdSelectionData_returnsApiDeprecatedWhenCalledOnFailure()
            throws Exception {
        mockGetAdSelectionDataDeprecated(true);
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCoordinatorOriginUri(Uri.parse("https://example.com"))
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_getAdSelectionData_returnsApiDeprecatedWhenCalledOnSuccess()
            throws Exception {
        mockGetAdSelectionDataDeprecated(false);
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCoordinatorOriginUri(Uri.parse("https://example.com"))
                        .build();

        OutcomeReceiverForTests<GetAdSelectionDataOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_persistAdSelectionResult_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockPersistAdSelectionResultDeprecated(true);
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setAdSelectionId(123L)
                        .build();
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.persistAdSelectionResult(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_persistAdSelectionResult_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockPersistAdSelectionResultDeprecated(false);
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setAdSelectionId(123L)
                        .build();
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.persistAdSelectionResult(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_selectAds_returnsApiDeprecatedOnFailure() throws Exception {
        mockSelectAdsDeprecated(true);
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.selectAds(
                AdSelectionConfigFixture.anAdSelectionConfig(), CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_selectAds_returnsApiDeprecatedOnSuccess() throws Exception {
        mockSelectAdsDeprecated(false);
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.selectAds(
                AdSelectionConfigFixture.anAdSelectionConfig(), CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_selectAdsFromOutcomes_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockSelectAdsFromOutcomesDeprecated(true);
        AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setAdSelectionIds(ImmutableList.of())
                        .setSelectionSignals(AdSelectionConfigFixture.AD_SELECTION_SIGNALS)
                        .setSelectionLogicUri(Uri.parse("https://example.com/logic.js"))
                        .build();
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.selectAds(
                adSelectionFromOutcomesConfig, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_selectAdsFromOutcomes_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockSelectAdsFromOutcomesDeprecated(false);
        AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setAdSelectionIds(ImmutableList.of())
                        .setSelectionSignals(AdSelectionConfigFixture.AD_SELECTION_SIGNALS)
                        .setSelectionLogicUri(Uri.parse("https://example.com/logic.js"))
                        .build();
        OutcomeReceiverForTests<AdSelectionOutcome> outcomeReceiver =
                new OutcomeReceiverForTests<>();

        mAdSelectionManager.selectAds(
                adSelectionFromOutcomesConfig, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportImpression_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockReportImpressionDeprecated(true);
        ReportImpressionRequest request = new ReportImpressionRequest(AD_SELECTION_ID);
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.reportImpression(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportImpression_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockReportImpressionDeprecated(false);
        ReportImpressionRequest request = new ReportImpressionRequest(AD_SELECTION_ID);
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.reportImpression(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportEvent_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockReportInteractionDeprecated(true); // reportEvent calls service.reportInteraction
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID,
                                "click",
                                new JSONObject().put("key", "value").toString(),
                                FLAG_REPORTING_DESTINATION_BUYER)
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.reportEvent(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportEvent_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockReportInteractionDeprecated(false); // reportEvent calls service.reportInteraction
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID,
                                "click",
                                new JSONObject().put("key", "value").toString(),
                                FLAG_REPORTING_DESTINATION_BUYER)
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.reportEvent(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_setAppInstallAdvertisers_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockSetAppInstallAdvertisersDeprecated(true);
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest.Builder()
                        .setAdvertisers(ImmutableSet.of())
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.setAppInstallAdvertisers(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_setAppInstallAdvertisers_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockSetAppInstallAdvertisersDeprecated(false);
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest.Builder()
                        .setAdvertisers(ImmutableSet.of())
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.setAppInstallAdvertisers(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_updateAdCounterHistogram_returnsApiDeprecatedOnFailure()
            throws Exception {
        mockUpdateAdCounterHistogramDeprecated(true);
        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.updateAdCounterHistogram(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_updateAdCounterHistogram_returnsApiDeprecatedOnSuccess()
            throws Exception {
        mockUpdateAdCounterHistogramDeprecated(false);
        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1)
                        .build();
        OutcomeReceiverForTests<Object> outcomeReceiver = new OutcomeReceiverForTests<>();

        mAdSelectionManager.updateAdCounterHistogram(request, CALLBACK_EXECUTOR, outcomeReceiver);

        Exception error = outcomeReceiver.assertFailureReceived();
        expect.that(error.getClass()).isEqualTo(IllegalStateException.class);
        expect.that(error.getMessage()).isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    private void mockGetAdSelectionDataDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            GetAdSelectionDataCallback callback =
                                    (GetAdSelectionDataCallback) inv.getArgument(2);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess(mMockGetAdSelectionDataResponse);
                            }
                            mLog.d("Api deprecation response sent.");
                            return null;
                        })
                .when(mMockAdSelectionService)
                .getAdSelectionData(any(), any(), any());
    }

    private void mockPersistAdSelectionResultDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            PersistAdSelectionResultCallback callback = inv.getArgument(2);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess(mMockPersistAdSelectionResultResponse);
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .persistAdSelectionResult(any(), any(), any());
    }

    private void mockSelectAdsDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            AdSelectionCallback callback = inv.getArgument(2);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess(mMockAdSelectionResponse);
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .selectAds(any(), any(), any());
    }

    private void mockSelectAdsFromOutcomesDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            AdSelectionCallback callback = inv.getArgument(2);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess(mMockAdSelectionResponse);
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .selectAdsFromOutcomes(any(), any(), any());
    }

    private void mockReportImpressionDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            ReportImpressionCallback callback = inv.getArgument(1);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess();
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .reportImpression(any(), any());
    }

    private void mockReportInteractionDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            ReportInteractionCallback callback = inv.getArgument(1);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess();
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .reportInteraction(any(), any());
    }

    private void mockSetAppInstallAdvertisersDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            SetAppInstallAdvertisersCallback callback = inv.getArgument(1);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess();
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .setAppInstallAdvertisers(any(), any());
    }

    private void mockUpdateAdCounterHistogramDeprecated(boolean callOnFailure) throws Exception {
        doAnswer(
                        inv -> {
                            UpdateAdCounterHistogramCallback callback = inv.getArgument(1);
                            if (callOnFailure) {
                                callback.onFailure(mMockFledgeErrorResponse);
                            } else {
                                callback.onSuccess();
                            }
                            return null;
                        })
                .when(mMockAdSelectionService)
                .updateAdCounterHistogram(any(), any());
    }
}
