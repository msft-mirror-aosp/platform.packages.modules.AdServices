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

import static android.adservices.adselection.DataHandlersFixture.getAdSelectionInitialization;
import static android.adservices.adselection.DataHandlersFixture.getAdSelectionResultBidAndUri;
import static android.adservices.adselection.DataHandlersFixture.getReportingData;
import static android.adservices.adselection.DataHandlersFixture.getWinningCustomAudience;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdTechIdentifier.UNSET_AD_TECH_IDENTIFIER;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.logging.ErrorLogUtilSyncCallback.mockErrorLogUtilWithoutThrowable;
import static com.android.adservices.service.Flags.FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_AUCTION_RESULT_HAS_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INTERACTION_URI_EXCEEDS_MAXIMUM_LIMIT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_AD_TECH_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_MISMATCH_INITIALIZATION_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_EMPTY_SUCCESS_SILENT_CONSENT_FAILURE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NULL_INITIALIZATION_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_PROCESSING_KANON_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_RESULT_IS_CHAFF;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_REVOKED_CONSENT_FILTER_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_TIMEOUT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.BINDER_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.PERSIST_AD_SELECTION_RESULT_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdsRelevanceExecutionLoggerImplFixture.sCallerMetadata;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.WINNER_TYPE_CA_WINNER;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.WINNER_TYPE_NO_WINNER;
import static com.android.adservices.service.stats.AdsRelevanceStatusUtils.WINNER_TYPE_PAS_WINNER;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.logging.ErrorLogUtilSyncCallback;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilCall;
import com.android.adservices.common.logging.annotations.ExpectErrorLogUtilWithExceptionCall;
import com.android.adservices.common.logging.annotations.SetErrorLogUtilDefaultParams;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.kanon.KAnonMessageEntity;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.kanon.KAnonSignJoinManager;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.AdsRelevanceExecutionLoggerFactory;
import com.android.adservices.service.stats.AdsRelevanceStatusUtils;
import com.android.adservices.service.stats.ApiCallStats;
import com.android.adservices.service.stats.DestinationRegisteredBeaconsReportedStats;
import com.android.adservices.service.stats.pas.PersistAdSelectionResultCalledStats;
import com.android.adservices.shared.testing.SkipLoggingUsageRule;
import com.android.adservices.shared.testing.concurrency.ResultSyncCallback;
import com.android.adservices.shared.util.Clock;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

@SpyStatic(FlagsFactory.class)
@SpyStatic(DebugFlags.class)
@SetErrorLogUtilDefaultParams(
        throwable = ExpectErrorLogUtilWithExceptionCall.Any.class,
        ppapiName = AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT)
