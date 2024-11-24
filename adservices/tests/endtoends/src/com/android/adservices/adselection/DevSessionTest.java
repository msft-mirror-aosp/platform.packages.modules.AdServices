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

package com.android.adservices.adselection;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.signals.ProtectedSignalsClient;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest;
import android.adservices.signals.UpdateSignalsRequest;
import android.net.Uri;

import com.android.adservices.AdServicesEndToEndTestCase;
import com.android.adservices.LoggerFactory;
import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastT;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/** Tests to ensure correct behaviour (rejection) from a non-debuggable app during a dev session. */
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_DEVELOPER_SESSION_FEATURE_ENABLED)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED)
@SetFlagDisabled(KEY_FLEDGE_AUCTION_SERVER_KILL_SWITCH)
@EnableAllApis
@SetCompatModeFlags
@RequiresSdkLevelAtLeastT
public final class DevSessionTest extends AdServicesEndToEndTestCase {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();
    private final AdvertisingCustomAudienceClient mCustomAudienceClient =
            new AdvertisingCustomAudienceClient.Builder()
                    .setContext(mContext)
                    .setExecutor(AdServicesExecutors.getLightWeightExecutor())
                    .build();
    private final AdSelectionClient mAdSelectionClient =
            new AdSelectionClient.Builder()
                    .setContext(mContext)
                    .setExecutor(AdServicesExecutors.getLightWeightExecutor())
                    .build();
    private final ProtectedSignalsClient mProtectedSignalsClient =
            new ProtectedSignalsClient.Builder()
                    .setContext(mContext)
                    .setExecutor(AdServicesExecutors.getLightWeightExecutor())
                    .build();

    private static final long VALID_AD_SELECTION_ID = 12L;
    private static final AdTechIdentifier AD_TECH = AdTechIdentifier.fromString("localhost");
    private static final long TIMEOUT_SEC = 5;

    @Before
    public void setUp() throws Exception {
        startDevSession();
    }

    @After
    public void tearDown() throws Exception {
        endDevSession();
    }

    @Test
    public void test_joinCustomAudience_throwsSecurityException() {
        assertCallIsRejected(
                mCustomAudienceClient.joinCustomAudience(
                        CustomAudienceFixture.getValidBuilderForBuyer(AD_TECH).build()));
    }

    @Test
    public void test_leaveCustomAudience_throwsSecurityException() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(AD_TECH).build();

        assertCallIsRejected(
                mCustomAudienceClient.leaveCustomAudience(
                        customAudience.getBuyer(), customAudience.getName()));
    }

    @Test
    public void test_fetchAndJoinCustomAudience_throwsSecurityException() {
        assertCallIsRejected(
                mCustomAudienceClient.fetchAndJoinCustomAudience(
                        new FetchAndJoinCustomAudienceRequest.Builder(
                                        CustomAudienceFixture.getValidFetchUriByBuyer(
                                                CommonFixture.VALID_BUYER_1, "1"))
                                .build()));
    }

    @Test
    public void test_scheduleCustomAudienceUpdate_throwsSecurityException() {
        assertCallIsRejected(
                mCustomAudienceClient.scheduleCustomAudienceUpdate(
                        new ScheduleCustomAudienceUpdateRequest.Builder(
                                        CustomAudienceFixture.getValidFetchUriByBuyer(
                                                CommonFixture.VALID_BUYER_1, "1"),
                                        Duration.ofDays(1),
                                        List.of())
                                .build()));
    }

    @Test
    public void test_getAdSelectionData_throwsSecurityException() {
        assertCallIsRejected(
                mAdSelectionClient.getAdSelectionData(
                        new GetAdSelectionDataRequest.Builder().setSeller(AD_TECH).build()));
    }

    @Test
    public void test_reportImpressionOnDeviceAuction_throwsSecurityException() {
        assertCallIsRejected(
                mAdSelectionClient.reportImpression(
                        new ReportImpressionRequest(
                                VALID_AD_SELECTION_ID,
                                AdSelectionConfigFixture.anAdSelectionConfig())));
    }

    @Test
    public void test_reportImpressionServerAuction_throwsSecurityException() {
        assertCallIsRejected(
                mAdSelectionClient.reportImpression(
                        new ReportImpressionRequest(VALID_AD_SELECTION_ID)));
    }

    @Test
    public void test_reportEvent_throwsSecurityException() {
        assertCallIsRejected(
                mAdSelectionClient.reportEvent(
                        new ReportEventRequest.Builder(
                                        VALID_AD_SELECTION_ID,
                                        "click",
                                        "some data",
                                        FLAG_REPORTING_DESTINATION_SELLER)
                                .build()));
    }

    @Test
    public void test_updateSignals_throwsSecurityException() {
        assertCallIsRejected(
                mProtectedSignalsClient.updateSignals(
                        new UpdateSignalsRequest.Builder(Uri.EMPTY).build()));
    }

    private void startDevSession() throws Exception {
        setDevSessionState(true);
    }

    private void endDevSession() throws Exception {
        setDevSessionState(false);
    }

    private void setDevSessionState(boolean state) throws Exception {
        sLogger.v("Starting setDevSession(%b)", state);
        assertThat(
                        mAdServicesShellCommandHelper.runCommand(
                                "adservices-api dev-session %s --erase-db",
                                state ? "start" : "end"))
                .isNotEmpty();
        sLogger.v("Completed setDevSession(%b)", state);
    }

    private void assertCallIsRejected(ListenableFuture<?> future) {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> future.get(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertTrue(
                "Expected ExecutionException to be caused by SecurityException, but was: "
                        + exception.getCause(),
                exception.getCause() instanceof SecurityException);
    }
}
