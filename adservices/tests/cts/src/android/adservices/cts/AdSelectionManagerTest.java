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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.net.Uri;
import android.os.Process;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.LogUtil;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class AdSelectionManagerTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();

    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final long AD_SELECTION_ID = 1;
    private static final AdTechIdentifier SELLER =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_urls");
    private static final Uri TRUSTED_SCORING_SIGNALS_URI =
            Uri.parse("https://developer.android.com/test/decisions_logic_urls");
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_url_1\": \"signals_for_1\",\n"
                            + "\t\"render_url_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                    .setSeller(SELLER)
                    .setDecisionLogicUri(DECISION_LOGIC_URI)
                    .setTrustedScoringSignalsUri(TRUSTED_SCORING_SIGNALS_URI)
                    .build();

    private AdSelectionClient mAdSelectionClient;
    private boolean mIsDebugMode;

    @Before
    public void setup() {
        mAdSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();
        DevContext devContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDevOptionsEnabled();
    }

    @Test
    public void testFailsWithInvalidAdSelectionId() throws Exception {
        LogUtil.i("Calling Report Impression");

        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ReportImpressionRequest input =
                new ReportImpressionRequest.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .build();

        ListenableFuture<Void> result =
                adSelectionClient.reportImpression(input);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(AdServicesException.class);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest(
                        AD_SELECTION_CONFIG, DECISION_LOGIC_JS, TRUSTED_SCORING_SIGNALS);

        ListenableFuture<Void> result =
                mAdSelectionClient.overrideAdSelectionConfigRemoteInfo(request);

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
                mAdSelectionClient.removeAdSelectionConfigRemoteInfoOverride(request);

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

        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<Void> result =
                adSelectionClient.resetAllAdSelectionConfigRemoteOverrides();

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
        LogUtil.i("Calling Ad Selection");
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
        assertThat(exception.getCause()).isInstanceOf(AdServicesException.class);
    }
}
