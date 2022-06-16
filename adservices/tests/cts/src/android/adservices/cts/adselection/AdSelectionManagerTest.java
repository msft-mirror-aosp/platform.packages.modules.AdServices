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

package android.adservices.cts.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AddAdSelectionOverrideRequest;
import android.adservices.adselection.RemoveAdSelectionOverrideRequest;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.clients.adselection.AdSelectionClient;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
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
    private static final AdSelectionConfig AD_SELECTION_CONFIG =
            AdSelectionConfigFixture.anAdSelectionConfig();

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
    public void testAddOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .setDecisionLogicJs(DECISION_LOGIC_JS)
                        .build();

        ListenableFuture<Void> result =
                mAdSelectionClient.overrideAdSelectionConfigRemoteInfo(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        AddAdSelectionOverrideRequest request =
                new AddAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .setDecisionLogicJs(DECISION_LOGIC_JS)
                        .build();

        ListenableFuture<Void> result =
                mAdSelectionClient.overrideAdSelectionConfigRemoteInfo(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testRemoveNotExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        RemoveAdSelectionOverrideRequest request =
                new RemoveAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .build();

        ListenableFuture<Void> result =
                mAdSelectionClient.removeAdSelectionConfigRemoteInfoOverride(request);

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveExistingOverrideSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        AddAdSelectionOverrideRequest addRequest =
                new AddAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .setDecisionLogicJs(DECISION_LOGIC_JS)
                        .build();

        ListenableFuture<Void> addResult =
                mAdSelectionClient.overrideAdSelectionConfigRemoteInfo(addRequest);

        // Asserting no exception since there is no returned value
        addResult.get(10, TimeUnit.SECONDS);

        RemoveAdSelectionOverrideRequest removeRequest =
                new RemoveAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .build();

        ListenableFuture<Void> removeResult =
                mAdSelectionClient.removeAdSelectionConfigRemoteInfoOverride(removeRequest);

        // Asserting no exception since there is no returned value
        removeResult.get(10, TimeUnit.SECONDS);
    }

    @Test
    public void testRemoveOverrideFailsWithDebugModeDisabled() throws Exception {
        Assume.assumeFalse(mIsDebugMode);

        RemoveAdSelectionOverrideRequest request =
                new RemoveAdSelectionOverrideRequest.Builder()
                        .setAdSelectionConfig(AD_SELECTION_CONFIG)
                        .build();

        ListenableFuture<Void> result =
                mAdSelectionClient.removeAdSelectionConfigRemoteInfoOverride(request);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testResetAllOverridesSucceeds() throws Exception {
        Assume.assumeTrue(mIsDebugMode);

        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<Void> result =
                adSelectionClient.resetAllAdSelectionConfigRemoteOverrides();

        // Asserting no exception since there is no returned value
        result.get(10, TimeUnit.SECONDS);
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
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testFailsWithInvalidAdSelectionConfigNoBuyers() throws Exception {
        LogUtil.i("Calling Ad Selection");
        AdSelectionConfig adSelectionConfigNoBuyers =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(new ArrayList<String>())
                        .build();
        AdSelectionClient adSelectionClient =
                new AdSelectionClient.Builder()
                        .setContext(sContext)
                        .setExecutor(CALLBACK_EXECUTOR)
                        .build();

        ListenableFuture<AdSelectionOutcome> result =
                adSelectionClient.runAdSelection(adSelectionConfigNoBuyers);

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            result.get(10, TimeUnit.SECONDS);
                        });
        assertThat(exception.getCause()).isInstanceOf(AdServicesException.class);
    }
}
