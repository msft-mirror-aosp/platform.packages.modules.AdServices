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


import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;
import static com.android.adservices.service.adselection.AdSelectionServiceImpl.AUCTION_SERVER_API_IS_NOT_AVAILABLE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.ObliviousHttpEncryptorWithSeedImpl;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.UpdateAdCounterHistogramCallback;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAudienceInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AuctionServerE2ETest {
    private static final int COUNTDOWN_LATCH_LIMIT_SECONDS = 10;
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final List<DBAdData> ADS_BUYER_1 =
            DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1);
    private static final Uri AD_RENDER_URI = ADS_BUYER_1.get(0).getRenderUri();
    private static final String BUYER_REPORTING_URI =
            CommonFixture.getUri(BUYER_1, "/reporting").toString();
    private static final String SELLER_REPORTING_URI =
            CommonFixture.getUri(SELLER, "/reporting").toString();
    private static final WinReportingUrls WIN_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder().setReportingUrl(BUYER_REPORTING_URI).build())
                    .setComponentSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .build())
                    .build();
    private static final String CUSTOM_AUDIENCE_NAME = "test-name";
    private static final String CUSTOM_AUDIENCE_OWNER = "test-owner";
    private static final float BID = 5;
    private static final float SCORE = 5;
    private static final AuctionResult AUCTION_RESULT =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(AD_RENDER_URI.toString())
                    .setCustomAudienceName(CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(BUYER_1.toString())
                    .setBid(BID)
                    .setScore(SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS)
                    .build();
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;
    private AdServicesLogger mAdServicesLoggerMock;
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilterMock;
    @Mock public AppImportanceFilter mAppImportanceFilterMock;
    private Context mContext;
    private Flags mFlags;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    private MockitoSession mStaticMockSession = null;
    @Mock private ConsentManager mConsentManagerMock;
    private CustomAudienceDao mCustomAudienceDaoSpy;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDaoSpy;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EncryptionContextDao mEncryptionContextDao;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    private AdSelectionService mAdSelectionService;
    private AuctionServerPayloadFormatter mPayloadFormatter;
    private AuctionServerPayloadExtractor mPayloadExtractor;
    private AuctionServerDataCompressor mDataCompressor;

    @Mock AdSelectionEncryptionKeyManager mAdSelectionEncryptionKeyManagerMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mContext = ApplicationProvider.getApplicationContext();
        mFlags = new AuctionServerE2ETestFlags();
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(JSScriptEngine.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .mockStatic(ConsentManager.class)
                        .mockStatic(AppImportanceFilter.class)
                        .mockStatic(FlagsFactory.class)
                        .startMocking();
        mAdServicesLoggerMock = ExtendedMockito.mock(AdServicesLoggerImpl.class);
        mCustomAudienceDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                                .addTypeConverter(new DBCustomAudience.Converters(true, true))
                                .build()
                                .customAudienceDao());
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class).build();

        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDaoSpy = spy(sharedDb.frequencyCapDao());
        AdSelectionServerDatabase serverDb =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionServerDatabase.class).build();
        mEncryptionContextDao = serverDb.encryptionContextDao();
        mEncryptionKeyDao = serverDb.encryptionKeyDao();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        when(ConsentManager.getInstance(mContext)).thenReturn(mConsentManagerMock);
        when(AppImportanceFilter.create(any(), anyInt(), any()))
                .thenReturn(mAppImportanceFilterMock);
        doNothing()
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());

        mAdSelectionService = createAdSelectionService();

        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        mFlags.getFledgeAuctionServerPayloadFormatVersion(),
                        mFlags.getFledgeAuctionServerPayloadBucketSizes());
        mPayloadExtractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        mFlags.getFledgeAuctionServerPayloadFormatVersion());

        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFlags.getFledgeAuctionServerCompressionAlgorithmVersion());

        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
        reset(mAdServicesHttpsClientMock);
    }

    @Test
    public void testAuctionServer_killSwitchDisabled_throwsException() throws RemoteException {
        mFlags = new AuctionServerE2ETestFlags(true);
        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        ThrowingRunnable getAdSelectionDataRunnable =
                () -> invokeGetAdSelectionData(mAdSelectionService, getAdSelectionDataInput);

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(123456L)
                        .setSeller(SELLER)
                        .setAdSelectionResult(new byte[42])
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ThrowingRunnable persistAdSelectionResultRunnable =
                () ->
                        invokePersistAdSelectionResult(
                                mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertThrows(
                AUCTION_SERVER_API_IS_NOT_AVAILABLE,
                IllegalStateException.class,
                getAdSelectionDataRunnable);
        Assert.assertThrows(
                AUCTION_SERVER_API_IS_NOT_AVAILABLE,
                IllegalStateException.class,
                persistAdSelectionResultRunnable);
    }

    @Test
    public void testGetAdSelectionData_withoutEncrypt_validRequest_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiences(nameAndBuyersMap);

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mAdSelectionService, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());

        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);
        Assert.assertEquals(buyers, buyerInputMap.keySet());
        for (AdTechIdentifier buyer : buyerInputMap.keySet()) {
            BuyerInput buyerInput = buyerInputMap.get(buyer);
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                Assert.assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEquals(buyerInputsCA, deviceCA);
            }
        }
    }

    @Test
    public void testGetAdSelectionData_withEncrypt_validRequest_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", BUYER_1,
                        "Shirts CA of Buyer 1", BUYER_1,
                        "Shoes CA Of Buyer 2", BUYER_2);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        DBEncryptionKey dbEncryptionKey =
                DBEncryptionKey.builder()
                        .setPublicKey("bSHP4J++pRIvnrwusqafzE8GQIzVSqyTTwEudvzc72I=")
                        .setKeyIdentifier("050bed24-c62f-46e0-a1ad-211361ad771a")
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(TimeUnit.DAYS.toSeconds(7))
                        .build();
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(dbEncryptionKey));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        AdSelectionService service =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionContextDao,
                        mEncryptionKeyDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        new ObliviousHttpEncryptorWithSeedImpl(
                                new AdSelectionEncryptionKeyManager(
                                        mEncryptionKeyDao,
                                        mFlags,
                                        mAdServicesHttpsClientMock,
                                        mLightweightExecutorService),
                                mEncryptionContextDao,
                                seedBytes,
                                mLightweightExecutorService),
                        mAdSelectionDebugReportDao);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        long adSelectionId = callback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_validRequest_success()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                AD_RENDER_URI,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        ReportingData reportingData = mAdSelectionEntryDao.getReportingDataForId(adSelectionId);
        Assert.assertEquals(
                BUYER_REPORTING_URI, reportingData.getBuyerWinReportingUri().toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI, reportingData.getSellerWinReportingUri().toString());
    }

    @Test
    public void testPersistAdSelectionResult_withDecrypt_validRequest_successEmptyUri()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        DBEncryptionKey dbEncryptionKey =
                DBEncryptionKey.builder()
                        .setPublicKey("bSHP4J++pRIvnrwusqafzE8GQIzVSqyTTwEudvzc72I=")
                        .setKeyIdentifier("050bed24-c62f-46e0-a1ad-211361ad771a")
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(TimeUnit.DAYS.toSeconds(7))
                        .build();
        mEncryptionKeyDao.insertAllKeys(ImmutableList.of(dbEncryptionKey));
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);

        AdSelectionService service =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionContextDao,
                        mEncryptionKeyDao,
                        mAdServicesHttpsClientMock,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        new ObliviousHttpEncryptorWithSeedImpl(
                                new AdSelectionEncryptionKeyManager(
                                        mEncryptionKeyDao,
                                        mFlags,
                                        mAdServicesHttpsClientMock,
                                        mLightweightExecutorService),
                                mEncryptionContextDao,
                                seedBytes,
                                mLightweightExecutorService),
                        mAdSelectionDebugReportDao);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(service, input);

        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionData();

        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));

        String cipherText =
                "Lu9TKo4rvstPJt98F1IrLiVUeczFzKuBEJ8jFe1BNXfNImu/lQR0CB8/B1Kur0n1Fxcz"
                        + "ZQs28dZO2b3jwOaKk5qJgIlcY8Zd1n0Tb/M9vQXcs+d2QbeykmoffEb9kf76zebKDd1"
                        + "Slb0psgEFtATuqaxaPd9ErumVWXdvD9QuvB6p+URWN+uIv2VhFwmjtf+QE/HZBD6EE+"
                        + "Ft8ipPiNkNysa7TyL3FLgXO3HGZ2FlQX4GvE5R3br3hPkceY+cplv7ZZDSmc/vfO+7N"
                        + "4S1XkZ/y0KYuQHXF24ejJ4xmwrJ5L22V3LhTm5euppXerNtUkIqaaYRE3lQ+Glh1rph"
                        + "dFYZqyoXLhFp6ABzk72lnjMzqdL2hYAVc7agowS29jz6Wo6Tw/pglfls8l1yLntocNE"
                        + "hEUUvCDl+MQJqrY9gwmbEzrvhwgfl3MbEcShXib3qny+b8/cGEJdQ8sDft1xglbe0a1"
                        + "rGHZbNgLiprEtVYKyD4dGMcNT7L/RqmygoLRgYzmCBBD7dLgEdYMpRrYh5kmopx4lZJ"
                        + "6HkltqP0f+OzDLzgA7JCiPsCgiZG7Sx4iRR8p2iwfhKBVZPX1fPORdkRhzjIbhdWxCA"
                        + "2+GuafjfdY5FBX2F719z0SbkJeaxxrrjKMmpXLzgVT12vVMsDbuFDFhi4i4buI3gMns"
                        + "g0r4+eeQ+KX1UOMaM6OsGkdt5/aTSsBYTTv8Ikp2ufUEFDnAK4nuoTJlp+gEN3l0K07"
                        + "/U3b7R4TI=";

        byte[] responseBytes = BaseEncoding.base64().decode(cipherText);

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionResult(responseBytes)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(service, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                Uri.EMPTY,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
    }

    @Test
    public void testReportImpression_serverAuction_reportsSellerAndBuyerUri() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(2);
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientMock)
                .getAndReadNothing(any(Uri.class), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionIdFromGetAdSelectionData =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionIdFromGetAdSelectionData)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        long adSelectionIdFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId();
        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionIdFromGetAdSelectionData, adSelectionIdFromPersistAdSelectionResult);
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionIdFromPersistAdSelectionResult)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionIdFromPersistAdSelectionResult)
                        .getSellerWinReportingUri()
                        .toString());

        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(
                                persistAdSelectionResultTestCallback
                                        .mPersistAdSelectionResultResponse.getAdSelectionId())
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);
        Assert.assertTrue(callback.mIsSuccess);
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());
    }

    @Test
    public void testReportImpression_serverAuction_sellerReportingFailure_noExceptionThrown()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(2);
        Answer<ListenableFuture<Void>> failedReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFailedFuture(
                            new IllegalStateException("Exception for test!"));
                };
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientMock)
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any(DevContext.class));
        doAnswer(failedReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientMock)
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getSellerWinReportingUri()
                        .toString());

        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);
        Assert.assertTrue(callback.mIsSuccess);
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());
    }

    @Test
    public void testReportImpression_serverAuction_buyerReportingFailure_noExceptionThrown()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(2);
        Answer<ListenableFuture<Void>> failedReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFailedFuture(
                            new IllegalStateException("Exception for test!"));
                };
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientMock)
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any(DevContext.class));
        doAnswer(failedReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientMock)
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getSellerWinReportingUri()
                        .toString());

        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);
        Assert.assertTrue(callback.mIsSuccess);
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientMock, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_savesWinEventsSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        mAdSelectionService = createAdSelectionService();

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        // Assert fcap win reporting
        ArgumentCaptor<HistogramEvent> histogramEventArgumentCaptor =
                ArgumentCaptor.forClass(HistogramEvent.class);
        verify(mFrequencyCapDaoSpy, times(AdDataFixture.getAdCounterKeys().size()))
                .insertHistogramEvent(
                        histogramEventArgumentCaptor.capture(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt());
        List<HistogramEvent> capturedHistogramEventList =
                histogramEventArgumentCaptor.getAllValues();
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                capturedHistogramEventList.get(0).getAdEventType());
        Assert.assertEquals(
                AdDataFixture.getAdCounterKeys(),
                capturedHistogramEventList.stream()
                        .map(HistogramEvent::getAdCounterKey)
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_savesNonWinEventsSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        mAdSelectionService = createAdSelectionService();

        when(mObliviousHttpEncryptorMock.encryptBytes(any(byte[].class), anyLong(), anyLong()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                BUYER_1, CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER)
                        .setAds(DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(BUYER_1))
                        .build(),
                Uri.EMPTY);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        // Assert fcap non-win reporting
        UpdateAdCounterHistogramInput updateHistogramInput =
                new UpdateAdCounterHistogramInput.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                SELLER,
                                CALLER_PACKAGE_NAME)
                        .build();
        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                invokeUpdateAdCounterHistogram(mAdSelectionService, updateHistogramInput);

        int numOfKeys = AdDataFixture.getAdCounterKeys().size();
        ArgumentCaptor<HistogramEvent> histogramEventArgumentCaptor =
                ArgumentCaptor.forClass(HistogramEvent.class);
        Assert.assertTrue(updateHistogramCallback.mIsSuccess);
        verify(
                        mFrequencyCapDaoSpy,
                        // Each key is reported twice; WIN and VIEW events
                        times(2 * numOfKeys))
                .insertHistogramEvent(
                        histogramEventArgumentCaptor.capture(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt());
        List<HistogramEvent> capturedHistogramEventList =
                histogramEventArgumentCaptor.getAllValues();
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                capturedHistogramEventList.get(0).getAdEventType());
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                capturedHistogramEventList.get(numOfKeys).getAdEventType());
        Assert.assertEquals(
                AdDataFixture.getAdCounterKeys(),
                capturedHistogramEventList.subList(numOfKeys, 2 * numOfKeys).stream()
                        .map(HistogramEvent::getAdCounterKey)
                        .collect(Collectors.toSet()));
    }

    /**
     * Asserts if a {@link BuyerInput.CustomAudience} and {@link DBCustomAudience} objects are
     * equal.
     */
    private void assertEquals(
            BuyerInput.CustomAudience buyerInputCA, DBCustomAudience dbCustomAudience) {
        Assert.assertEquals(buyerInputCA.getName(), dbCustomAudience.getName());
        Assert.assertNotNull(dbCustomAudience.getTrustedBiddingData());
        Assert.assertEquals(
                buyerInputCA.getBiddingSignalsKeysList(),
                dbCustomAudience.getTrustedBiddingData().getKeys());
        Assert.assertNotNull(dbCustomAudience.getUserBiddingSignals());
        Assert.assertEquals(
                buyerInputCA.getUserBiddingSignals(),
                dbCustomAudience.getUserBiddingSignals().toString());
    }

    private AdSelectionService createAdSelectionService() {
        return new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mAppInstallDao,
                mCustomAudienceDaoSpy,
                mFrequencyCapDaoSpy,
                mEncryptionContextDao,
                mEncryptionKeyDao,
                mAdServicesHttpsClientMock,
                mDevContextFilterMock,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mScheduledExecutor,
                mContext,
                mAdServicesLoggerMock,
                mFlags,
                CallingAppUidSupplierProcessImpl.create(),
                mFledgeAuthorizationFilterMock,
                mAdSelectionServiceFilterMock,
                mAdFilteringFeatureFactory,
                mConsentManagerMock,
                mObliviousHttpEncryptorMock,
                mAdSelectionDebugReportDao);
    }

    private Map<AdTechIdentifier, BuyerInput> getBuyerInputMapFromDecryptedBytes(
            byte[] decryptedBytes) {
        try {
            byte[] unformatted =
                    mPayloadExtractor
                            .extract(AuctionServerPayloadFormattedData.create(decryptedBytes))
                            .getData();
            ProtectedAudienceInput protectedAudienceInput =
                    ProtectedAudienceInput.parseFrom(unformatted);
            Map<String, ByteString> buyerInputBytesMap = protectedAudienceInput.getBuyerInputMap();
            Function<Map.Entry<String, ByteString>, AdTechIdentifier> entryToAdTechIdentifier =
                    entry -> AdTechIdentifier.fromString(entry.getKey());
            Function<Map.Entry<String, ByteString>, BuyerInput> entryToBuyerInput =
                    entry -> {
                        try {
                            byte[] compressedBytes = entry.getValue().toByteArray();
                            byte[] decompressedBytes =
                                    mDataCompressor
                                            .decompress(
                                                    AuctionServerDataCompressor.CompressedData
                                                            .create(compressedBytes))
                                            .getData();
                            return BuyerInput.parseFrom(decompressedBytes);
                        } catch (InvalidProtocolBufferException e) {
                            throw new UncheckedIOException(e);
                        }
                    };
            return buyerInputBytesMap.entrySet().stream()
                    .collect(Collectors.toMap(entryToAdTechIdentifier, entryToBuyerInput));
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, DBCustomAudience> createAndPersistDBCustomAudiences(
            Map<String, AdTechIdentifier> nameAndBuyers) {
        Map<String, DBCustomAudience> customAudiences = new HashMap<>();
        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(buyer, name)
                            .build();
            customAudiences.put(name, thisCustomAudience);
            mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(thisCustomAudience, Uri.EMPTY);
        }
        return customAudiences;
    }

    private byte[] prepareAuctionResultBytes() {
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

    public GetAdSelectionDataTestCallback invokeGetAdSelectionData(
            AdSelectionService service, GetAdSelectionDataInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        GetAdSelectionDataTestCallback callback =
                new GetAdSelectionDataTestCallback(countDownLatch);
        service.getAdSelectionData(input, null, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public PersistAdSelectionResultTestCallback invokePersistAdSelectionResult(
            AdSelectionService service, PersistAdSelectionResultInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultTestCallback callback =
                new PersistAdSelectionResultTestCallback(countDownLatch);
        service.persistAdSelectionResult(input, null, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public UpdateAdCounterHistogramTestCallback invokeUpdateAdCounterHistogram(
            AdSelectionService service, UpdateAdCounterHistogramInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(countDownLatch);
        service.updateAdCounterHistogram(input, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public ReportImpressionTestCallback invokeReportImpression(
            AdSelectionService service, ReportImpressionInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(countDownLatch);
        service.reportImpression(input, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    static class GetAdSelectionDataTestCallback extends GetAdSelectionDataCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        GetAdSelectionDataResponse mGetAdSelectionDataResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        GetAdSelectionDataTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mGetAdSelectionDataResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(GetAdSelectionDataResponse getAdSelectionDataResponse)
                throws RemoteException {
            mIsSuccess = true;
            mGetAdSelectionDataResponse = getAdSelectionDataResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
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

    static class UpdateAdCounterHistogramTestCallback
            extends UpdateAdCounterHistogramCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        UpdateAdCounterHistogramTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportImpressionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class AuctionServerE2ETestFlags implements Flags {
        private final boolean mFledgeAuctionServerKillSwitch;

        AuctionServerE2ETestFlags() {
            this(false);
        }

        AuctionServerE2ETestFlags(boolean fledgeAuctionServerKillSwitch) {
            mFledgeAuctionServerKillSwitch = fledgeAuctionServerKillSwitch;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return mFledgeAuctionServerKillSwitch;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForReportImpression() {
            return true;
        }
    }
}
