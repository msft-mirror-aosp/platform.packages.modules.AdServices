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
package com.android.adservices.tests.ui.common;

import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesCommonStates;
import android.adservices.common.AdServicesCommonStatesResponse;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.AdServicesStates;
import android.adservices.common.ConsentStatus;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.platform.test.rule.ScreenRecordRule;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.tests.ui.libs.AdservicesWorkflows;
import com.android.adservices.tests.ui.libs.UiConstants.UX;
import com.android.adservices.tests.ui.libs.UiUtils;
import com.android.adservices.tests.ui.libs.pages.NotificationPages;
import com.android.adservices.tests.ui.libs.pages.SettingsPages;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Test for get adservices common states. */
@ScreenRecordRule.ScreenRecord
public final class CommonServicesTest extends AdServicesCommonStatesServicesTestCase {
    private AdServicesCommonManager mCommonManager;

    private UiDevice mDevice;

    private String mTestName;

    private OutcomeReceiver<Boolean, Exception> mCallback;

    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String TEST_PACKAGE_NAME = "com.android.adservices.tests.ui.common";
    private static final String INVALID_PACKAGE_NAME = "invalidPackage";

    @Rule public final ScreenRecordRule sScreenRecordRule = new ScreenRecordRule();

    @Before
    public void setUp() throws Exception {
        mTestName = getTestName();

        UiUtils.setBinderTimeout(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);
        UiUtils.resetAdServicesConsentData(mContext, flags);
        UiUtils.enableNotificationPermission();
        UiUtils.enableGa(flags);
        UiUtils.enablePas(flags);
        UiUtils.enableU18(flags);
        UiUtils.setFlipFlow(flags, true);
        UiUtils.disableOtaStrings(flags);
        UiUtils.setGetAdservicesCommonStatesServiceEnable(flags, true);
        UiUtils.setGetAdservicesCommonStatesAllowList(flags, TEST_PACKAGE_NAME);

        mDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());

        mCommonManager = AdServicesCommonManager.get(mContext);