public final class PersistAdSelectionResultRunnerTest extends AdServicesExtendedMockitoTestCase {
    private static final int CALLER_UID = Process.myUid();
    private static final String SHA256 = "SHA-256";
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String DIFFERENT_CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME_2;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier DIFFERENT_SELLER = AdSelectionConfigFixture.SELLER_1;
    private static final AdTechIdentifier COMPONENT_SELLER =
            AdSelectionConfigFixture.COMPONENT_SELLER_1;
    private static final Uri AD_RENDER_URI_1 = Uri.parse("test2.com/render_uri");
    private static final Uri AD_RENDER_URI_2 = Uri.parse("test3.com/render_uri");
    private static final AdTechIdentifier WINNER_BUYER =
            AdTechIdentifier.fromString("winner-buyer.com");
    private static final AdTechIdentifier DIFFERENT_BUYER =
            AdTechIdentifier.fromString("different-buyer.com");
    private static final Uri WINNER_AD_RENDER_URI =
            CommonFixture.getUri(WINNER_BUYER, "/render_uri");
    private static final String BUYER_REPORTING_URI =
            CommonFixture.getUri(WINNER_BUYER, "/reporting").toString();
    private static final String BUYER_REPORTING_URI_DIFFERENT_BUYER =
            CommonFixture.getUri(DIFFERENT_BUYER, "/reporting").toString();
    private static final String SELLER_REPORTING_URI =
            CommonFixture.getUri(SELLER, "/reporting").toString();
    private static final String SELLER_REPORTING_URI_DIFFERENT_SELLER =
            CommonFixture.getUri(DIFFERENT_SELLER, "/reporting").toString();
    private static final String BUYER_INTERACTION_KEY = "buyer-interaction-key";
    private static final String BUYER_INTERACTION_URI =
            CommonFixture.getUri(WINNER_BUYER, "/interaction").toString();
    private static final String BUYER_INTERACTION_URI_DIFFERENT_BUYER =
            CommonFixture.getUri(DIFFERENT_BUYER, "/interaction").toString();
    private static final String SELLER_INTERACTION_KEY = "seller-interaction-key";
    private static final String SELLER_INTERACTION_URI =
            CommonFixture.getUri(SELLER, "/interaction").toString();
    private static final String SELLER_INTERACTION_URI_DIFFERENT_SELLER =
            CommonFixture.getUri(DIFFERENT_SELLER, "/interaction").toString();
    private static final String BUYER_INTERACTION_URI_EXCEEDS_MAX =
            CommonFixture.getUri(WINNER_BUYER, "/interaction_uri_exceeds_max").toString();
    private static final String SELLER_INTERACTION_URI_EXCEEDS_MAX =
            CommonFixture.getUri(SELLER, "/interaction_uri_exceeds_max").toString();
    private static final String SELLER_INTERACTION_KEY_EXCEEDS_MAX =
            "seller-interaction-key-exceeds-max";
    private static final String COMPONENT_SELLER_REPORTING_URI =
            CommonFixture.getUri(COMPONENT_SELLER, "/reporting").toString();
    private static final String COMPONENT_SELLER_INTERACTION_KEY =
            "component-seller-interaction-key";
    private static final String COMPONENT_SELLER_INTERACTION_URI =
            CommonFixture.getUri(COMPONENT_SELLER, "/interaction").toString();
    private static final WinReportingUrls WIN_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_COMPONENT_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .setComponentSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(COMPONENT_SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            COMPONENT_SELLER_INTERACTION_KEY,
                                            COMPONENT_SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_DIFFERENT_SELLER_REPORTING_URIS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI_DIFFERENT_SELLER)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY,
                                            SELLER_INTERACTION_URI_DIFFERENT_SELLER)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_DIFFERENT_BUYER_REPORTING_URIS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI_DIFFERENT_BUYER)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY,
                                            BUYER_INTERACTION_URI_DIFFERENT_BUYER)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final WinReportingUrls WIN_REPORTING_URLS_WITH_INTERACTION_DATA_EXCEEDS_MAX =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY,
                                            BUYER_INTERACTION_URI_EXCEEDS_MAX)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY_EXCEEDS_MAX,
                                            SELLER_INTERACTION_URI_DIFFERENT_SELLER)
                                    .build())
                    .build();

    private static final WinReportingUrls WIN_REPORTING_URLS_INTERACTION_URIS_EXCEED_MAX =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY,
                                            BUYER_INTERACTION_URI_EXCEEDS_MAX)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY,
                                            SELLER_INTERACTION_URI_EXCEEDS_MAX)
                                    .build())
                    .build();
    private static final String WINNER_CUSTOM_AUDIENCE_NAME = "test-name-1";
    private static final String WINNER_CUSTOM_AUDIENCE_OWNER = "winner-owner";
    private static final String CUSTOM_AUDIENCE_OWNER_1 = "owner-1";
    private static final String CUSTOM_AUDIENCE_OWNER_2 = "owner-2";
    private static final double BID = 5;
    private static final double SCORE = 5;
    private static final AuctionResult.Builder AUCTION_RESULT =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS);
    private static final AuctionResult.Builder AUCTION_RESULT_WITH_WINNING_SELLER =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS_WITH_COMPONENT_REPORTING_URLS)
                    .setWinningSeller(COMPONENT_SELLER.toString());
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_COMPONENT_REPORTING_URL_WITH_INVALID_ETLD_1 =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(WIN_REPORTING_URLS_WITH_COMPONENT_REPORTING_URLS)
                            .setWinningSeller(DIFFERENT_SELLER.toString());
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_DIFFERENT_SELLER_REPORTING_URIS);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_DIFFERENT_BUYER_IN_REPORTING_URIS =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_DIFFERENT_BUYER_REPORTING_URIS);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_INTERACTION_REPORTING_DATA_EXCEEDS_MAX =
                    AuctionResult.newBuilder()
                            .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                            .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                            .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                            .setBuyer(WINNER_BUYER.toString())
                            .setBid((float) BID)
                            .setScore((float) SCORE)
                            .setIsChaff(false)
                            .setWinReportingUrls(
                                    WIN_REPORTING_URLS_WITH_INTERACTION_DATA_EXCEEDS_MAX);
    private static final AuctionResult.Builder
            AUCTION_RESULT_WITH_INTERACTION_URI_LENGTH_EXCEEDS_MAX =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(
                            WIN_REPORTING_URLS_INTERACTION_URIS_EXCEED_MAX);
    private static final AuctionResult.Builder AUCTION_RESULT_WITHOUT_OWNER =
            AuctionResult.newBuilder()
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNER_CUSTOM_AUDIENCE_NAME)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid((float) BID)
                    .setScore((float) SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS);
    private static final AuctionResult.Builder AUCTION_RESULT_CHAFF =
            AuctionResult.newBuilder().setIsChaff(true);
    private static final AuctionResult.Builder AUCTION_RESULT_WITH_ERROR =
            AuctionResult.newBuilder()
                    .setError(
                            AuctionResult.Error.newBuilder()
                                    .setCode(-1)
                                    .setMessage("AuctionServerError: Bad things happened!")
                                    .build());
    private static final Set<Integer> AD_COUNTER_KEYS = Set.of(1, 2, 3);
    private static final DBAdData WINNING_AD =
            new DBAdData.Builder()
                    .setRenderUri(WINNER_AD_RENDER_URI)
                    .setAdCounterKeys(AD_COUNTER_KEYS)
                    .setMetadata("")
                    .build();
    private static final DBCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, WINNER_CUSTOM_AUDIENCE_OWNER)
                    .setAds(ImmutableList.of(WINNING_AD))
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_WIN_AD_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER_1)
                    .setAds(
                            ImmutableList.of(
                                    new DBAdData.Builder()
                                            .setRenderUri(AD_RENDER_URI_1)
                                            .setMetadata("")
                                            .build()))
                    .build();
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_WIN_AD_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                            WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME, CUSTOM_AUDIENCE_OWNER_2)
                    .setAds(
                            ImmutableList.of(
                                    new DBAdData.Builder()
                                            .setRenderUri(AD_RENDER_URI_2)
                                            .setMetadata("")
                                            .build()))
                    .build();
    private static final List<DBCustomAudience> CUSTOM_AUDIENCE_LIST_INCLUDING_WINNER =
            List.of(
                    WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD,
                    CUSTOM_AUDIENCE_WITH_WIN_AD_1,
                    CUSTOM_AUDIENCE_WITH_WIN_AD_2);
    private static final byte[] CIPHER_TEXT_BYTES =
            "encrypted-cipher-for-auction-result".getBytes(StandardCharsets.UTF_8);
    private static final long AD_SELECTION_ID = 12345L;
    private static final AdSelectionInitialization INITIALIZATION_DATA =
            getAdSelectionInitialization(SELLER, CALLER_PACKAGE_NAME);
    private static final AdSelectionInitialization INITIALIZATION_DATA_WITH_DIFFERENT_SELLER =
            getAdSelectionInitialization(DIFFERENT_SELLER, CALLER_PACKAGE_NAME);
    private static final AdSelectionInitialization
            INITIALIZATION_DATA_WITH_DIFFERENT_CALLER_PACKAGE =
                    getAdSelectionInitialization(SELLER, DIFFERENT_CALLER_PACKAGE_NAME);
    private static final AdSelectionResultBidAndUri BID_AND_URI =
            getAdSelectionResultBidAndUri(AD_SELECTION_ID, BID, WINNER_AD_RENDER_URI);
    private static final WinningCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS =
            getWinningCustomAudience(
                    WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_CUSTOM_AUDIENCE_NAME, AD_COUNTER_KEYS);
    private static final WinningCustomAudience WINNER_CUSTOM_AUDIENCE_WITH_EMPTY_AD_COUNTER_KEYS =
            getWinningCustomAudience(
                    WINNER_CUSTOM_AUDIENCE_OWNER,
                    WINNER_CUSTOM_AUDIENCE_NAME,
                    Collections.emptySet());
    private static final WinningCustomAudience EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL =
            getWinningCustomAudience("", "", Collections.emptySet());
    private static final ReportingData REPORTING_DATA =
            getReportingData(Uri.parse(BUYER_REPORTING_URI), Uri.parse(SELLER_REPORTING_URI));
    private static final ReportingData REPORTING_DATA_WITH_EMPTY_SELLER =
            getReportingData(Uri.parse(BUYER_REPORTING_URI), Uri.EMPTY);
    private static final ReportingData REPORTING_DATA_WITH_EMPTY_BUYER =
            getReportingData(Uri.EMPTY, Uri.parse(SELLER_REPORTING_URI));

    private static final boolean FLEDGE_BEACON_REPORTING_METRICS_ENABLED_IN_TEST = true;

    private static final boolean FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED_IN_TEST = true;

    private static final boolean PAS_EXTENDED_METRICS_ENABLED_IN_TEST = true;

    private static final int ADSERVICES_STATUS_UNSET = -1;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;

    private Flags mFakeFlags;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    private AuctionServerPayloadFormatter mPayloadFormatter;
    private AuctionServerDataCompressor mDataCompressor;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private KAnonSignJoinFactory mKAnonSignJoinFactoryMock;
    @Mock private KAnonSignJoinManager mKAnonSignJoinManagerMock;
    @Captor private ArgumentCaptor<List<KAnonMessageEntity>> mKAnonMessageEntitiesCaptor;

    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private PersistAdSelectionResultRunner mPersistAdSelectionResultRunner;
    private long mOverallTimeout;
    private boolean mForceContinueOnAbsentOwner;
    private PersistAdSelectionResultRunner.ReportingRegistrationLimits mReportingLimits;
    private final AdCounterHistogramUpdater mAdCounterHistogramUpdaterSpy =
            spy(new AdCounterHistogramUpdaterNoOpImpl());

    private AuctionResultValidator mAuctionResultValidator;

    @Mock private Clock mFledgeAuctionServerExecutionLoggerClockMock;
    private AdsRelevanceExecutionLoggerFactory mAdsRelevanceExecutionLoggerFactory;
    private AdsRelevanceExecutionLogger mAdsRelevanceExecutionLogger;

    private ResultSyncCallback<ApiCallStats> logApiCallStatsCallback;

    private AdServicesLogger mAdServicesLoggerSpy;

    private ArgumentCaptor<PersistAdSelectionResultCalledStats>
            mPersistAdSelectionResultCalledStatsArgumentCaptor;

    @Before
    public void setup() throws InvalidKeySpecException, UnsupportedHpkeAlgorithmException {
        mFakeFlags = new PersistAdSelectionResultRunnerTestFlags();
        mocker.mockGetDebugFlags(mMockDebugFlags);
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdSelectionEntryDao =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                                .build()
                                .adSelectionEntryDao());
        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        AuctionServerPayloadFormatterV0.VERSION,
                        mFakeFlags.getFledgeAuctionServerPayloadBucketSizes(),
                        /* sellerConfiguration= */ null);
        mDataCompressor = new AuctionServerDataCompressorGzip();

        mOverallTimeout = FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        mForceContinueOnAbsentOwner = false;
        mReportingLimits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount())
                        .setMaxInteractionKeySize(
                                mFakeFlags
                                        .getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB())
                        .setMaxInteractionReportingUriSize(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxInteractionReportingUriSizeB())
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount())
                        .build();
        mAuctionResultValidator =
                new AuctionResultValidator(
                        mFledgeAuthorizationFilterMock, /* disableFledgeEnrollmentCheck */
                        false,
                        /* enableWinningSellerInAdSelectionOutcome= */ false);
        mAdServicesLoggerSpy = Mockito.spy(AdServicesLoggerImpl.getInstance());
        mAdsRelevanceExecutionLoggerFactory =
                new AdsRelevanceExecutionLoggerFactory(
                        TEST_PACKAGE_NAME,
                        sCallerMetadata,
                        mFledgeAuctionServerExecutionLoggerClockMock,
                        mAdServicesLoggerSpy,
                        mFakeFlags,
                        AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        setupPersistAdSelectionResultCalledLogging();
    }

    @Test
    public void testRunner_persistRemarketingResult_success() throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
        verify(mAdCounterHistogramUpdaterSpy)
                .updateWinHistogram(
                        WINNER_BUYER,
                        mAdSelectionEntryDao.getAdSelectionInitializationForId(AD_SELECTION_ID),
                        mAdSelectionEntryDao.getWinningCustomAudienceDataForId(AD_SELECTION_ID));

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_CA_WINNER);
    }

    @Test
    public void testRunner_withWinningSellerInOutComeDisabled_returnsEmptyWinningSeller()
            throws Exception {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return false;
            }
        }
        Flags flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled();
        mocker.mockGetFlags(flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_WINNING_SELLER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(
                "Unset Winning seller ",
                UNSET_AD_TECH_IDENTIFIER,
                callback.mPersistAdSelectionResultResponse.getWinningSeller());
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_RESULT_IS_CHAFF)
    public void testRunner_persistChaffResult_returnsEmptyWinningSeller() throws Exception {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return false;
            }
        }
        Flags flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled();
        mocker.mockGetFlags(flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerOutcomeInAdSelectionOutcomeDisabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_CHAFF))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        assertWithMessage("Empty ad render uri")
                .that(callback.mPersistAdSelectionResultResponse.getAdRenderUri())
                .isEqualTo(Uri.EMPTY);
        assertWithMessage("Ad selection id")
                .that(callback.mPersistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID);
        assertWithMessage("Empty winning seller id")
                .that(callback.mPersistAdSelectionResultResponse.getWinningSeller())
                .isEqualTo(UNSET_AD_TECH_IDENTIFIER);
    }

    @Test
    public void testRunner_withWinningSellerInOutComeEnabled_returnsCorrectWinningSeller()
            throws Exception {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcome
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return true;
            }
        }
        Flags flagsWithWinningSellerInOutcomeEnabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcome();
        mocker.mockGetFlags(flagsWithWinningSellerInOutcomeEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerInOutcomeEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_WINNING_SELLER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                "Ad render uri",
                WINNER_AD_RENDER_URI,
                callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                "Ad selection id",
                AD_SELECTION_ID,
                callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(
                "Winning seller ",
                COMPONENT_SELLER,
                callback.mPersistAdSelectionResultResponse.getWinningSeller());
    }

    @Test
    public void
            testRunner_winningSellerEnabledAndNoWinningSellerInAuctionResult_returnsEmptyWinner()
                    throws Exception {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcome
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return true;
            }
        }
        Flags flagsWithWinningSellerInOutcomeEnabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcome();
        mocker.mockGetFlags(flagsWithWinningSellerInOutcomeEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerInOutcomeEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(
                "Unset Winning seller ",
                UNSET_AD_TECH_IDENTIFIER,
                callback.mPersistAdSelectionResultResponse.getWinningSeller());
    }

    @Test
    public void testCreatePersistAdSelectionResultResponse_winningSellerEnabled_success() {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcome
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return true;
            }
        }
        Flags flagsWithWinningSellerInOutcomeEnabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcome();
        mocker.mockGetFlags(flagsWithWinningSellerInOutcomeEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerInOutcomeEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        AuctionResult auctionResult = AUCTION_RESULT_WITH_WINNING_SELLER.build();
        PersistAdSelectionResultResponse response =
                mPersistAdSelectionResultRunner.createPersistAdSelectionResultResponse(
                        auctionResult, AD_SELECTION_ID);

        Assert.assertEquals("Ad render uri", WINNER_AD_RENDER_URI, response.getAdRenderUri());
        Assert.assertEquals("Ad selection id", AD_SELECTION_ID, response.getAdSelectionId());
        Assert.assertEquals("Winning seller ", COMPONENT_SELLER, response.getWinningSeller());
    }

    @Test
    public void testCreatePersistAdSelectionResultResponse_winningSellerDisabled_success() {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcome
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return false;
            }
        }
        Flags flagsWithWinningSellerInOutcomeEnabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcome();
        mocker.mockGetFlags(flagsWithWinningSellerInOutcomeEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerInOutcomeEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        AuctionResult auctionResult = AUCTION_RESULT_WITH_WINNING_SELLER.build();
        PersistAdSelectionResultResponse response =
                mPersistAdSelectionResultRunner.createPersistAdSelectionResultResponse(
                        auctionResult, AD_SELECTION_ID);

        Assert.assertEquals("Ad render uri", WINNER_AD_RENDER_URI, response.getAdRenderUri());
        Assert.assertEquals("Ad selection id", AD_SELECTION_ID, response.getAdSelectionId());
        Assert.assertEquals(
                "Unset Winning seller ", UNSET_AD_TECH_IDENTIFIER, response.getWinningSeller());
    }

    @Test
    public void testCreatePersistAdSelectionResultResponse_withAuctionResultChaff_success() {
        class FlagsWithWinningSellerOutcomeInAdSelectionOutcome
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableWinningSellerIdInAdSelectionOutcome() {
                return true;
            }
        }
        Flags flagsWithWinningSellerInOutcomeEnabled =
                new FlagsWithWinningSellerOutcomeInAdSelectionOutcome();
        mocker.mockGetFlags(flagsWithWinningSellerInOutcomeEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithWinningSellerInOutcomeEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        AuctionResult auctionResult = AUCTION_RESULT_CHAFF.build();
        PersistAdSelectionResultResponse response =
                mPersistAdSelectionResultRunner.createPersistAdSelectionResultResponse(
                        auctionResult, AD_SELECTION_ID);

        assertWithMessage("Empty ad render uri")
                .that(response.getAdRenderUri())
                .isEqualTo(Uri.EMPTY);
        assertWithMessage("Ad selection id")
                .that(response.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID);
        assertWithMessage("Empty winning seller id")
                .that(response.getWinningSeller())
                .isEqualTo(UNSET_AD_TECH_IDENTIFIER);
    }

    @Test
    public void testRunner_persistAppInstallResult_success() throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForAppInstallAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
        verify(mAdCounterHistogramUpdaterSpy)
                .updateWinHistogram(
                        WINNER_BUYER,
                        mAdSelectionEntryDao.getAdSelectionInitializationForId(AD_SELECTION_ID),
                        mAdSelectionEntryDao.getWinningCustomAudienceDataForId(AD_SELECTION_ID));

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_PAS_WINNER);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_AD_TECH_URI)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI)
    public void testRunner_persistRemarketingResult_withInvalidSellerReportingUriSuccess()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_SELLER);
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_CA_WINNER);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_AD_TECH_URI)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI)
    public void testRunner_persistAppInstallResult_withInvalidSellerReportingUriSuccess()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        doReturn(
                        prepareDecryptedAuctionResultForAppInstallAd(
                                AUCTION_RESULT_WITH_DIFFERENT_SELLER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        EMPTY_CUSTOM_AUDIENCE_FOR_APP_INSTALL);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_SELLER);
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        BUYER_INTERACTION_KEY,
                                        BUYER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_PAS_WINNER);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_AD_TECH_URI)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI)
    public void testRunner_persistRemarketingResult_withInvalidBuyerReportingUriSuccess()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_DIFFERENT_BUYER_IN_REPORTING_URIS))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA_WITH_EMPTY_BUYER);
        verify(mAdSelectionEntryDao, times(1))
                .safelyInsertRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        List.of(
                                getDBRegisteredAdInteraction(
                                        SELLER_INTERACTION_KEY,
                                        SELLER_INTERACTION_URI,
                                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)),
                        mReportingLimits.getMaxRegisteredAdBeaconsTotalCount(),
                        mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount(),
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER));

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_CA_WINNER);
    }

    // TODO(b/291680065): Remove the test when owner field is returned from B&A
    @Test
    public void testRunner_persistRemarketingResult_forceOnAbsentOwnerFalseSkipsValidation()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITHOUT_OWNER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        boolean forceSearchOnAbsentOwner = false;
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        forceSearchOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudiencesForBuyerAndName(any(), any());
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudienceByPrimaryKey(any(), any(), any());

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_CA_WINNER);
    }

    // TODO(b/291680065): Remove the test when owner field is returned from B&A
    @Test
    public void testRunner_persistRemarketingResult_forceOnAbsentOwnerFalseFuzzySearch()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITHOUT_OWNER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(CUSTOM_AUDIENCE_LIST_INCLUDING_WINNER)
                .when(mCustomAudienceDaoMock)
                .getCustomAudiencesForBuyerAndName(WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        boolean forceSearchOnAbsentOwner = true;
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        forceSearchOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verify(mCustomAudienceDaoMock, times(1))
                .getCustomAudiencesForBuyerAndName(WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);
        verify(mCustomAudienceDaoMock, times(0)).getCustomAudienceByPrimaryKey(any(), any(), any());

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(2);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_CA_WINNER);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_RESULT_IS_CHAFF)
    public void testRunner_persistChaffResult_nothingPersisted() throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_CHAFF))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
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
        verify(mAdSelectionEntryDao, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDao, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER));
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(),
                        any(),
                        anyLong(),
                        anyLong(),
                        eq(ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER));

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_NO_WINNER);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_AUCTION_RESULT_HAS_ERROR)
    public void testRunner_persistResultWithError_throwsException() throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_ERROR))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_INVALID_ARGUMENT);

        verifyPersistAdSelectionResultWinnerType(WINNER_TYPE_NO_WINNER);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_TIMEOUT)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_TIMEOUT)
    public void testRunner_persistTimesOut_throwsException() throws Exception {
        mocker.mockGetFlags(mFakeFlags);

        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        mOverallTimeout = 200;
        when(mObliviousHttpEncryptorMock.decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID))
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * mOverallTimeout,
                                new Returns(
                                        prepareDecryptedAuctionResultForRemarketingAd(
                                                AUCTION_RESULT))));

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_TIMEOUT, callback.mFledgeErrorResponse.getStatusCode());

        verifyPersistAdSelectionResultApiUsageLog(STATUS_TIMEOUT);
    }

    @Test
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testRunner_revokedUserConsent_returnsEmptyResult() throws InterruptedException {
        mocker.mockGetFlags(mFakeFlags);

        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        ErrorLogUtilSyncCallback errorLogUtilWithoutThrowableCallback =
                mockErrorLogUtilWithoutThrowable(/* numExpectedCalls= */ 2);

        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_REVOKED_CONSENT_FILTER_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_EMPTY_SUCCESS_SILENT_CONSENT_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mPersistAdSelectionResultResponse);
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verifyZeroInteractions(mCustomAudienceDaoMock);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDao);
    }

    @Test
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testRunner_revokedUserConsent_silentReport() throws InterruptedException {
        mocker.mockGetFlags(mFakeFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));
        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ErrorLogUtilSyncCallback errorLogUtilWithoutThrowableCallback =
                mockErrorLogUtilWithoutThrowable(/* numExpectedCalls= */ 2);

        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_REVOKED_CONSENT_FILTER_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_EMPTY_SUCCESS_SILENT_CONSENT_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);
        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mPersistAdSelectionResultResponse);
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
    }

    @Test
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testRunner_revokedUserConsent_returnsEmptyResult_UXNotificationEnforcementDisabled()
            throws InterruptedException {
        mocker.mockGetConsentNotificationDebugMode(true);
        mocker.mockGetFlags(mFakeFlags);

        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        ErrorLogUtilSyncCallback errorLogUtilWithoutThrowableCallbackSync =
                mockErrorLogUtilWithoutThrowable(/* numExpectedCalls= */ 2);

        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        errorLogUtilWithoutThrowableCallbackSync.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_REVOKED_CONSENT_FILTER_EXCEPTION,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        errorLogUtilWithoutThrowableCallbackSync.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_EMPTY_SUCCESS_SILENT_CONSENT_FAILURE,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mPersistAdSelectionResultResponse);
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertEquals(Uri.EMPTY, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        verifyZeroInteractions(mCustomAudienceDaoMock);
        verifyZeroInteractions(mObliviousHttpEncryptorMock);
        verifyZeroInteractions(mAdSelectionEntryDao);

        verify(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(false),
                        eq(CALLER_UID),
                        eq(AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NULL_INITIALIZATION_INFO)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT)
    public void testRunner_persistResultWithNotEnrolledBuyer_throwsException() throws Exception {
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();
        Mockito.doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        WINNER_BUYER,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN);
        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);
        Assert.assertFalse(callback.mIsSuccess);
        assertNotNull(callback.mFledgeErrorResponse);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());

        verifyPersistAdSelectionResultApiUsageLog(STATUS_INVALID_ARGUMENT);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_MISMATCH_INITIALIZATION_INFO)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT)
    public void testRunner_persistResultWithWrongSeller_throwsException() throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA_WITH_DIFFERENT_SELLER);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mAdSelectionEntryDao, times(1)).getAdSelectionInitializationForId(AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDao, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());

        verifyPersistAdSelectionResultApiUsageLog(STATUS_INVALID_ARGUMENT);
    }

    @Test
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_NOTIFY_FAILURE_INVALID_ARGUMENT)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_MISMATCH_INITIALIZATION_INFO)
    public void testRunner_persistResultWithWrongCallerPackage_throwsException() throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                AD_SELECTION_ID, INITIALIZATION_DATA_WITH_DIFFERENT_CALLER_PACKAGE);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        verify(mAdSelectionEntryDao, times(1)).getAdSelectionInitializationForId(AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(0))
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        verify(mAdSelectionEntryDao, times(0)).persistReportingData(anyLong(), any());
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());

        verifyPersistAdSelectionResultApiUsageLog(STATUS_INVALID_ARGUMENT);
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI)
    @ExpectErrorLogUtilCall(
            errorCode = AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INTERACTION_URI_EXCEEDS_MAXIMUM_LIMIT)
    public void testRunner_persistResultWithLongInteractionKeyAndUri_throwsException()
            throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_INTERACTION_REPORTING_DATA_EXCEEDS_MAX))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultRunner.ReportingRegistrationLimits reportingLimits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount())
                        .setMaxInteractionKeySize(
                                SELLER_INTERACTION_KEY_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8)
                                                .length
                                        - 1)
                        .setMaxInteractionReportingUriSize(
                                BUYER_INTERACTION_URI_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8)
                                                .length
                                        - 1)
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount())
                        .build();

        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        reportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
        verify(mObliviousHttpEncryptorMock, times(1))
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        verify(mAdSelectionEntryDao, times(1))
                .persistAdSelectionResultForCustomAudience(
                        AD_SELECTION_ID,
                        BID_AND_URI,
                        WINNER_BUYER,
                        WINNER_CUSTOM_AUDIENCE_WITH_AD_COUNTER_KEYS);
        verify(mAdSelectionEntryDao, times(1))
                .persistReportingData(AD_SELECTION_ID, REPORTING_DATA);
        verify(mAdSelectionEntryDao, times(0))
                .safelyInsertRegisteredAdInteractions(
                        anyLong(), any(), anyLong(), anyLong(), anyInt());

        // Verifies DestinationRegisteredBeaconsReportedStats get the correct values.
        verify(mAdServicesLoggerSpy, times(2))
                .logDestinationRegisteredBeaconsReportedStats(argumentCaptor.capture());
        List<DestinationRegisteredBeaconsReportedStats> stats = argumentCaptor.getAllValues();
        assertThat(stats.size()).isEqualTo(2);
        // Verifies buyer destination log is correct.
        DestinationRegisteredBeaconsReportedStats buyerDestinationStats = stats.get(0);
        assertThat(buyerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(BUYER_DESTINATION);
        assertThat(buyerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(buyerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType
                                        .SMALLER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(buyerDestinationStats.getTableNumRows()).isEqualTo(0);
        assertThat(buyerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);
        // Verifies seller destination log is correct.
        DestinationRegisteredBeaconsReportedStats sellerDestinationStats = stats.get(1);
        assertThat(sellerDestinationStats.getBeaconReportingDestinationType())
                .isEqualTo(SELLER_DESTINATION);
        assertThat(sellerDestinationStats.getAttemptedRegisteredBeacons()).isEqualTo(1);
        assertThat(sellerDestinationStats.getAttemptedKeySizesRangeType())
                .isEqualTo(
                        Arrays.asList(
                                DestinationRegisteredBeaconsReportedStats
                                        .InteractionKeySizeRangeType.LARGER_THAN_MAXIMUM_KEY_SIZE));
        assertThat(sellerDestinationStats.getTableNumRows()).isEqualTo(0);
        assertThat(sellerDestinationStats.getAdServicesStatusCode())
                .isEqualTo(ADSERVICES_STATUS_UNSET);

        verifyPersistAdSelectionResultApiUsageLog(STATUS_SUCCESS);
    }

    @Test
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testRunner_persistResultWithLongInteractionUri_silentReport() throws Exception {
        mocker.mockGetFlags(mFakeFlags);
        mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger();

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(
                AUCTION_RESULT_WITH_INTERACTION_URI_LENGTH_EXCEEDS_MAX))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultRunner.ReportingRegistrationLimits reportingLimits =
                PersistAdSelectionResultRunner.ReportingRegistrationLimits.builder()
                        .setMaxRegisteredAdBeaconsTotalCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount())
                        .setMaxInteractionKeySize(
                                Math.max(SELLER_INTERACTION_KEY.getBytes(StandardCharsets.UTF_8)
                                        .length, BUYER_INTERACTION_KEY.getBytes(StandardCharsets.UTF_8).length))
                        .setMaxInteractionReportingUriSize(
                                Math.min(
                                        BUYER_INTERACTION_URI_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8).length,
                                        SELLER_INTERACTION_URI_EXCEEDS_MAX.getBytes(StandardCharsets.UTF_8).length)
                                        - 1)
                        .setMaxRegisteredAdBeaconsPerAdTechCount(
                                mFakeFlags
                                        .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount())
                        .build();
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        reportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        ErrorLogUtilSyncCallback errorLogUtilWithoutThrowableCallback =
                mockErrorLogUtilWithoutThrowable(/* numExpectedCalls= */ 2);

        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);

        errorLogUtilWithoutThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INTERACTION_URI_EXCEEDS_MAXIMUM_LIMIT,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT,
                /* numExpectedCalls= */ 2);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI, callback.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                AD_SELECTION_ID, callback.mPersistAdSelectionResultResponse.getAdSelectionId());
    }

    @Test
    public void testRunner_withKAnonSignFlagedDisabled_doesNothing() throws Exception {
        Flags flagsWithKAnonDisabled =
                new PersistAdSelectionResultRunnerTestFlagsForKAnon(false, 100);
        doReturn(flagsWithKAnonDisabled).when(FlagsFactory::getFlags);

        PersistAdSelectionResultInput inputParams = setupPersistRunnerMocksForKAnonTests();
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithKAnonDisabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerSpy)
                .logKAnonSignJoinStatus();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);
        countDownLatch.await();

        Assert.assertTrue(callback.mIsSuccess);
        verifyZeroInteractions(mKAnonSignJoinFactoryMock);
    }

    @Test
    // TODO (b/355696393): Enhance rule to verify log calls that happen in the background.
    @SkipLoggingUsageRule(
            reason = "Using ErrorLogUtilSyncCallback as logging happens in background.")
    public void testRunner_kanonSignJoinManagerThrowsException_persistSelectionRunnerIsSuccessful()
            throws Exception {
        // Do not move this into setup as it will conflict with ErrorLogUtil mocking behavior
        // required by the AdServicesLoggingUsageRule.
        ErrorLogUtilSyncCallback errorLogUtilWithThrowableCallback =
                mockErrorLogUtilWithoutThrowable();

        Flags flagsWithKAnonEnabled =
                new PersistAdSelectionResultRunnerTestFlagsForKAnon(true, 100);
        doReturn(flagsWithKAnonEnabled).when(FlagsFactory::getFlags);
        PersistAdSelectionResultInput inputParams = setupPersistRunnerMocksForKAnonTests();
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithKAnonEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            throw new RuntimeException("some random exception");
                        })
                .when(mKAnonSignJoinManagerMock)
                .processNewMessages(anyList());
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);
        countDownLatch.await();

        errorLogUtilWithThrowableCallback.assertReceived(
                expect,
                AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_PROCESSING_KANON_ERROR,
                AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__PERSIST_AD_SELECTION_RESULT);

        Assert.assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testRunner_withKAnonEnabled_makesSignAndJoinCalls() throws Exception {
        Flags flagsWithKAnonEnabled =
                new PersistAdSelectionResultRunnerTestFlagsForKAnon(true, 100);
        doReturn(flagsWithKAnonEnabled).when(FlagsFactory::getFlags);
        PersistAdSelectionResultInput inputParams = setupPersistRunnerMocksForKAnonTests();
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithKAnonEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mKAnonSignJoinManagerMock)
                .processNewMessages(anyList());
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);
        countDownLatch.await();

        Assert.assertTrue(callback.mIsSuccess);
        verify(mKAnonSignJoinManagerMock, times(1)).processNewMessages(anyList());
    }

    @Test
    public void testRunner_withKAnonEnabled_createsKAnonEntityWithCorrectEncoding()
            throws Exception {
        Flags flagsWithKAnonEnabled =
                new PersistAdSelectionResultRunnerTestFlagsForKAnon(true, 100);
        doReturn(flagsWithKAnonEnabled).when(FlagsFactory::getFlags);
        PersistAdSelectionResultInput inputParams = setupPersistRunnerMocksForKAnonTests();
        PersistAdSelectionResultRunner persistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithKAnonEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mKAnonSignJoinManagerMock)
                .processNewMessages(anyList());

        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(persistAdSelectionResultRunner, inputParams);
        countDownLatch.await();

        Assert.assertTrue(callback.mIsSuccess);
        MessageDigest md = MessageDigest.getInstance(SHA256);
        String winningUrl = callback.mPersistAdSelectionResultResponse.getAdRenderUri().toString();
        byte[] expectedBytes = md.digest(winningUrl.getBytes(StandardCharsets.UTF_8));

        verify(mKAnonSignJoinManagerMock, times(1))
                .processNewMessages(mKAnonMessageEntitiesCaptor.capture());
        List<KAnonMessageEntity> capturedMessageEntity = mKAnonMessageEntitiesCaptor.getValue();

        assertThat(capturedMessageEntity.size()).isEqualTo(1);

        byte[] actualBytes =
                Base64.getUrlDecoder().decode(capturedMessageEntity.get(0).getHashSet());
        assertThat(actualBytes).isEqualTo(expectedBytes);
    }

    @Test
    public void
            testRunner_withReportingForComponentSellerEnabled_persistsInteractionAndReportingData()
                    throws Exception {
        class FlagsWithComponentSellerReportingEnabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableReportEventForComponentSeller() {
                return true;
            }
        }
        Flags flagsWithComponentSellerReportingEnabled =
                new FlagsWithComponentSellerReportingEnabled();
        mocker.mockGetFlags(flagsWithComponentSellerReportingEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithComponentSellerReportingEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_WINNING_SELLER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        ReportingData reportingDataBeforeApiCall =
                mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ID);
        assertWithMessage("Component seller win reporting uri before persistAdSelectionAPI")
                .that(reportingDataBeforeApiCall)
                .isNull();
        List<RegisteredAdInteraction> registeredComponentSellerAdInteractionsBeforeApiCall =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        assertWithMessage("Empty list of registered ad interaction")
                .that(registeredComponentSellerAdInteractionsBeforeApiCall)
                .isEmpty();

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        assertWithMessage("Callback success").that(callback.mIsSuccess).isTrue();
        assertWithMessage("Callback ad selection id")
                .that(callback.mPersistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID);

        ReportingData reportingData =
                mAdSelectionEntryDao.getReportingDataForId(
                        AD_SELECTION_ID, /* shouldUseUnifiedTables= */ true);
        assertWithMessage("Component seller win reporting uri")
                .that(reportingData.getComponentSellerWinReportingUri().toString())
                .isEqualTo(COMPONENT_SELLER_REPORTING_URI);

        List<RegisteredAdInteraction> registeredComponentSellerAdInteractions =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        RegisteredAdInteraction expectedRegisteredAdInteraction =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(COMPONENT_SELLER_INTERACTION_KEY)
                        .setInteractionReportingUri(Uri.parse(COMPONENT_SELLER_INTERACTION_URI))
                        .build();
        assertWithMessage("Registered ad interaction for component seller")
                .that(registeredComponentSellerAdInteractions)
                .containsExactly(expectedRegisteredAdInteraction);
    }

    @Test
    public void
            testPersistAdInteractionKeysAndUrls_withReportingForComponentSellerEnabled_persistsInteraction()
                    throws Exception {
        class FlagsWithComponentSellerReportingEnabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableReportEventForComponentSeller() {
                return true;
            }
        }
        Flags flagsWithComponentSellerReportingEnabled =
                new FlagsWithComponentSellerReportingEnabled();
        mocker.mockGetFlags(flagsWithComponentSellerReportingEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithComponentSellerReportingEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        List<RegisteredAdInteraction> registeredComponentSellerAdInteractionsBeforeApiCall =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        assertWithMessage("Empty list of registered ad interaction")
                .that(registeredComponentSellerAdInteractionsBeforeApiCall)
                .isEmpty();

        mPersistAdSelectionResultRunner.persistAdInteractionKeysAndUrls(
                AUCTION_RESULT_WITH_WINNING_SELLER.build(), AD_SELECTION_ID, SELLER);

        List<RegisteredAdInteraction> registeredComponentSellerAdInteractions =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        RegisteredAdInteraction expectedRegisteredAdInteraction =
                RegisteredAdInteraction.builder()
                        .setInteractionKey(COMPONENT_SELLER_INTERACTION_KEY)
                        .setInteractionReportingUri(Uri.parse(COMPONENT_SELLER_INTERACTION_URI))
                        .build();
        assertWithMessage("Registered ad interaction for component seller")
                .that(registeredComponentSellerAdInteractions)
                .containsExactly(expectedRegisteredAdInteraction);
    }

    @Test
    public void
            testPersistAuctionResults_withReportingForComponentSellerEnabled_persistsReportingData()
                    throws Exception {
        class FlagsWithComponentSellerReportingEnabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableReportEventForComponentSeller() {
                return true;
            }
        }
        Flags flagsWithComponentSellerReportingEnabled =
                new FlagsWithComponentSellerReportingEnabled();
        mocker.mockGetFlags(flagsWithComponentSellerReportingEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithComponentSellerReportingEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        ReportingData reportingDataBeforeApiCall =
                mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ID);
        assertWithMessage("Component seller win reporting uri before persistAdSelectionAPI")
                .that(reportingDataBeforeApiCall)
                .isNull();


        mPersistAdSelectionResultRunner.persistAuctionResults(
                AUCTION_RESULT_WITH_WINNING_SELLER.build(), WINNING_AD, AD_SELECTION_ID, SELLER);

        ReportingData reportingData =
                mAdSelectionEntryDao.getReportingDataForId(
                        AD_SELECTION_ID, /* shouldUseUnifiedTables= */ true);
        assertWithMessage("Component seller win reporting uri")
                .that(reportingData.getComponentSellerWinReportingUri().toString())
                .isEqualTo(COMPONENT_SELLER_REPORTING_URI);
    }

    @Test
    public void test_reportingForComponentSellerDisabled_doesNotPersistInteractionAndReportingData()
            throws Exception {
        class FlagsWithComponentSellerReportingEnabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableReportEventForComponentSeller() {
                return false;
            }
        }
        Flags flagsWithComponentSellerReportingEnabled =
                new FlagsWithComponentSellerReportingEnabled();
        mocker.mockGetFlags(flagsWithComponentSellerReportingEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithComponentSellerReportingEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT_WITH_WINNING_SELLER))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        assertWithMessage("Callback success").that(callback.mIsSuccess).isTrue();
        assertWithMessage("Callback ad selection id")
                .that(callback.mPersistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID);

        ReportingData reportingData =
                mAdSelectionEntryDao.getReportingDataForId(
                        AD_SELECTION_ID, /* shouldUseUnifiedTables= */ true);
        assertWithMessage("Null component seller win reporting uri")
                .that(reportingData.getComponentSellerWinReportingUri())
                .isNull();

        List<RegisteredAdInteraction> registeredComponentSellerAdInteractions =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        assertWithMessage("Registered ad interaction for component seller")
                .that(registeredComponentSellerAdInteractions)
                .isEmpty();
    }

    @Test
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_AD_TECH_URI)
    @ExpectErrorLogUtilWithExceptionCall(
            errorCode =
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__PERSIST_AD_SELECTION_RESULT_RUNNER_INVALID_INTERACTION_URI)
    public void
            test_reportingForComponentSellerEnabled_failedValidation_doesNotPersistInteractionAndReportingData()
                    throws Exception {
        class FlagsWithComponentSellerReportingEnabled
                extends PersistAdSelectionResultRunnerTestFlags {
            @Override
            public boolean getEnableReportEventForComponentSeller() {
                return true;
            }
        }
        Flags flagsWithComponentSellerReportingEnabled =
                new FlagsWithComponentSellerReportingEnabled();
        mocker.mockGetFlags(flagsWithComponentSellerReportingEnabled);

        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        flagsWithComponentSellerReportingEnabled,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);

        doReturn(
                        prepareDecryptedAuctionResultForRemarketingAd(
                                AUCTION_RESULT_WITH_COMPONENT_REPORTING_URL_WITH_INVALID_ETLD_1))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        PersistAdSelectionResultInput inputParams =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionResult(CIPHER_TEXT_BYTES)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback =
                invokePersistAdSelectionResult(mPersistAdSelectionResultRunner, inputParams);

        assertWithMessage("Callback success").that(callback.mIsSuccess).isTrue();
        assertWithMessage("Callback ad selection id")
                .that(callback.mPersistAdSelectionResultResponse.getAdSelectionId())
                .isEqualTo(AD_SELECTION_ID);

        ReportingData reportingData =
                mAdSelectionEntryDao.getReportingDataForId(
                        AD_SELECTION_ID, /* shouldUseUnifiedTables= */ true);

        Uri componentSeller = reportingData.getComponentSellerWinReportingUri();
        assertWithMessage("Empty component seller win reporting uri")
                .that(reportingData.getComponentSellerWinReportingUri().toString())
                .isEmpty();

        List<RegisteredAdInteraction> registeredComponentSellerAdInteractions =
                mAdSelectionEntryDao.listRegisteredAdInteractions(
                        AD_SELECTION_ID,
                        ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER);
        assertWithMessage("Registered ad interaction for component seller")
                .that(registeredComponentSellerAdInteractions)
                .isEmpty();
    }

    private byte[] prepareDecryptedAuctionResultForRemarketingAd(
            AuctionResult.Builder auctionResult) {
        return prepareDecryptedAuctionResult(
                auctionResult.setAdType(AuctionResult.AdType.REMARKETING_AD).build());
    }

    private byte[] prepareDecryptedAuctionResultForAppInstallAd(
            AuctionResult.Builder auctionResult) {
        return prepareDecryptedAuctionResult(
                auctionResult
                        .setCustomAudienceName("")
                        .setCustomAudienceOwner("")
                        .setAdType(AuctionResult.AdType.APP_INSTALL_AD)
                        .build());
    }

    private byte[] prepareDecryptedAuctionResult(AuctionResult auctionResult) {
        byte[] auctionResultBytes = auctionResult.toByteArray();
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

    private DBRegisteredAdInteraction getDBRegisteredAdInteraction(
            String interactionKey, String interactionUri, int reportingDestination) {
        return DBRegisteredAdInteraction.builder()
                .setAdSelectionId(AD_SELECTION_ID)
                .setInteractionKey(interactionKey)
                .setInteractionReportingUri(Uri.parse(interactionUri))
                .setDestination(reportingDestination)
                .build();
    }

    private void mockPersistAdSelectionResultWithFledgeAuctionServerExecutionLogger() {
        when(mFledgeAuctionServerExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        BINDER_ELAPSED_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_RESULT_END_TIMESTAMP);
        logApiCallStatsCallback = mocker.mockLogApiCallStats(mAdServicesLoggerSpy);
        mAdsRelevanceExecutionLogger =
                mAdsRelevanceExecutionLoggerFactory.getAdsRelevanceExecutionLogger();
        mPersistAdSelectionResultRunner =
                new PersistAdSelectionResultRunner(
                        mObliviousHttpEncryptorMock,
                        mAdSelectionEntryDao,
                        mCustomAudienceDaoMock,
                        mAdSelectionServiceFilterMock,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mScheduledExecutor,
                        CALLER_UID,
                        DevContext.createForDevOptionsDisabled(),
                        mOverallTimeout,
                        mForceContinueOnAbsentOwner,
                        mReportingLimits,
                        mAdCounterHistogramUpdaterSpy,
                        mAuctionResultValidator,
                        mFakeFlags,
                        mMockDebugFlags,
                        mAdServicesLoggerSpy,
                        mAdsRelevanceExecutionLogger,
                        mKAnonSignJoinFactoryMock);
    }

    private void verifyPersistAdSelectionResultApiUsageLog(int resultCode)
            throws InterruptedException {
        ApiCallStats apiCallStats = logApiCallStatsCallback.assertResultReceived();
        assertThat(apiCallStats.getApiName()).isEqualTo(
                AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT);
        assertThat(apiCallStats.getAppPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        assertThat(apiCallStats.getResultCode()).isEqualTo(resultCode);
        assertThat(apiCallStats.getLatencyMillisecond()).isEqualTo(
                PERSIST_AD_SELECTION_RESULT_OVERALL_LATENCY_MS);
    }

    private PersistAdSelectionResultInput setupPersistRunnerMocksForKAnonTests() {
        doReturn(mKAnonSignJoinManagerMock)
                .when(mKAnonSignJoinFactoryMock)
                .getKAnonSignJoinManager();

        // Uses ArgumentCaptor to capture the logs in the tests.
        ArgumentCaptor<DestinationRegisteredBeaconsReportedStats> argumentCaptor =
                ArgumentCaptor.forClass(DestinationRegisteredBeaconsReportedStats.class);

        doReturn(prepareDecryptedAuctionResultForRemarketingAd(AUCTION_RESULT))
                .when(mObliviousHttpEncryptorMock)
                .decryptBytes(CIPHER_TEXT_BYTES, AD_SELECTION_ID);
        doReturn(WINNER_CUSTOM_AUDIENCE_WITH_WIN_AD)
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceByPrimaryKey(
                        WINNER_CUSTOM_AUDIENCE_OWNER, WINNER_BUYER, WINNER_CUSTOM_AUDIENCE_NAME);

        mAdSelectionEntryDao.persistAdSelectionInitialization(AD_SELECTION_ID, INITIALIZATION_DATA);

        return new PersistAdSelectionResultInput.Builder()
                .setSeller(SELLER)
                .setAdSelectionId(AD_SELECTION_ID)
                .setAdSelectionResult(CIPHER_TEXT_BYTES)
                .setCallerPackageName(CALLER_PACKAGE_NAME)
                .build();
    }

    public static class PersistAdSelectionResultRunnerTestFlags implements Flags {
        @Override
        public long getFledgeAuctionServerOverallTimeoutMs() {
            return FLEDGE_AUCTION_SERVER_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getFledgeCustomAudienceActiveTimeWindowInMs() {
            return FLEDGE_CUSTOM_AUDIENCE_ACTIVE_TIME_WINDOW_MS;
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return FLEDGE_BEACON_REPORTING_METRICS_ENABLED_IN_TEST;
        }

        @Override
        public boolean getFledgeAuctionServerApiUsageMetricsEnabled() {
            return FLEDGE_AUCTION_SERVER_API_USAGE_METRICS_ENABLED_IN_TEST;
        }

        @Override
        public boolean getPasExtendedMetricsEnabled() {
            return PAS_EXTENDED_METRICS_ENABLED_IN_TEST;
        }
    }

    public static class PersistAdSelectionResultRunnerTestFlagsForKAnon
            extends PersistAdSelectionResultRunnerTestFlags {
        private boolean mKAnonFeatureFlagEnabled;
        private int mPercentageImmediateJoinValue;

        public PersistAdSelectionResultRunnerTestFlagsForKAnon(
                boolean kAnonFlagEnabled, int percentageImmediateJoinValue) {
            mKAnonFeatureFlagEnabled = kAnonFlagEnabled;
            mPercentageImmediateJoinValue = percentageImmediateJoinValue;
        }

        @Override
        public boolean getFledgeKAnonSignJoinFeatureEnabled() {
            return mKAnonFeatureFlagEnabled;
        }

        @Override
        public int getFledgeKAnonPercentageImmediateSignJoinCalls() {
            return mPercentageImmediateJoinValue;
        }

        @Override
        public boolean getFledgeKAnonSignJoinFeatureAuctionServerEnabled() {
            return mKAnonFeatureFlagEnabled;
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

    private void setupPersistAdSelectionResultCalledLogging() {
        mPersistAdSelectionResultCalledStatsArgumentCaptor =
                ArgumentCaptor.forClass(PersistAdSelectionResultCalledStats.class);
    }

    private void verifyPersistAdSelectionResultWinnerType(
            @AdsRelevanceStatusUtils.WinnerType int winnerType) {
        verify(mAdServicesLoggerSpy)
                .logPersistAdSelectionResultCalledStats(
                        mPersistAdSelectionResultCalledStatsArgumentCaptor.capture());
        PersistAdSelectionResultCalledStats stats =
                mPersistAdSelectionResultCalledStatsArgumentCaptor.getValue();
        assertThat(stats.getWinnerType()).isEqualTo(winnerType);
    }
}
