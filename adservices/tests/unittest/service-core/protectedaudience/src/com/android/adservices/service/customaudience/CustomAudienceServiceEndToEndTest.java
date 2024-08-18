/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SERVER_RATE_LIMIT_REACHED;
import static android.adservices.common.CommonFixture.FIXED_NOW;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;

import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponse;
import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponseString;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ACTIVATION_TIME;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.LEAVE_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CA_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_1;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.PARTIAL_CUSTOM_AUDIENCE_2;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.ScheduleUpdateTestCallback;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.UPDATE_ID;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.VALID_BIDDING_SIGNALS;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.createJsonResponsePayload;
import static com.android.adservices.service.customaudience.ScheduleCustomAudienceUpdateTestUtils.extractPartialCustomAudiencesFromRequest;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesPermissions;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierFailureImpl;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateInput;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.IBinder;
import android.os.LimitExceededException;
import android.os.RemoteException;

import androidx.room.Room;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBPartialCustomAudience;
import com.android.adservices.data.customaudience.DBScheduledCustomAudienceUpdate;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.JsVersionRegister;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeApiThrottleFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FledgeConsentFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
@SpyStatic(ScheduleCustomAudienceUpdateJobService.class)
@MockStatic(BackgroundFetchJob.class)
public final class CustomAudienceServiceEndToEndTest extends AdServicesExtendedMockitoTestCase {
    @Rule(order = 11)
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_1 =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_2 =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS =
            CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                            CommonFixture.VALID_BUYER_1, FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME =
            CustomAudienceFixture.getValidBuilderForBuyerFilters(CommonFixture.VALID_BUYER_1)
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                    .build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1).build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS =
            DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(
                            CommonFixture.VALID_BUYER_1)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final String MY_APP_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier LOCALHOST_BUYER =
            AdTechIdentifier.fromString("localhost");
    private static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("BUYER_1");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("BUYER_2");
    private static final String NAME_1 = "NAME_1";
    private static final String NAME_2 = "NAME_2";
    private static final String UPDATE_URI_PATH = "/update";
    private static final String UPDATE_URI_PATH_2 = "/update2";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private static final FrequencyCapAdDataValidator FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP =
            new FrequencyCapAdDataValidatorNoOpImpl();

    private static final AdRenderIdValidator RENDER_ID_VALIDATOR_NO_OP =
            AdRenderIdValidator.AD_RENDER_ID_VALIDATOR_NO_OP;

    private CustomAudienceDao mCustomAudienceDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;

    private CustomAudienceServiceImpl mService;

    private ScheduledUpdatesHandler mScheduledUpdatesHandler;

    @Mock private ConsentManager mConsentManagerMock;
    @Mock private FledgeConsentFilter mFledgeConsentFilterMock;
    @Mock private CustomAudienceQuantityChecker mCustomAudienceQuantityCheckerMock;
    @Mock private CustomAudienceValidator mCustomAudienceValidatorMock;

    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilter;
    @Mock private FledgeApiThrottleFilter mFledgeApiThrottleFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilter;

    private FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private Uri mFetchUri;
    private CustomAudienceQuantityChecker mCustomAudienceQuantityChecker;
    private CustomAudienceValidator mCustomAudienceValidator;

    private static final Flags COMMON_FLAGS_WITH_FILTERS_ENABLED =
            new CustomAudienceServiceE2ETestFlags() {
                @Override
                public boolean getFledgeFrequencyCapFilteringEnabled() {
                    return true;
                }

                @Override
                public boolean getFledgeAppInstallFilteringEnabled() {
                    return true;
                }
            };

    @Before
    public void setup() {
        doReturn(new CustomAudienceServiceE2ETestFlags()).when(FlagsFactory::getFlags);

        mFetchUri = mMockWebServerRule.uriForPath("/fetch");

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true, true))
                        .build()
                        .customAudienceDao();

        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class).build();
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();

        mCustomAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, COMMON_FLAGS_WITH_FILTERS_ENABLED);

        mCustomAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        // Spy FledgeAuthorizationFilter to bypass the permission check in some tests in order to
        // validate other checks, such as package name mismatching.
        mFledgeAuthorizationFilterSpy =
                spy(
                        new FledgeAuthorizationFilter(
                                mContext.getPackageManager(),
                                EnrollmentDao.getInstance(),
                                mAdServicesLoggerMock));

        mService =
                new CustomAudienceServiceImpl(
                        mContext,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED),
                        mFledgeAuthorizationFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                mContext,
                                mFledgeConsentFilterMock,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        mContext.getPackageManager(),
                                        EnrollmentDao.getInstance(),
                                        mAdServicesLoggerMock),
                                new FledgeAllowListsFilter(
                                        COMMON_FLAGS_WITH_FILTERS_ENABLED, mAdServicesLoggerMock),
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao,
                                mFrequencyCapDao,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED));

        Mockito.doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilter)
                .createDevContext();

        mScheduledUpdatesHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                CacheProviderFactory.createNoOpCache()),
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP,
                        AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                                true, true, true),
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED),
                        mCustomAudienceQuantityChecker);
    }

    @Test
    public void testJoinCustomAudience_notInBinderThread_fail() {
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, COMMON_FLAGS_WITH_FILTERS_ENABLED);

        CustomAudienceValidator customAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        mService =
                new CustomAudienceServiceImpl(
                        mContext,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                customAudienceQuantityChecker,
                                customAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED),
                        new FledgeAuthorizationFilter(
                                mContext.getPackageManager(),
                                EnrollmentDao.getInstance(),
                                mAdServicesLoggerMock),
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                mContext,
                                mFledgeConsentFilterMock,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        mContext.getPackageManager(),
                                        EnrollmentDao.getInstance(),
                                        mAdServicesLoggerMock),
                                new FledgeAllowListsFilter(
                                        COMMON_FLAGS_WITH_FILTERS_ENABLED, mAdServicesLoggerMock),
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao,
                                mFrequencyCapDao,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED));

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.joinCustomAudience(
                                CUSTOM_AUDIENCE_PK1_1,
                                CustomAudienceFixture.VALID_OWNER,
                                callback));
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_callerPackageNameMismatch_fail() {
        String otherOwnerPackageName = "other_owner";
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1).build(),
                otherOwnerPackageName,
                callback);

        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof SecurityException);
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.getException().getMessage());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__JOIN_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
    }

    @Test
    public void testJoinCustomAudience_joinTwice_secondJoinOverrideValues() {
        doReturn(COMMON_FLAGS_WITH_FILTERS_ENABLED).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_2, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJob.schedule(any()), times(2));
    }

    @Test
    public void testJoinCustomAudienceAppInstallDisabled() {
        Flags flagsWithAppInstallDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithAppInstallDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithAppInstallDisabled);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());

        DBCustomAudience result =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1, VALID_NAME);

        assertNoAppInstallFilters(result);
        verifyFCapFiltersNotNull(result);
        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void testJoinCustomAudienceFrequencyCapDisabled() {
        Flags flagsWithFCapDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithFCapDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithFCapDisabled);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());

        DBCustomAudience result =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER, CommonFixture.VALID_BUYER_1, VALID_NAME);

        assertNoFCapFilters(result);
        verifyAppInstallFiltersNotNull(result);
        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void
            testJoinCustomAudience_joinTwice_secondJoinOverrideValuesWithAuctionServerRequestFlags() {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                getFlagsWithAuctionServerRequestFlagsEnabled();

        doReturn(flagsWithAuctionServerRequestFlagsEnabled).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        reInitServiceWithFlags(flagsWithAuctionServerRequestFlagsEnabled);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS,
                CustomAudienceFixture.VALID_OWNER,
                callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJob.schedule(any()), times(2));
    }

    @Test
    public void testJoinCustomAudienceWithRevokedUserConsentForAppSuccess() {
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));
    }

    @Test
    public void testJoinCustomAudience_beyondMaxExpirationTime_fail() {
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME,
                CustomAudienceFixture.VALID_OWNER,
                callback);
        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof IllegalArgumentException);
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testFetchAndJoinCustomAudience_overridesJoinCustomAudience() throws Exception {
        // Join a custom audience using the joinCustomAudience API.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER).build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Fetch and join a custom audience with the same owner, buyer and name but a different
        // value for one of fields. In this case, we'll use a different activation time.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_DELAYED_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void testFetchAndJoinCustomAudienceAppInstallDisabled() throws Exception {
        Flags flagsWithAppInstallDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return false;
                    }
                };

        doReturn(flagsWithAppInstallDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithAppInstallDisabled);

        // Fetch and join a custom audience with the same owner, buyer and name but a different
        // value for one of fields. In this case, we'll use a different activation time.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertNoAppInstallFilters(persistedCustomAudience);
        verifyFCapFiltersNotNull(persistedCustomAudience);
    }

    @Test
    public void testFetchAndJoinCustomAudienceFrequencyCapDisabled() throws Exception {
        Flags flagsWithFrequencyCapDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }
                };

        doReturn(flagsWithFrequencyCapDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithFrequencyCapDisabled);

        // Fetch and join a custom audience with the same owner, buyer and name but a different
        // value for one of fields. In this case, we'll use a different activation time.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertNoFCapFilters(persistedCustomAudience);
        verifyAppInstallFiltersNotNull(persistedCustomAudience);
    }

    @Test
    public void
            testFetchAndJoinCustomAudience_overridesJoinCustomAudienceWithAuctionServerReqeustFlagsEnabled()
                    throws Exception {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                getFlagsWithAuctionServerRequestFlagsEnabled();

        doReturn(flagsWithAuctionServerRequestFlagsEnabled).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        reInitServiceWithFlags(flagsWithAuctionServerRequestFlagsEnabled);

        // Join a custom audience using the joinCustomAudience API.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER).build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Fetch and join a custom audience with the same owner, buyer and name but a different
        // value for one of fields. In this case, we'll use a different activation time.
        boolean auctionServerRequestFlagsEnabled = true;
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponse(
                                                                LOCALHOST_BUYER,
                                                                auctionServerRequestFlagsEnabled)
                                                        .toString())));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_DELAYED_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                persistedCustomAudience.getAuctionServerRequestFlags());
    }

    @Test
    public void testFetchAndJoinCustomAudience_overriddenByJoinCustomAudience() throws Exception {
        // Fetch and join a custom audience.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Join a custom audience using the joinCustomAudience API with the same owner, buyer and
        // name but a different value for one of fields. In this case, we'll use a different
        // activation time.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER).build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the joined custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void
            testFetchAndJoinCustomAudience_overriddenByJoinCustomAudienceWithAuctionServerRequestFlag()
                    throws Exception {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                getFlagsWithAuctionServerRequestFlagsEnabled();

        doReturn(flagsWithAuctionServerRequestFlagsEnabled).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        reInitServiceWithFlags(flagsWithAuctionServerRequestFlagsEnabled);

        // Fetch and join a custom audience.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Join a custom audience using the joinCustomAudience API with the same owner, buyer and
        // name but a different value for one of fields. In this case, we'll use a different
        // activation time.
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                                LOCALHOST_BUYER, FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Assert persisted custom audience's activation time is from the joined custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                persistedCustomAudience.getAuctionServerRequestFlags());
    }

    @Test
    public void testFetchAndJoinCustomAudience_FirstJoinsQuarantineTableSecondIsFiltered()
            throws Exception {
        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER_1));

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setResponseCode(429)));

        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback1 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch1);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback1);
        resultLatch1.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback1.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback1.mFledgeErrorResponse.getStatusCode());
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Try to make the same request again, should fail with STATUS_SERVER_RATE_LIMIT_REACHED but
        // should not request the server
        CountDownLatch resultLatch2 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback2 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch2);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback2);
        resultLatch2.await();
        // Assert a new request was not made
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback2.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback2.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testFetchAndJoinCustomAudience_FirstJoinsQuarantineTableSecondPasses()
            throws Exception {
        Flags flagsWithZeroMinRetry =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public long getFledgeFetchCustomAudienceMinRetryAfterValueMs() {
                        return 0;
                    }
                };

        reInitServiceWithFlags(flagsWithZeroMinRetry);

        assertFalse(mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER_1));

        // Start server that returns 429 with 1 ms before expiration in first call, and allows the
        // second call
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setResponseCode(429).setHeader("Retry-After", 1),
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));

        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback1 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch1);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback1);
        resultLatch1.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(fetchAndJoinCallback1.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED,
                fetchAndJoinCallback1.mFledgeErrorResponse.getStatusCode());
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Try to make the same request again, should pass this time
        CountDownLatch resultLatch2 = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback2 =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch2);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback2);
        resultLatch2.await();
        // Assert a new request was not made
        assertEquals(2, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback2.mIsSuccess);

        // Assert entry was cleared
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceQuarantineExist(VALID_OWNER, LOCALHOST_BUYER));

        // Assert persisted custom audience's activation time is from the fetched custom audience.
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME);
        assertEquals(VALID_DELAYED_ACTIVATION_TIME, persistedCustomAudience.getActivationTime());
    }

    @Test
    public void testFetchAndJoinCustomAudience_leaveSuccessfully() throws Exception {
        // Fetch and join a custom audience.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        LOCALHOST_BUYER))));
        FetchAndJoinCustomAudienceInput input =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(VALID_NAME)
                        .setActivationTime(VALID_DELAYED_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback fetchAndJoinCallback =
                new FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback(resultLatch);
        mService.fetchAndJoinCustomAudience(input, fetchAndJoinCallback);
        resultLatch.await();
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(fetchAndJoinCallback.mIsSuccess);

        // Leave the fetched and joined custom audience.
        ResultCapturingCallback leaveCallback = new ResultCapturingCallback();
        mService.leaveCustomAudience(VALID_OWNER, LOCALHOST_BUYER, VALID_NAME, leaveCallback);
        assertTrue(leaveCallback.mIsSuccess);

        // Assert the custom audience does not exist in the DB.
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_notInBinderThread_fail() {
        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(
                        mCustomAudienceDao, COMMON_FLAGS_WITH_FILTERS_ENABLED);

        CustomAudienceValidator customAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP);

        mService =
                new CustomAudienceServiceImpl(
                        mContext,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                customAudienceQuantityChecker,
                                customAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED),
                        new FledgeAuthorizationFilter(
                                mContext.getPackageManager(),
                                EnrollmentDao.getInstance(),
                                mAdServicesLoggerMock),
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        COMMON_FLAGS_WITH_FILTERS_ENABLED,
                        CallingAppUidSupplierFailureImpl.create(),
                        new CustomAudienceServiceFilter(
                                mContext,
                                mFledgeConsentFilterMock,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        mContext.getPackageManager(),
                                        EnrollmentDao.getInstance(),
                                        mAdServicesLoggerMock),
                                new FledgeAllowListsFilter(
                                        COMMON_FLAGS_WITH_FILTERS_ENABLED, mAdServicesLoggerMock),
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(
                                mAppInstallDao,
                                mFrequencyCapDao,
                                COMMON_FLAGS_WITH_FILTERS_ENABLED));

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () ->
                        mService.leaveCustomAudience(
                                CustomAudienceFixture.VALID_OWNER,
                                CommonFixture.VALID_BUYER_1,
                                VALID_NAME,
                                callback));
    }

    @Test
    public void testLeaveCustomAudience_callerPackageNameMismatch_fail() {
        String otherOwnerPackageName = "other_owner";
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                otherOwnerPackageName, CommonFixture.VALID_BUYER_1, VALID_NAME, callback);

        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof SecurityException);
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.getException().getMessage());
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        otherOwnerPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__LEAVE_CUSTOM_AUDIENCE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudience() {
        doReturn(COMMON_FLAGS_WITH_FILTERS_ENABLED).when(FlagsFactory::getFlags);
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudienceWithAuctionServerRequestFlags() {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                getFlagsWithAuctionServerRequestFlagsEnabled();

        doReturn(flagsWithAuctionServerRequestFlagsEnabled).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        reInitServiceWithFlags(flagsWithAuctionServerRequestFlagsEnabled);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS,
                CustomAudienceFixture.VALID_OWNER,
                callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_2_AUCTION_SERVER_REQUEST_FLAGS,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudienceFilersDisabled() {
        doReturn(
                        // CHECKSTYLE:OFF IndentationCheck
                        new Flags() {
                            @Override
                            public boolean getFledgeFrequencyCapFilteringEnabled() {
                                return true;
                            }

                            @Override
                            public boolean getFledgeAppInstallFilteringEnabled() {
                                return true;
                            }
                        })
                .when(FlagsFactory::getFlags);
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));

        verify(() -> BackgroundFetchJob.schedule(any()));
    }

    @Test
    public void testLeaveCustomAudienceWithRevokedUserConsentForAppSuccess() {
        doReturn(COMMON_FLAGS_WITH_FILTERS_ENABLED).when(FlagsFactory::getFlags);
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                CUSTOM_AUDIENCE_PK1_1.getName(),
                callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CUSTOM_AUDIENCE_PK1_1.getBuyer(),
                        CUSTOM_AUDIENCE_PK1_1.getName()));
    }

    @Test
    public void testLeaveCustomAudience_leaveNotJoinedCustomAudience_doesNotFail() {
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER_1,
                "Not exist name",
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        VALID_NAME));
    }

    @Test
    public void testScheduleCustomAudienceUpdate_JoinWithOverridesAndLeave_Success()
            throws Exception {
        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(
                                caBlobs.stream()
                                        .map(b -> b.getName())
                                        .collect(Collectors.toList())
                                        .containsAll(List.of(PARTIAL_CA_1, PARTIAL_CA_2)));
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Join a custom audience using the joinCustomAudience API which would be "left" by update
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER)
                        .setName(LEAVE_CA_1)
                        .build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Make a request to the API
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        DBCustomAudience persistedCustomAudience2 =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_2);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience2);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience.getUserBiddingSignals());

        // Leave
        assertNull(
                "The custom audience should have been left",
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, LEAVE_CA_1));

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void
            testScheduleCustomAudienceUpdate_JoinWithOverridesAndLeave_SuccessWithAuctionServerRequestFlags()
                    throws Exception {
        Flags flagsWithAuctionServerRequestFlagsEnabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
                        return true;
                    }

                    @Override
                    public int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
                        // Lets the delay be set in past for easier testing
                        return -100;
                    }
                };

        doReturn(flagsWithAuctionServerRequestFlagsEnabled).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        reInitServiceWithFlags(flagsWithAuctionServerRequestFlagsEnabled);

        mScheduledUpdatesHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                CacheProviderFactory.createNoOpCache()),
                        flagsWithAuctionServerRequestFlagsEnabled,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP,
                        AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                                true, true, true),
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flagsWithAuctionServerRequestFlagsEnabled),
                        mCustomAudienceQuantityChecker);

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ true)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(
                                caBlobs.stream()
                                        .map(b -> b.getName())
                                        .collect(Collectors.toList())
                                        .containsAll(List.of(PARTIAL_CA_1, PARTIAL_CA_2)));
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Join a custom audience using the joinCustomAudience API which would be "left" by update
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER)
                        .setName(LEAVE_CA_1)
                        .build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Make a request to the API
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                persistedCustomAudience.getAuctionServerRequestFlags());
        DBCustomAudience persistedCustomAudience2 =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_2);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience2);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience.getUserBiddingSignals());
        assertEquals(
                FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS,
                persistedCustomAudience2.getAuctionServerRequestFlags());

        // Leave
        assertNull(
                "The custom audience should have been left",
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, LEAVE_CA_1));

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_MultipleUpdates_OnlyFirstUpdateSuccess()
            throws Exception {
        // Wire the mock web server for handling two updates
        String responsePayload1 =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1),
                                List.of(LEAVE_CA_1),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        String responsePayload2 =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_2),
                                List.of(LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        if (request.getPath().equals(UPDATE_URI_PATH)) {
                            List<CustomAudienceBlob> caBlobs =
                                    extractPartialCustomAudiencesFromRequest(request.getBody());
                            assertTrue(
                                    caBlobs.stream()
                                            .map(b -> b.getName())
                                            .collect(Collectors.toList())
                                            .containsAll(List.of(PARTIAL_CA_1)));
                            return new MockResponse().setBody(responsePayload1);
                        } else if (request.getPath().equals(UPDATE_URI_PATH_2)) {
                            List<CustomAudienceBlob> caBlobs =
                                    extractPartialCustomAudiencesFromRequest(request.getBody());
                            assertTrue(
                                    caBlobs.stream()
                                            .map(b -> b.getName())
                                            .collect(Collectors.toList())
                                            .containsAll(List.of(PARTIAL_CA_2)));
                            return new MockResponse().setBody(responsePayload2);
                        }
                        return new MockResponse();
                    }
                };

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri1 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());
        Uri updateUri2 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH_2).toString());

        // Make two requests to the API to schedule updates
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri1,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback1 = new ScheduleUpdateTestCallback(resultLatch1);
        mService.scheduleCustomAudienceUpdate(input1, callback1);
        resultLatch1.await();
        assertTrue(callback1.isSuccess());

        ScheduleCustomAudienceUpdateInput input2 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri2,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch2 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback2 = new ScheduleUpdateTestCallback(resultLatch2);
        mService.scheduleCustomAudienceUpdate(input2, callback2);
        resultLatch2.await();
        assertFalse(callback2.isSuccess());
        assertEquals(
                AdServicesStatusUtils.STATUS_UPDATE_ALREADY_PENDING_ERROR,
                callback2.mFledgeErrorResponse.getStatusCode());
        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience1 =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience1);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience1.getUserBiddingSignals());

        DBCustomAudience persistedCustomAudience2 =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_2);
        assertNull(
                "The second custom audience should not have been joined", persistedCustomAudience2);

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_AppInstallDisabled() throws Exception {
        Flags flagsWithAppInstallDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithAppInstallDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithAppInstallDisabled);

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1),
                                List.of(LEAVE_CA_1),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        if (request.getPath().equals(UPDATE_URI_PATH)) {
                            List<CustomAudienceBlob> caBlobs =
                                    extractPartialCustomAudiencesFromRequest(request.getBody());
                            assertTrue(
                                    caBlobs.stream()
                                            .map(b -> b.getName())
                                            .collect(Collectors.toList())
                                            .containsAll(List.of(PARTIAL_CA_1)));
                            return new MockResponse().setBody(responsePayload);
                        }
                        return new MockResponse();
                    }
                };

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri1 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make two requests to the API to schedule updates
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri1,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback1 = new ScheduleUpdateTestCallback(resultLatch1);
        mService.scheduleCustomAudienceUpdate(input1, callback1);
        resultLatch1.await();
        assertTrue(callback1.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        mScheduledUpdatesHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience.getUserBiddingSignals());

        assertNoAppInstallFilters(persistedCustomAudience);
        verifyFCapFiltersNotNull(persistedCustomAudience);

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_CAQuantityCheckerFail_shouldNotJoinCustomAudience()
            throws Exception {
        Flags flagsWithCAQuantityCheckerFlags =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public long getFledgeCustomAudienceMaxOwnerCount() {
                        return 0;
                    }

                    @Override
                    public long getFledgeCustomAudienceMaxCount() {
                        return 0;
                    }
                };

        doReturn(flagsWithCAQuantityCheckerFlags).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithCAQuantityCheckerFlags);

        mScheduledUpdatesHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                CacheProviderFactory.createNoOpCache()),
                        flagsWithCAQuantityCheckerFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP,
                        AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                                true, true, true),
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flagsWithCAQuantityCheckerFlags),
                        new CustomAudienceQuantityChecker(
                                mCustomAudienceDao, flagsWithCAQuantityCheckerFlags));

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1),
                                List.of(LEAVE_CA_1),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        if (request.getPath().equals(UPDATE_URI_PATH)) {
                            List<CustomAudienceBlob> caBlobs =
                                    extractPartialCustomAudiencesFromRequest(request.getBody());
                            assertTrue(
                                    caBlobs.stream()
                                            .map(b -> b.getName())
                                            .collect(Collectors.toList())
                                            .containsAll(List.of(PARTIAL_CA_1)));
                            return new MockResponse().setBody(responsePayload);
                        }
                        return new MockResponse();
                    }
                };

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri1 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make two requests to the API to schedule updates
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri1,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback1 = new ScheduleUpdateTestCallback(resultLatch1);
        mService.scheduleCustomAudienceUpdate(input1, callback1);
        resultLatch1.await();
        assertTrue(callback1.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        mScheduledUpdatesHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNull("The custom audience should not have been joined", persistedCustomAudience);

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void testScheduleCAUpdate_TwoCAToJoin_CAQuantityCheckerFailForOne_shouldOnlyJoinOneCA()
            throws Exception {
        Flags flagsWithCAQuantityCheckerFlags =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public long getFledgeCustomAudienceMaxOwnerCount() {
                        return 1;
                    }

                    @Override
                    public long getFledgeCustomAudienceMaxCount() {
                        return 1;
                    }

                    @Override
                    public long getFledgeCustomAudiencePerAppMaxCount() {
                        // Lets the delay be set in past for easier testing
                        return 1;
                    }
                };

        doReturn(flagsWithCAQuantityCheckerFlags).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithCAQuantityCheckerFlags);

        mScheduledUpdatesHandler =
                new ScheduledUpdatesHandler(
                        mCustomAudienceDao,
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                CacheProviderFactory.createNoOpCache()),
                        flagsWithCAQuantityCheckerFlags,
                        Clock.systemUTC(),
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesExecutors.getLightWeightExecutor(),
                        FREQUENCY_CAP_AD_DATA_VALIDATOR_NO_OP,
                        RENDER_ID_VALIDATOR_NO_OP,
                        AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                                true, true, true),
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flagsWithCAQuantityCheckerFlags),
                        new CustomAudienceQuantityChecker(
                                mCustomAudienceDao, flagsWithCAQuantityCheckerFlags));

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ true)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(
                                caBlobs.stream()
                                        .map(b -> b.getName())
                                        .collect(Collectors.toList())
                                        .containsAll(List.of(PARTIAL_CA_1, PARTIAL_CA_2)));
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Join a custom audience using the joinCustomAudience API which would be "left" by update
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER)
                        .setName(LEAVE_CA_1)
                        .build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Make a request to the API
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        List<DBCustomAudience> joinedCustomAudiences =
                mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(LOCALHOST_BUYER), FIXED_NOW, 10000);

        assertEquals("Only 1 custom audience should be joined", 1, joinedCustomAudiences.size());
        assertEquals(PARTIAL_CA_1, joinedCustomAudiences.get(0).getName());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_FrequencyCapDisabled() throws Exception {
        Flags flagsWithFrequencyCapDisabled =
                new CustomAudienceServiceE2ETestFlags() {
                    @Override
                    public boolean getFledgeFrequencyCapFilteringEnabled() {
                        return false;
                    }

                    @Override
                    public boolean getFledgeAppInstallFilteringEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithFrequencyCapDisabled).when(FlagsFactory::getFlags);
        reInitServiceWithFlags(flagsWithFrequencyCapDisabled);

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1),
                                List.of(LEAVE_CA_1),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        if (request.getPath().equals(UPDATE_URI_PATH)) {
                            List<CustomAudienceBlob> caBlobs =
                                    extractPartialCustomAudiencesFromRequest(request.getBody());
                            assertTrue(
                                    caBlobs.stream()
                                            .map(b -> b.getName())
                                            .collect(Collectors.toList())
                                            .containsAll(List.of(PARTIAL_CA_1)));
                            return new MockResponse().setBody(responsePayload);
                        }
                        return new MockResponse();
                    }
                };

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri1 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make two requests to the API to schedule updates
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri1,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_2)))
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback1 = new ScheduleUpdateTestCallback(resultLatch1);
        mService.scheduleCustomAudienceUpdate(input1, callback1);
        resultLatch1.await();
        assertTrue(callback1.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        mScheduledUpdatesHandler.performScheduledUpdates(Instant.now()).get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience.getUserBiddingSignals());

        assertNoFCapFilters(persistedCustomAudience);
        verifyAppInstallFiltersNotNull(persistedCustomAudience);

        // Check handled updates are cleared
        assertTrue(
                "The handled updates should have been removed from DB",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_InvalidServerResponses_SilentFailure()
            throws Exception {
        // Wire the mock web server for handling two updates
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // Both update requests do not have valid updates
                        if (request.getPath().equals(UPDATE_URI_PATH)) {
                            new MockResponse().setResponseCode(400);
                        } else if (request.getPath().equals(UPDATE_URI_PATH_2)) {
                            new MockResponse().setResponseCode(200).setBody("{}");
                        }
                        return new MockResponse();
                    }
                };

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri1 = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make two requests to the API to schedule updates
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input1 =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri1,
                                VALID_OWNER,
                                negativeDelayForTest,
                                Collections.emptyList())
                        .build();
        CountDownLatch resultLatch1 = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback1 = new ScheduleUpdateTestCallback(resultLatch1);
        mService.scheduleCustomAudienceUpdate(input1, callback1);
        resultLatch1.await();
        assertTrue(callback1.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully but nothing updated
        // Join
        assertEquals(
                "No custom audience should have been joined",
                0,
                mCustomAudienceDao.getCustomAudienceCount());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_PartialCaOverrides_Success() throws Exception {
        // A Custom Audience which has no corresponding partial CA
        String nonOverriddenCaName = "non_overridden_ca";

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(nonOverriddenCaName, PARTIAL_CA_1),
                                List.of(LEAVE_CA_1),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(
                                caBlobs.stream()
                                        .map(b -> b.getName())
                                        .collect(Collectors.toList())
                                        .containsAll(List.of(PARTIAL_CA_1)));
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make a request to the API
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1)))
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        assertEquals(
                "The signals should have been overridden from partial custom audience",
                VALID_BIDDING_SIGNALS,
                persistedCustomAudience.getUserBiddingSignals());

        DBCustomAudience nonOverriddenCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, nonOverriddenCaName);
        assertEquals(
                "Bidding Signals should not have been overridden",
                AdSelectionSignals.EMPTY.toString(),
                nonOverriddenCustomAudience.getUserBiddingSignals().toString());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_InvalidPartialCa_PartialSuccess()
            throws Exception {

        // Create a partial CA with invalid activation time
        DBPartialCustomAudience invalidPartialCustomAudience2 =
                DBPartialCustomAudience.builder()
                        .setUpdateId(UPDATE_ID)
                        .setName(PARTIAL_CA_2)
                        .setActivationTime(ACTIVATION_TIME)
                        .setExpirationTime(ACTIVATION_TIME.minus(20, ChronoUnit.DAYS))
                        .setUserBiddingSignals(VALID_BIDDING_SIGNALS)
                        .build();

        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(
                                caBlobs.stream()
                                        .map(b -> b.getName())
                                        .collect(Collectors.toList())
                                        .containsAll(List.of(PARTIAL_CA_1)));
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Make a request to the API
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                List.of(
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                PARTIAL_CUSTOM_AUDIENCE_1),
                                        DBPartialCustomAudience.getPartialCustomAudience(
                                                invalidPartialCustomAudience2)))
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);

        // Invalid CA should not have been joined
        DBCustomAudience persistedCustomAudience2 =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_2);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience2);
        assertEquals(
                "Invalid override should not have been applied",
                AdSelectionSignals.EMPTY.toString(),
                persistedCustomAudience2.getUserBiddingSignals().toString());
    }

    @Test
    public void testScheduleCustomAudienceUpdate_NoOverrides_Success() throws Exception {
        // Wire the mock web server
        String responsePayload =
                createJsonResponsePayload(
                                LOCALHOST_BUYER,
                                VALID_OWNER,
                                List.of(PARTIAL_CA_1, PARTIAL_CA_2),
                                List.of(LEAVE_CA_1, LEAVE_CA_2),
                                /* auctionServerRequestFlagsEnabled= */ false)
                        .toString();

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        // We can validate the request within server
                        List<CustomAudienceBlob> caBlobs =
                                extractPartialCustomAudiencesFromRequest(request.getBody());
                        assertTrue(caBlobs.isEmpty());
                        return new MockResponse().setBody(responsePayload);
                    }
                };
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(dispatcher);

        Uri updateUri = Uri.parse(mockWebServer.getUrl(UPDATE_URI_PATH).toString());

        // Join a custom audience using the joinCustomAudience API which would be "left" by update
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mService.joinCustomAudience(
                CustomAudienceFixture.getValidBuilderForBuyerFilters(LOCALHOST_BUYER)
                        .setName(LEAVE_CA_1)
                        .build(),
                CustomAudienceFixture.VALID_OWNER,
                joinCallback);
        assertTrue(joinCallback.mIsSuccess);

        // Make a request to the API with empty overrides
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                Collections.EMPTY_LIST)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now())
                        .get(10, TimeUnit.SECONDS);

        // Check that the request for updates was made to server successfully
        assertEquals(1, mockWebServer.getRequestCount());

        // Check that updates processed successfully
        // Join
        DBCustomAudience persistedCustomAudience =
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, PARTIAL_CA_1);
        assertNotNull("The custom audience should have been joined", persistedCustomAudience);
        assertEquals(
                "Bidding signals should not have been overridden due to empty overrides",
                AdSelectionSignals.EMPTY.toString(),
                persistedCustomAudience.getUserBiddingSignals().toString());

        // Leave
        assertNull(
                "The custom audience should have been left",
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        VALID_OWNER, LOCALHOST_BUYER, LEAVE_CA_1));
    }

    @Test
    public void testScheduleCustomAudienceUpdate_RemoveStaleUpdates_Success() throws Exception {

        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(Collections.EMPTY_LIST);
        Uri updateUri = mMockWebServerRule.uriForPath(UPDATE_URI_PATH);

        // Make a request to the API with empty overrides
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                Collections.EMPTY_LIST)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());

        // Ensure that job that maintains update-scheduled is itself scheduled
        verify(
                () ->
                        ScheduleCustomAudienceUpdateJobService.scheduleIfNeeded(
                                any(), any(), eq(false)),
                times(1));

        assertTrue(
                "Update should have been scheduled and persisted",
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledBeforeTime(Instant.now()).size()
                        > 0);

        // Manually trigger handler as it would have been triggered by its job schedule, but in far
        // future so that updates become stale by that time
        Void unused =
                mScheduledUpdatesHandler
                        .performScheduledUpdates(Instant.now().plus(20, ChronoUnit.DAYS))
                        .get(10, TimeUnit.SECONDS);

        // Check that no request for updates was made to server
        assertEquals(0, mockWebServer.getRequestCount());

        // Check that update handler deleted the stale update
        assertTrue(
                "Stale updates should have been deleted",
                mCustomAudienceDao
                        .getCustomAudienceUpdatesScheduledBeforeTime(Instant.now())
                        .isEmpty());

        // Empty Join
        assertEquals(
                "No custom audience should have been joined",
                0,
                mCustomAudienceDao.getCustomAudienceCount());
    }

    @Test
    public void testScheduleCAUpdate_withPendingUpdatesAndRemoveUpdatesFalse_fails()
            throws Exception {
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(Collections.EMPTY_LIST);
        Uri updateUri = mMockWebServerRule.uriForPath(UPDATE_URI_PATH);

        DBScheduledCustomAudienceUpdate oldScheduledCA =
                DBScheduledCustomAudienceUpdate.builder()
                        .setBuyer(LOCALHOST_BUYER)
                        .setOwner(VALID_OWNER)
                        .setUpdateUri(updateUri)
                        .setScheduledTime(
                                CommonFixture.FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .setCreationTime(CommonFixture.FIXED_NOW.truncatedTo(ChronoUnit.MILLIS))
                        .setUpdateId(null)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(oldScheduledCA);

        // Make a request to the API with empty overrides
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                Collections.EMPTY_LIST)
                        .setShouldReplacePendingUpdates(false)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is unsuccessful.
        assertFalse(callback.isSuccess());
        assertEquals(
                AdServicesStatusUtils.STATUS_UPDATE_ALREADY_PENDING_ERROR,
                callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testScheduleCAUpdate_withPendingUpdatesAndRemovePendingTrue_replacesPendingUpdates()
            throws Exception {
        MockWebServer mockWebServer = mMockWebServerRule.startMockWebServer(Collections.EMPTY_LIST);
        Uri updateUri = mMockWebServerRule.uriForPath(UPDATE_URI_PATH);

        DBScheduledCustomAudienceUpdate oldScheduledCA =
                DBScheduledCustomAudienceUpdate.builder()
                        .setBuyer(LOCALHOST_BUYER)
                        .setOwner(VALID_OWNER)
                        .setUpdateUri(updateUri)
                        .setScheduledTime(
                                CommonFixture.FIXED_NEXT_ONE_DAY.truncatedTo(ChronoUnit.MILLIS))
                        .setCreationTime(CommonFixture.FIXED_NOW.truncatedTo(ChronoUnit.MILLIS))
                        .setUpdateId(null)
                        .build();
        mCustomAudienceDao.insertScheduledCustomAudienceUpdate(oldScheduledCA);

        // Make a request to the API with empty overrides
        Duration negativeDelayForTest = Duration.of(-20, ChronoUnit.MINUTES);
        ScheduleCustomAudienceUpdateInput input =
                new ScheduleCustomAudienceUpdateInput.Builder(
                                updateUri,
                                VALID_OWNER,
                                negativeDelayForTest,
                                Collections.EMPTY_LIST)
                        .setShouldReplacePendingUpdates(true)
                        .build();
        CountDownLatch resultLatch = new CountDownLatch(1);
        ScheduleUpdateTestCallback callback = new ScheduleUpdateTestCallback(resultLatch);
        mService.scheduleCustomAudienceUpdate(input, callback);
        resultLatch.await();

        // Validate response of API is complete
        assertTrue(callback.isSuccess());
        List<DBScheduledCustomAudienceUpdate> newScheduledCAUpdates =
                mCustomAudienceDao.getCustomAudienceUpdatesScheduledByOwner(VALID_OWNER);
        expect.that(newScheduledCAUpdates).doesNotContain(oldScheduledCA);
        assertEquals(1, newScheduledCAUpdates.size());
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        MY_APP_PACKAGE_NAME,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        MY_APP_PACKAGE_NAME,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoDoesNotAddOverrideWithPackageNameNotMatchOwner()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        MY_APP_PACKAGE_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        String otherOwner = "otherOwner";

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        otherOwner,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(otherOwner, BUYER_1, NAME_1));

        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        MY_APP_PACKAGE_NAME,
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_CUSTOM_AUDIENCE_REMOTE_INFO,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoFailsWithDevOptionsDisabled() {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverride(
                                MY_APP_PACKAGE_NAME,
                                BUYER_1,
                                NAME_1,
                                BIDDING_LOGIC_JS,
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                                TRUSTED_BIDDING_DATA,
                                mService));

        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(incorrectPackageName)
                                .setDevOptionsEnabled(true)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__REMOVE_CUSTOM_AUDIENCE_REMOTE_INFO_OVERRIDE,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideFailsWithDevOptionsDisabled() {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesWithRevokedUserConsentSuccess()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(MY_APP_PACKAGE_NAME).setDevOptionsEnabled(true).build());
        doReturn(true).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder(incorrectPackageName)
                                .setDevOptionsEnabled(true)
                                .build());
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredPermission(
                        mContext,
                        incorrectPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_CUSTOM_AUDIENCE_OVERRIDES,
                        AdServicesPermissions.ACCESS_ADSERVICES_CUSTOM_AUDIENCE);
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesFailsWithDevOptionsDisabled() {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1)
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2)
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));

        assertThrows(SecurityException.class, () -> callResetAllOverrides(mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1, NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2, NAME_2));
    }

    @Test
    public void testCustomAudience_throttledSubsequentCallFails() {
        class FlagsWithLowRateLimit implements Flags {
            @Override
            public boolean getDisableFledgeEnrollmentCheck() {
                return true;
            }

            @Override
            public boolean getFledgeFrequencyCapFilteringEnabled() {
                return true;
            }

            @Override
            public boolean getFledgeAppInstallFilteringEnabled() {
                return true;
            }

            @Override
            public float getSdkRequestPermitsPerSecond() {
                return 1f;
            }

            @Override
            public String getPpapiAppAllowList() {
                return MY_APP_PACKAGE_NAME;
            }
        }

        Flags flagsWithLowRateLimit = new FlagsWithLowRateLimit();

        doReturn(flagsWithLowRateLimit).when(FlagsFactory::getFlags);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        Throttler.destroyExistingThrottler();
        try {
            CustomAudienceServiceImpl customAudienceService =
                    mService =
                            new CustomAudienceServiceImpl(
                                    mContext,
                                    new CustomAudienceImpl(
                                            mCustomAudienceDao,
                                            mCustomAudienceQuantityCheckerMock,
                                            mCustomAudienceValidatorMock,
                                            CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                            flagsWithLowRateLimit),
                                    new FledgeAuthorizationFilter(
                                            mContext.getPackageManager(),
                                            EnrollmentDao.getInstance(),
                                            mAdServicesLoggerMock),
                                    mConsentManagerMock,
                                    mDevContextFilter,
                                    MoreExecutors.newDirectExecutorService(),
                                    mAdServicesLoggerMock,
                                    mAppImportanceFilter,
                                    flagsWithLowRateLimit,
                                    CallingAppUidSupplierProcessImpl.create(),
                                    new CustomAudienceServiceFilter(
                                            mContext,
                                            mFledgeConsentFilterMock,
                                            flagsWithLowRateLimit,
                                            mAppImportanceFilter,
                                            new FledgeAuthorizationFilter(
                                                    mContext.getPackageManager(),
                                                    EnrollmentDao.getInstance(),
                                                    mAdServicesLoggerMock),
                                            new FledgeAllowListsFilter(
                                                    flagsWithLowRateLimit, mAdServicesLoggerMock),
                                            new FledgeApiThrottleFilter(
                                                    Throttler.getInstance(flagsWithLowRateLimit),
                                                    mAdServicesLoggerMock)),
                                    new AdFilteringFeatureFactory(
                                            mAppInstallDao,
                                            mFrequencyCapDao,
                                            flagsWithLowRateLimit));

            // The first call should succeed
            ResultCapturingCallback callbackFirstCall = new ResultCapturingCallback();
            customAudienceService.joinCustomAudience(
                    CUSTOM_AUDIENCE_PK1_1, CustomAudienceFixture.VALID_OWNER, callbackFirstCall);

            // The immediate subsequent call should be throttled
            ResultCapturingCallback callbackSubsequentCall = new ResultCapturingCallback();
            customAudienceService.joinCustomAudience(
                    CUSTOM_AUDIENCE_PK1_1,
                    CustomAudienceFixture.VALID_OWNER,
                    callbackSubsequentCall);

            assertWithMessage("First callback success")
                    .that(callbackFirstCall.isSuccess())
                    .isTrue();
            assertWithMessage("Inserted CA")
                    .that(
                            mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                                    CustomAudienceFixture.VALID_OWNER,
                                    CommonFixture.VALID_BUYER_1,
                                    VALID_NAME))
                    .isEqualTo(DB_CUSTOM_AUDIENCE_PK1_1);

            assertWithMessage("Second callback success")
                    .that(callbackSubsequentCall.isSuccess())
                    .isFalse();
            assertWithMessage("Second callback exception")
                    .that(callbackSubsequentCall.getException())
                    .isInstanceOf(LimitExceededException.class);
            assertWithMessage("Second callback exception")
                    .that(callbackSubsequentCall.getException())
                    .hasMessageThat()
                    .isEqualTo(AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE);
        } finally {
            resetThrottlerToNoRateLimits();
        }
    }

    /**
     * Given Throttler is singleton, & shared across tests, this method should be invoked after
     * tests that impose restrictive rate limits.
     */
    private void resetThrottlerToNoRateLimits() {
        Throttler.destroyExistingThrottler();
        final float noRateLimit = -1;
        Flags mockNoRateLimitFlags = mock(Flags.class);
        doReturn(noRateLimit).when(mockNoRateLimitFlags).getSdkRequestPermitsPerSecond();
        Throttler.getInstance(mockNoRateLimitFlags);
    }

    private void verifyFCapFiltersNotNull(DBCustomAudience dbCustomAudience) {
        for (int i = 0; i < dbCustomAudience.getAds().size(); i++) {
            if (dbCustomAudience.getAds().get(i).getAdFilters() != null) {
                assertThat(dbCustomAudience.getAds().get(i).getAdFilters().getFrequencyCapFilters())
                        .isNotNull();
            }
        }
    }

    private void verifyAppInstallFiltersNotNull(DBCustomAudience dbCustomAudience) {
        for (int i = 0; i < dbCustomAudience.getAds().size(); i++) {
            if (dbCustomAudience.getAds().get(i).getAdFilters() != null) {
                assertThat(dbCustomAudience.getAds().get(i).getAdFilters().getAppInstallFilters())
                        .isNotNull();
            }
        }
    }

    private void assertNoFCapFilters(DBCustomAudience customAudience) {
        for (DBAdData ad : customAudience.getAds()) {
            if (ad.getAdFilters() != null) {
                assertThat(ad.getAdFilters().getFrequencyCapFilters()).isNull();
            }
        }
    }

    private void assertNoAppInstallFilters(DBCustomAudience customAudience) {
        for (DBAdData ad : customAudience.getAds()) {
            if (ad.getAdFilters() != null) {
                assertThat(ad.getAdFilters().getAppInstallFilters()).isNull();
            }
        }
    }

    private CustomAudienceOverrideTestCallback callAddOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            Long biddingLogicJsVersion,
            AdSelectionSignals trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner,
                buyer,
                name,
                biddingLogicJs,
                biddingLogicJsVersion,
                trustedBiddingData,
                callback);
        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callRemoveOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.removeCustomAudienceRemoteInfoOverride(owner, buyer, name, callback);

        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callResetAllOverrides(
            CustomAudienceServiceImpl customAudienceService) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.resetAllCustomAudienceOverrides(callback);
        resultLatch.await();
        return callback;
    }

    public static class CustomAudienceOverrideTestCallback
            extends CustomAudienceOverrideCallback.Stub {
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;
        private final CountDownLatch mCountDownLatch;

        public CustomAudienceOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = AdServicesStatusUtils.asException(responseParcel);
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    private void reInitServiceWithFlags(Flags flags) {
        mService =
                new CustomAudienceServiceImpl(
                        mContext,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                mCustomAudienceQuantityChecker,
                                mCustomAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flags),
                        mFledgeAuthorizationFilterSpy,
                        mConsentManagerMock,
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLoggerMock,
                        mAppImportanceFilter,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                mContext,
                                mFledgeConsentFilterMock,
                                flags,
                                mAppImportanceFilter,
                                new FledgeAuthorizationFilter(
                                        mContext.getPackageManager(),
                                        EnrollmentDao.getInstance(),
                                        mAdServicesLoggerMock),
                                new FledgeAllowListsFilter(flags, mAdServicesLoggerMock),
                                mFledgeApiThrottleFilterMock),
                        new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, flags));
    }

    private static class CustomAudienceServiceE2ETestFlags implements Flags {
        // Using tolerant timeouts for tests to avoid flakiness.
        // Tests that need to validate timeout behaviours will override these values too.
        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return 600000;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeFrequencyCapFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAppInstallFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeScheduleCustomAudienceUpdateEnabled() {
            return true;
        }

        @Override
        public int getFledgeScheduleCustomAudienceMinDelayMinsOverride() {
            // Lets the delay be set in past for easier testing
            return -100;
        }

        @Override
        public boolean getMeasurementFlexibleEventReportingApiEnabled() {
            return true;
        }

        @Override
        public boolean getEnableLoggedTopic() {
            return true;
        }

        @Override
        public boolean getEnableDatabaseSchemaVersion8() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeBeaconReportingMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAppPackageNameLoggingEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerKeyFetchMetricsEnabled() {
            return true;
        }

        @Override
        public boolean getPasExtendedMetricsEnabled() {
            return true;
        }

        @Override
        public String getPpapiAppAllowList() {
            return MY_APP_PACKAGE_NAME;
        }
    }

    private Flags getFlagsWithAuctionServerRequestFlagsEnabled() {
        return new CustomAudienceServiceE2ETestFlags() {
            @Override
            public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                return true;
            }
        };
    }
}