        // General purpose callback used for expected success calls.
        mCallback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        assertThat(result).isTrue();
                    }

                    @Override
                    public void onError(Exception exception) {
                        Assert.fail();
                    }
                };

        // Reset consent and thereby AdServices data before each test.
        UiUtils.refreshConsentResetToken(flags);

        SettableFuture<Boolean> responseFuture = SettableFuture.create();

        mCommonManager.enableAdServices(
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build(),
                Executors.newCachedThreadPool(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        responseFuture.set(result);
                    }

                    @Override
                    public void onError(Exception exception) {
                        responseFuture.setException(exception);
                    }
                });

        Boolean response = responseFuture.get();
        assertThat(response).isTrue();

        mDevice.pressHome();
    }

    @After
    public void tearDown() throws Exception {
        UiUtils.takeScreenshot(mDevice, getClass().getSimpleName() + "_" + mTestName + "_");
        UiUtils.disablePas(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);
    }

    /** Verify that for GA, ROW devices get adservices common states of opt-in consent. */
    @Test
    public void testGetAdservicesCommonStatesOptIn() throws Exception {
        UiUtils.setAsRowDevice(flags);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        // Set consents to true.
        AdservicesWorkflows.testSettingsPageFlow(
                mContext,
                mDevice,
                flags,
                /* ux type */ UX.GA_UX,
                /* consent opt-in */ true,
                /* flip consent */ false,
                /* assert consent */ true);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        // Trigger Pas re-notify notification.
        NotificationPages.verifyNotification(
                mContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* ux type */ UX.GA_UX,
                /* isFlipFlow */ true,
                /* isPas */ true,
                /* isPasRenotify */ true);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.GIVEN);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.GIVEN);
    }

    /** Verify that for GA devices get adservices common states of opt-out consent. */
    @Test
    public void testGetAdservicesCommonStatesOptOut() throws Exception {
        UiUtils.setFlipFlow(flags, true);
        UiUtils.setAsRowDevice(flags);
        UiUtils.setGetAdservicesCommonStatesServiceEnable(flags, true);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        // Set consents to true.
        AdservicesWorkflows.testSettingsPageFlow(
                mContext,
                mDevice,
                flags,
                /* ux type */ UX.GA_UX,
                /* consent opt-in */ true,
                /* flip consent */ false,
                /* assert consent */ true);

        AdServicesStates adServicesStates =
                new AdServicesStates.Builder()
                        .setAdIdEnabled(true)
                        .setAdultAccount(true)
                        .setPrivacySandboxUiEnabled(true)
                        .build();

        mCommonManager.enableAdServices(
                adServicesStates, Executors.newCachedThreadPool(), mCallback);

        AdservicesWorkflows.testClickNotificationFlow(
                mContext,
                mDevice,
                /* isDisplayed */ true,
                /* isEuTest */ false,
                /* ux type */ UX.GA_UX,
                /* isFlipFlow */ true,
                /* consent opt-in */ true,
                /* isPas */ true,
                /* isPasRenotify */ true);

        // Set consents to false.
        SettingsPages.testSettingsPageConsents(
                mContext,
                mDevice,
                /* ux type */ UX.GA_UX,
                /* consent opt-in */ false,
                /* flip consent */ false,
                /* assert consent */ false);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.REVOKED);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.REVOKED);
    }

    /** Verify that for GA, ROW devices get adservices common states of opt-in consent. */
    @Test
    public void testGetAdservicesCommonStatesNotEnabled() throws Exception {
        UiUtils.setAsRowDevice(flags);
        UiUtils.setGetAdservicesCommonStatesServiceEnable(flags, false);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        AdServicesCommonStates commonStates =
                adServicesCommonStatesResponse.get().getAdServicesCommonStates();
        assertThat(commonStates.getMeasurementState()).isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
        assertThat(commonStates.getPaState()).isEqualTo(ConsentStatus.SERVICE_NOT_ENABLED);
    }

    @Test
    public void testGetAdservicesCommonStatesNotAllowed() throws Exception {
        UiUtils.setAsRowDevice(flags);
        UiUtils.setGetAdservicesCommonStatesServiceEnable(flags, false);
        UiUtils.setGetAdservicesCommonStatesAllowList(flags, INVALID_PACKAGE_NAME);
        AdservicesTestHelper.killAdservicesProcess(mContext);

        ListenableFuture<AdServicesCommonStatesResponse> adServicesCommonStatesResponse =
                getAdservicesCommonStates();

        Exception e = Assert.assertThrows(Exception.class, adServicesCommonStatesResponse::get);
        assertThat("class " + e.getMessage())
                .isEqualTo(
                        e.getCause().getClass()
                                + ": "
                                + SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
    }

    private ListenableFuture<AdServicesCommonStatesResponse> getAdservicesCommonStates() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.getAdservicesCommonStates(
                            CALLBACK_EXECUTOR,
                            new AdServicesOutcomeReceiver<>() {
                                @Override
                                public void onResult(AdServicesCommonStatesResponse result) {
                                    completer.set(result);
                                }

                                @Override
                                public void onError(Exception error) {
                                    completer.setException(error);
                                }
                            });
                    // This value is used only for debug purposes: it will be used in toString()
                    // of returned future or error cases.
                    return "getStatus";
                });
    }

    @Test
    public void testAdservicesCommonStatesCoverages() {
        AdServicesCommonStates states =
                new AdServicesCommonStates.Builder()
                        .setMeasurementState(ConsentStatus.GIVEN)
                        .setPaState(ConsentStatus.REVOKED)
                        .build();

        Parcel parcel = Parcel.obtain();

        try {
            states.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            AdServicesCommonStates createdParams =
                    AdServicesCommonStates.CREATOR.createFromParcel(parcel);
            assertThat(createdParams.describeContents()).isEqualTo(0);
            assertThat(createdParams).isNotSameInstanceAs(states);
            assertThat(createdParams.getPaState()).isEqualTo(states.getPaState());
            assertThat(createdParams.getMeasurementState()).isEqualTo(states.getMeasurementState());
            assertThat(createdParams.equals(states)).isTrue();
            assertThat(createdParams.hashCode()).isEqualTo(states.hashCode());
            assertThat(createdParams.toString()).isEqualTo(states.toString());
        } finally {
            parcel.recycle();
        }
    }

    @Test
    public void testAdservicesCommonStatesResponseCoverages() {
        AdServicesCommonStates states =
                new AdServicesCommonStates.Builder()
                        .setMeasurementState(ConsentStatus.GIVEN)
                        .setPaState(ConsentStatus.REVOKED)
                        .build();
        AdServicesCommonStatesResponse response =
                new AdServicesCommonStatesResponse.Builder(states)
                        .setAdservicesCommonStates(states)
                        .build();

        Parcel parcel = Parcel.obtain();

        try {
            response.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);

            AdServicesCommonStatesResponse createdParams =
                    AdServicesCommonStatesResponse.CREATOR.createFromParcel(parcel);
            assertThat(createdParams.describeContents()).isEqualTo(0);
            assertThat(createdParams).isNotSameInstanceAs(response);
            assertThat(createdParams.getAdServicesCommonStates().getPaState())
                    .isEqualTo(states.getPaState());
            assertThat(createdParams.getAdServicesCommonStates().getMeasurementState())
                    .isEqualTo(states.getMeasurementState());
            assertThat(createdParams.toString()).isEqualTo(response.toString());
        } finally {
            parcel.recycle();
        }
    }
}
