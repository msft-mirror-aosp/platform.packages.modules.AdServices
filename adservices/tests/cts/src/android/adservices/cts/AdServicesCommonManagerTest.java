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

import android.adservices.common.AdServicesCommonManager;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.content.Context;
import android.os.OutcomeReceiver;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.CompatAdServicesTestUtils;
import com.android.compatibility.common.util.ShellUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class AdServicesCommonManagerTest {
    private static final String TAG = "AdservicesStatusManagerTest";
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private AdServicesCommonManager mCommonManager = AdServicesCommonManager.get(sContext);

    @Rule
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setup() {
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.setFlags();
        }
    }

    @After
    public void tearDown() {
        if (!SdkLevel.isAtLeastT()) {
            CompatAdServicesTestUtils.resetFlagsToDefault();
        }
    }

    @Test
    public void testStatusManagerNotAuthorizedOnSPlus() {
        Assume.assumeTrue(SdkLevel.isAtLeastS());

        overrideAdserviceEnableStatus(false);

        // At beginning, Sdk1 receives a false status.
        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatus();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            adServicesStatusResponse.get(1, TimeUnit.SECONDS);
                        });
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testSetStatusEnabledNotExecutedOnSPlus() throws Exception {
        Assume.assumeTrue(SdkLevel.isAtLeastS());
        mCommonManager.setAdServicesEnabled(true, true);

        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatus();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            adServicesStatusResponse.get(1, TimeUnit.SECONDS);
                        });
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testStatusManagerNotAuthorizedCompat() {
        overrideAdserviceEnableStatus(false);

        // At beginning, Sdk1 receives a false status.
        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatusCompat();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            adServicesStatusResponse.get(1, TimeUnit.SECONDS);
                        });
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    @Test
    public void testSetStatusEnabledNotExecutedCompat() throws Exception {
        mCommonManager.setAdServicesEnabled(true, true);

        ListenableFuture<Boolean> adServicesStatusResponse = getAdservicesStatusCompat();

        Exception adServicesStatusResponseException =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            adServicesStatusResponse.get(1, TimeUnit.SECONDS);
                        });
        assertThat(adServicesStatusResponseException.getCause())
                .isInstanceOf(SecurityException.class);
    }

    // Override the Adservice enable status in the test.
    private void overrideAdserviceEnableStatus(boolean overrideStatus) {
        ShellUtils.runShellCommand(
                "device_config put adservices adservice_enabled " + overrideStatus);
    }

    private ListenableFuture<Boolean> getAdservicesStatus() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new OutcomeReceiver<Boolean, Exception>() {
                                @Override
                                public void onResult(Boolean result) {
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

    private ListenableFuture<Boolean> getAdservicesStatusCompat() {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    mCommonManager.isAdServicesEnabled(
                            CALLBACK_EXECUTOR,
                            new AdServicesOutcomeReceiver<Boolean, Exception>() {
                                @Override
                                public void onResult(Boolean result) {
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
}
