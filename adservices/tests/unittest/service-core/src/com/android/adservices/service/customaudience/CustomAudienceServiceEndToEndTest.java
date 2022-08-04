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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;

import java.util.concurrent.CountDownLatch;

public class CustomAudienceServiceEndToEndTest {
    protected static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_1 =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER).build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_2 =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final CustomAudience CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME =
            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                    .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                    .build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER).build();

    private static final DBCustomAudience DB_CUSTOM_AUDIENCE_PK1_2 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER)
                    .setActivationTime(CustomAudienceFixture.VALID_DELAYED_ACTIVATION_TIME)
                    .setExpirationTime(CustomAudienceFixture.VALID_DELAYED_EXPIRATION_TIME)
                    .build();

    private static final String MY_APP_PACKAGE_NAME = "com.google.ppapi.test";
    private static final AdTechIdentifier BUYER_1 = AdTechIdentifier.fromString("BUYER_1");
    private static final AdTechIdentifier BUYER_2 = AdTechIdentifier.fromString("BUYER_1");
    private static final String NAME_1 = "NAME_1";
    private static final String NAME_2 = "NAME_2";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private CustomAudienceDao mCustomAudienceDao;

    private CustomAudienceServiceImpl mService;

    private MockitoSession mStaticMockSession = null;

    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilter;
    private final AdServicesLogger mAdServicesLogger = AdServicesLoggerImpl.getInstance();

    @Before
    public void setup() {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(Binder.class)
                        .initMocks(this)
                        .startMocking();

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .build()
                        .customAudienceDao();

        CustomAudienceQuantityChecker customAudienceQuantityChecker =
                new CustomAudienceQuantityChecker(mCustomAudienceDao, CommonFixture.FLAGS_FOR_TEST);

        CustomAudienceValidator customAudienceValidator =
                new CustomAudienceValidator(
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI, CommonFixture.FLAGS_FOR_TEST);

        mService =
                new CustomAudienceServiceImpl(
                        CONTEXT,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                customAudienceQuantityChecker,
                                customAudienceValidator,
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                CommonFixture.FLAGS_FOR_TEST),
                        new FledgeAuthorizationFilter(
                                CONTEXT.getPackageManager(),
                                EnrollmentDao.getInstance(CONTEXT),
                                mAdServicesLogger),
                        mDevContextFilter,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLogger);
    }

    @After
    public void teardown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testJoinCustomAudience_notInBinderThread_fail() {
        when(Binder.getCallingUidOrThrow()).thenThrow(IllegalStateException.class);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_1, callback);
                });
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_notAuthorized_fail() {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                SecurityException.class,
                () -> {
                    mService.joinCustomAudience(
                            CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER)
                                    .setOwner("other_owner")
                                    .build(),
                            callback);
                });
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_joinTwice_secondJoinOverrideValues() {
        doReturn(CommonFixture.FLAGS_FOR_TEST).when(FlagsFactory::getFlags);
        doReturn(Process.myUid()).when(Binder::getCallingUidOrThrow);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_1, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_2, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_2,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testJoinCustomAudience_beyondMaxExpirationTime_fail() {
        doReturn(Process.myUid()).when(Binder::getCallingUidOrThrow);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_BEYOND_MAX_EXPIRATION_TIME, callback);
        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof AdServicesException);
        assertTrue(callback.getException().getCause() instanceof IllegalArgumentException);
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_notInBinderThread_fail() {
        when(Binder.getCallingUidOrThrow()).thenThrow(IllegalStateException.class);

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                IllegalStateException.class,
                () -> {
                    mService.leaveCustomAudience(
                            CustomAudienceFixture.VALID_OWNER,
                            CommonFixture.VALID_BUYER,
                            CustomAudienceFixture.VALID_NAME,
                            callback);
                });
    }

    @Test
    public void testLeaveCustomAudience_notAuthorized_fail() {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        assertThrows(
                SecurityException.class,
                () -> {
                    mService.leaveCustomAudience(
                            "other_owner",
                            CommonFixture.VALID_BUYER,
                            CustomAudienceFixture.VALID_NAME,
                            callback);
                });
    }

    @Test
    public void testLeaveCustomAudience_leaveJoinedCustomAudience() {
        doReturn(CommonFixture.FLAGS_FOR_TEST).when(FlagsFactory::getFlags);
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());

        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.joinCustomAudience(CUSTOM_AUDIENCE_PK1_1, callback);
        assertTrue(callback.isSuccess());
        assertEquals(
                DB_CUSTOM_AUDIENCE_PK1_1,
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));

        callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                CustomAudienceFixture.VALID_NAME,
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testLeaveCustomAudience_leaveNotJoinedCustomAudience_doesNotFail() {
        when(Binder.getCallingUidOrThrow()).thenReturn(Process.myUid());
        ResultCapturingCallback callback = new ResultCapturingCallback();
        mService.leaveCustomAudience(
                CustomAudienceFixture.VALID_OWNER,
                CommonFixture.VALID_BUYER,
                "Not exist name",
                callback);
        assertTrue(callback.isSuccess());
        assertNull(
                mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER.toString(),
                        CustomAudienceFixture.VALID_NAME));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        MY_APP_PACKAGE_NAME,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoDoesNotAddOverrideWithPackageNameNotMatchOwner()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        String otherOwner = "otherOwner";

        CustomAudienceOverrideTestCallback callback =
                callAddOverride(
                        otherOwner,
                        BUYER_1,
                        NAME_1,
                        BIDDING_LOGIC_JS,
                        TRUSTED_BIDDING_DATA,
                        mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        otherOwner, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testOverrideCustomAudienceRemoteInfoFailsWithDevOptionsDisabled() throws Exception {
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
                                TRUSTED_BIDDING_DATA,
                                mService));

        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));

        CustomAudienceOverrideTestCallback callback =
                callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testRemoveCustomAudienceRemoteInfoOverrideFailsWithDevOptionsDisabled()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(MY_APP_PACKAGE_NAME, BUYER_1, NAME_1, mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesSuccess() throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2.toString())
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
        assertFalse(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        String incorrectPackageName = "incorrectPackageName";

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2.toString())
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));

        CustomAudienceOverrideTestCallback callback = callResetAllOverrides(mService);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
    }

    @Test
    public void testResetAllCustomAudienceRemoteOverridesFailsWithDevOptionsDisabled()
            throws Exception {
        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_1.toString())
                        .setName(NAME_1)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(MY_APP_PACKAGE_NAME)
                        .setBuyer(BUYER_2.toString())
                        .setName(NAME_2)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA.toString())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));

        assertThrows(SecurityException.class, () -> callResetAllOverrides(mService));

        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_1.toString(), NAME_1));
        assertTrue(
                mCustomAudienceDao.doesCustomAudienceOverrideExist(
                        MY_APP_PACKAGE_NAME, BUYER_2.toString(), NAME_2));
    }

    private CustomAudienceOverrideTestCallback callAddOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            AdSelectionSignals trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner, buyer, name, biddingLogicJs, trustedBiddingData, callback);
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
            mException = responseParcel.asException();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }
}
