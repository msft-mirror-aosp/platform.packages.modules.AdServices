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

package android.adservices.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.adservices.service.PhFlagsFixture;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class TestAdSelectionManagerTest extends ForegroundCtsTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final long AD_SELECTION_ID = 1;
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("test.com");
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://test.com/test/decisions_logic_uris");
    private static final Uri TRUSTED_SCORING_SIGNALS_URI =
            Uri.parse("https://test.com/test/decisions_logic_uris");
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setSeller(SELLER)
                    .setDecisionLogicUri(DECISION_LOGIC_URI)
                    .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                    .build();

    private static final AdSelectionSignals SELECTION_SIGNALS = AdSelectionSignals.EMPTY;

    private static final AdSelectionFromOutcomesConfig AD_SELECTION_FROM_OUTCOMES_CONFIG =
            AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                    SELLER, DECISION_LOGIC_URI);

    private TestAdSelectionClient mTestAdSelectionClient;
    private boolean mIsDebugMode;
    private String mPreviousAppAllowList;

    @Before
    public void setup() {
        if (SdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        } else {
            mPreviousAppAllowList =
                    CompatAdServicesTestUtils.getAndOverridePpapiAppAllowList(
                            sContext.getPackageName());
            CompatAdServicesTestUtils.setFlags();
        }

        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        DevContext devContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDevOptionsEnabled();

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);
        PhFlagsFixture.overrideEnforceIsolateMaxHeapSize(false);
        PhFlagsFixture.overrideIsolateMaxHeapSizeBytes(0);
    }

    @After
    public void tearDown() {
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.setPpapiAppAllowList(mPreviousAppAllowList);
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
    }

    @Test
    public void testFailsWithInvalidAdSelectionId() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        sLogger.i("Calling Report Impression");

        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportImpressionRequest input =
                new ReportImpressionRequest(AD_SELECTION_ID, AD_SELECTION_CONFIG);

        ListenableFuture<Void> result = adSelectionClient.reportImpression(input);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        ListenableFuture<Void> result =
                mTestAdSelectionClient.overrideAdSelectionConfigRemoteInfo(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testRemoveOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        RemoveAdSelectionOverrideRequest request =
                new RemoveAdSelectionOverrideRequest(AD_SELECTION_CONFIG);

        ListenableFuture<Void> result =
                mTestAdSelectionClient.removeAdSelectionConfigRemoteInfoOverride(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testResetAllOverridesFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        TestAdSelectionClient testAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<Void> result =
                testAdSelectionClient.resetAllAdSelectionConfigRemoteOverrides();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testFailsWithInvalidAdSelectionConfigNoBuyers() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        sLogger.i("Calling Ad Selection");
        AdSelectionConfig adSelectionConfigNoBuyers =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER)
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(new ArrayList<>())
                        .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                        .build();
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<AdSelectionOutcome> result =
                adSelectionClient.selectAds(adSelectionConfigNoBuyers);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void testAddFromOutcomesOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        AD_SELECTION_FROM_OUTCOMES_CONFIG, DECISION_LOGIC_JS, SELECTION_SIGNALS);

        ListenableFuture<Void> result =
                mTestAdSelectionClient.overrideAdSelectionFromOutcomesConfigRemoteInfo(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testRemoveFromOutcomesOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        RemoveAdSelectionFromOutcomesOverrideRequest request =
                new RemoveAdSelectionFromOutcomesOverrideRequest(AD_SELECTION_FROM_OUTCOMES_CONFIG);

        ListenableFuture<Void> result =
                mTestAdSelectionClient.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                        request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testResetAllFromOutcomesOverridesFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        ListenableFuture<Void> result =
                mTestAdSelectionClient.resetAllAdSelectionFromOutcomesConfigRemoteOverrides();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    // TODO(b/221876775): Add override CTS tests for frequency cap API review
}
