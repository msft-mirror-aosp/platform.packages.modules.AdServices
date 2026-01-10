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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

import static com.android.adservices.AdServicesCommon.BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_ISOLATE_MAX_HEAP_SIZE_BYTES;
import static com.android.adservices.shared.common.exception.AdServicesDeprecationConstants.AD_SELECTION_SERVICE_DEPRECATION_MESSAGE;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AddAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.GetAdSelectionDataRequest;
import android.adservices.adselection.PersistAdSelectionResultRequest;
import android.adservices.adselection.RemoveAdSelectionFromOutcomesOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.adselection.SetAppInstallAdvertisersRequest;
import android.adservices.adselection.UpdateAdCounterHistogramRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.clients.adselection.TestAdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.utils.CtsWebViewSupportUtil;
import android.net.Uri;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.annotations.EnableAllApis;
import com.android.adservices.common.annotations.SetCompatModeFlags;
import com.android.adservices.common.annotations.SetPpapiAppAllowList;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.SetFlagDisabled;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;
import com.android.adservices.shared.testing.annotations.SetLongDebugFlag;

import com.google.common.collect.ImmutableSet;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@EnableAllApis
@SetCompatModeFlags
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
@SetFlagDisabled(KEY_ISOLATE_MAX_HEAP_SIZE_BYTES)
@SetPpapiAppAllowList
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
// TODO (b/330324133): Short-term solution to allow test to extend binder timeout to
// resolve the test flakiness.
@SetLongDebugFlag(name = BINDER_TIMEOUT_SYSTEM_PROPERTY_NAME, value = 10_000)
public final class TestAdSelectionManagerTest extends ForegroundCtsTestCase {

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
    private static final String EVENT_KEY = "click";
    private static final int REPORTING_DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;

    private TestAdSelectionClient mTestAdSelectionClient;
    private boolean mIsDebugMode;

    @Before
    public void setup() {

        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        mTestAdSelectionClient =
                new TestAdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        DevContext devContext =
                DevContextFilter.create(mContext, /* developerModeFeatureEnabled= */ false)
                        .createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDeviceDevOptionsEnabled();

        String[] deviceConfigPermissions;
        if (sdkLevel.isAtLeastU()) {
            deviceConfigPermissions =
                    new String[] {
                        Manifest.permission.WRITE_DEVICE_CONFIG,
                        Manifest.permission.WRITE_ALLOWLISTED_DEVICE_CONFIG
                    };
        } else {
            deviceConfigPermissions = new String[] {Manifest.permission.WRITE_DEVICE_CONFIG};
        }
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(deviceConfigPermissions);

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @Test
    public void tesAdSelectionManager_selectAds_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        AdSelectionConfig config =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER)
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setCustomAudienceBuyers(new ArrayList<>())
                        .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> adSelectionClient.selectAds(config).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_selectAdsFromOutcomes_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        AdSelectionFromOutcomesConfig config = AD_SELECTION_FROM_OUTCOMES_CONFIG;

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class, () -> adSelectionClient.selectAds(config).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportImpression_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        ReportImpressionRequest input =
                new ReportImpressionRequest(AD_SELECTION_ID, AD_SELECTION_CONFIG);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.reportImpression(input).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_reportEvent_apiDeprecated() throws Exception {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID,
                                EVENT_KEY,
                                new JSONObject().put("key", "value").toString(),
                                REPORTING_DESTINATIONS)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.reportEvent(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_updateAdCounterHistogram_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        UpdateAdCounterHistogramRequest request =
                new UpdateAdCounterHistogramRequest.Builder(
                                AD_SELECTION_ID,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                CommonFixture.VALID_BUYER_1)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.updateAdCounterHistogram(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_setAppInstallAdvertisers_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        SetAppInstallAdvertisersRequest request =
                new SetAppInstallAdvertisersRequest.Builder()
                        .setAdvertisers(ImmutableSet.of())
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.setAppInstallAdvertisers(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_getAdSelectionData_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCoordinatorOriginUri(Uri.parse("https://example.com"))
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.getAdSelectionData(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void testAdSelectionManager_persistAdSelectionResult_apiDeprecated() {
        Assume.assumeTrue(CtsWebViewSupportUtil.isJSSandboxAvailable(sContext));
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        PersistAdSelectionResultRequest request =
                new PersistAdSelectionResultRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setAdSelectionId(AD_SELECTION_ID)
                        .build();

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () -> adSelectionClient.persistAdSelectionResult(request).get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    // TestAdSelectionClient APIs (Testing for SecurityException when Debug Mode is Disabled)

    @Test
    public void testTestAdSelectionManager_overrideAdSelectionConfigRemoteInfo_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);
        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .overrideAdSelectionConfigRemoteInfo(request)
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void
            testTestAdSelectionManager_removeAdSelectionConfigRemoteInfoOverride_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);
        RemoveAdSelectionOverrideRequest request =
                new RemoveAdSelectionOverrideRequest(AD_SELECTION_CONFIG);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .removeAdSelectionConfigRemoteInfoOverride(request)
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void
            testTestAdSelectionManager_resetAllAdSelectionConfigRemoteOverrides_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .resetAllAdSelectionConfigRemoteOverrides()
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void
            testTestAdSelectionManager_overrideAdSelectionFromOutcomesConfigRemoteInfo_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);
        AddAdSelectionFromOutcomesOverrideRequest request =
                new AddAdSelectionFromOutcomesOverrideRequest(
                        AD_SELECTION_FROM_OUTCOMES_CONFIG, DECISION_LOGIC_JS, SELECTION_SIGNALS);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .overrideAdSelectionFromOutcomesConfigRemoteInfo(request)
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void
            testTestAdSelectionManager_removeAdSelectionFromOutcomesConfigRemoteInfoOverride_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);
        RemoveAdSelectionFromOutcomesOverrideRequest request =
                new RemoveAdSelectionFromOutcomesOverrideRequest(AD_SELECTION_FROM_OUTCOMES_CONFIG);

        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                                                request)
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }

    @Test
    public void
            testTestAdSelectionManager_resetAllAdSelectionFromOutcomesConfigRemoteOverrides_debugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mTestAdSelectionClient
                                        .resetAllAdSelectionFromOutcomesConfigRemoteOverrides()
                                        .get());

        assertThat(exception.getCause()).isNotNull();
        assertThat(exception.getCause().getClass()).isEqualTo(IllegalStateException.class);
        assertThat(exception.getCause().getMessage())
                .isEqualTo(AD_SELECTION_SERVICE_DEPRECATION_MESSAGE);
    }
}
