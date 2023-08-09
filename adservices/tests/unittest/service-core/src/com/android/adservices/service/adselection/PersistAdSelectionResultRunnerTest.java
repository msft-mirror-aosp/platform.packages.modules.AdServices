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

package com.android.adservices.service.adselection;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AuctionServerAdSelectionDao;
import com.android.adservices.data.adselection.DBAuctionServerAdSelection;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

public class PersistAdSelectionResultRunnerTest {
    private static final int CALLER_UID = Process.myUid();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final Uri AD_RENDER_URI = Uri.parse("test.com/render_uri");
    private static final AdTechIdentifier WINNER_BUYER =
            AdTechIdentifier.fromString("winner-buyer.com");
    private static final String BUYER_REPORTING_URI = "https://foobarbuyer.reporting";
    private static final String SELLER_REPORTING_URI = "https://foobarseller.reporting";
    private static final WinReportingUrls WIN_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder().setReportingUrl(BUYER_REPORTING_URI).build())
                    .setComponentSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .build())
                    .build();
    private static final AuctionResult AUCTION_RESULT =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(AD_RENDER_URI.toString())
                    .setCustomAudienceName("test-name")
                    .setBuyer(WINNER_BUYER.toString())
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS)
                    .build();
    private static final AuctionResult CHAFF_AUCTION_RESULT =
            AuctionResult.newBuilder().setIsChaff(true).build();
    private static final byte[] CIPHER_TEXT_BYTES =
            "encrypted-cipher-for-auction-result".getBytes();
    private static final long AD_SELECTION_ID = 12345L;
    private Context mContext;
    private Flags mFlags;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    @Spy private AuctionServerAdSelectionDao mServerAdSelectionDaoSpy;
    private AuctionServerPayloadFormatter mPayloadFormatter;
    private AuctionServerDataCompressor mDataCompressor;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    private PersistAdSelectionResultRunner mPersistAdSelectionResultRunner;
    private MockitoSession mStaticMockSession = null;

    @Before
    public void setup() throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException {
        mFlags = new ProcessAdSelectionResultRunnerTestFlags();
        mContext = ApplicationProvider.getApplicationContext();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mServerAdSelectionDaoSpy =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionServerDatabase.class)
                        .build()
                        .auctionServerAdSelectionDao();
        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        AuctionServerPayloadFormatterV0.VERSION,
                        mFlags.getFledgeAuctionServerPayloadBucketSizes());
        mDataCompressor = new AuctionServerDataCompressorGzip();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(PackageManagerCompatUtils.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        MockitoAnnotations.initMocks(this); // init @Mock mocks

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mServerAdSelectionDaoSpy,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled());
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testRunner_persistAdSelectionResult_returnsSuccess() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedAuctionResult())
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setPersistAdSelectionResultRequest(
                                new PersistAdSelectionResultRequest.Builder()
                                        .setSeller(SELLER)
                                        .setAdSelectionId(AD_SELECTION_ID)
                                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                                        .build())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mServerAdSelectionDaoSpy, times(1))
                .updateAuctionServerAdSelection(
                        DBAuctionServerAdSelection.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setSeller(SELLER)
                                .setWinnerBuyer(WINNER_BUYER)
                                .setWinnerAdRenderUri(AD_RENDER_URI)
                                .setBuyerReportingUri(Uri.parse(BUYER_REPORTING_URI))
                                .setSellerReportingUri(Uri.parse(SELLER_REPORTING_URI))
                                .build());
    }

    @Test
    public void testRunner_persistAdSelectionResultChaff_noResultPersisted() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        doReturn(prepareDecryptedChaffAuctionResult())
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setPersistAdSelectionResultRequest(
                                new PersistAdSelectionResultRequest.Builder()
                                        .setSeller(SELLER)
                                        .setAdSelectionId(AD_SELECTION_ID)
                                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                                        .build())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verifyZeroInteractions(mServerAdSelectionDaoSpy);
    }

    @Test
    public void testRunner_revokedUserConsent_returnsEmptyResult() throws InterruptedException {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setPersistAdSelectionResultRequest(
                                new PersistAdSelectionResultRequest.Builder()
                                        .setSeller(SELLER)
                                        .setAdSelectionId(AD_SELECTION_ID)
                                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                                        .build())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNull(callback.mPersistAdSelectionResultResponse);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
    }

    private byte[] prepareDecryptedAuctionResult() {
        byte[] auctionResultBytes = AUCTION_RESULT.toByteArray();
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(auctionResultBytes));
        AuctionServerPayloadFormattedData formattedData =
                mPayloadFormatter.apply(
                        AuctionServerPayloadUnformattedData.create(compressedData.getData()),
                        AuctionServerDataCompressorGzip.VERSION);
        return formattedData.getData();
    }

    private byte[] prepareDecryptedChaffAuctionResult() {
        byte[] auctionResultBytes = CHAFF_AUCTION_RESULT.toByteArray();
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(auctionResultBytes));
        AuctionServerPayloadFormattedData formattedData =
                mPayloadFormatter.apply(
                        AuctionServerPayloadUnformattedData.create(compressedData.getData()),
                        AuctionServerDataCompressorGzip.VERSION);
        return formattedData.getData();
    }

    private PersistAdSelectionResultTestCallback invokePersistAdSelectionResult(
            PersistAdSelectionResultRunner runner, PersistAdSelectionResultInput inputParams)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        PersistAdSelectionResultTestCallback callback =
                new PersistAdSelectionResultTestCallback(countdownLatch);

        runner.run(inputParams, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public static class ProcessAdSelectionResultRunnerTestFlags implements Flags {
        @Override
        public long getFledgeCustomAudienceActiveTimeWindowInMs() {
            return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
        }
    }

    static class PersistAdSelectionResultTestCallback
            extends PersistAdSelectionResultCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        PersistAdSelectionResultResponse mPersistAdSelectionResultResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        PersistAdSelectionResultTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mPersistAdSelectionResultResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(PersistAdSelectionResultResponse persistAdSelectionResultResponse)
                throws RemoteException {
            mIsSuccess = true;
            mPersistAdSelectionResultResponse = persistAdSelectionResultResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }
}
