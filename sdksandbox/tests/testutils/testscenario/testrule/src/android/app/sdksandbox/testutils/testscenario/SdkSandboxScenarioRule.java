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

package android.app.sdksandbox.testutils.testscenario;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.sdksandbox.RequestSurfacePackageException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.testutils.FakeLoadSdkCallback;
import android.app.sdksandbox.testutils.FakeRequestSurfacePackageCallback;
import android.app.sdksandbox.testutils.WaitableCountDownLatch;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This rule is used to invoke tests inside SDKs. It loads a given Sdk, calls for a test to be
 * executed inside given Sdk and unloads the Sdk once the execution is finished.
 * assertSdkTestRunPasses() contains the logic to trigger an in-SDK test and retrieve its results,
 * while {@link SdkSandboxTestScenarioRunner} handles the Sdk-side logic for test execution.
 */
public final class SdkSandboxScenarioRule implements TestRule {
    // We need to allow a fair amount of time to time out since we might
    // want to execute fairly large tests.
    private static final int TEST_TIMEOUT_S = 60;
    private final String mSdkName;
    private ISdkSandboxTestExecutor mTestExecutor;

    public SdkSandboxScenarioRule(String sdkName) {
        mSdkName = sdkName;
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        // This statement would wrap around every test, similar to @Before and @After
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                try (ActivityScenario scenario =
                        ActivityScenario.launch(SdkSandboxCtsActivity.class)) {
                    final Context context =
                            InstrumentationRegistry.getInstrumentation().getContext();
                    SdkSandboxManager sdkSandboxManager =
                            context.getSystemService(SdkSandboxManager.class);
                    final SandboxedSdk sdk = getLoadedSdk(sdkSandboxManager, mSdkName);
                    assertThat(scenario.getState()).isEqualTo(Lifecycle.State.RESUMED);
                    setView(scenario, sdkSandboxManager);
                    mTestExecutor = ISdkSandboxTestExecutor.Stub.asInterface(sdk.getInterface());
                    try {
                        base.evaluate();
                    } finally {
                        sdkSandboxManager.unloadSdk(mSdkName);
                    }
                }
            }
        };
    }

    public void assertSdkTestRunPasses(String testMethodName) throws Exception {
        assertSdkTestRunPasses(testMethodName, new Bundle());
    }

    public void assertSdkTestRunPasses(String testMethodName, Bundle params) throws Exception {
        WaitableCountDownLatch testDoneLatch = new WaitableCountDownLatch(TEST_TIMEOUT_S);
        AtomicReference<String> errorRef = new AtomicReference<>(null);

        ISdkSandboxResultCallback.Stub callback =
                new ISdkSandboxResultCallback.Stub() {
                    public void onResult() {
                        testDoneLatch.countDown();
                    }

                    public void onError(String errorMessage) {
                        if (TextUtils.isEmpty(errorMessage)) {
                            errorRef.set("Error executing test: Sdk returned no stacktrace");
                        } else {
                            errorRef.set(errorMessage);
                        }
                        testDoneLatch.countDown();
                    }
                };

        assertThat(mTestExecutor).isNotNull();
        mTestExecutor.executeTest(testMethodName, params, callback);

        testDoneLatch.waitForLatch("Sdk did not return any response");

        if (errorRef.get() != null) assertWithMessage(errorRef.get()).fail();
        assertThat(true).isTrue();
    }

    private static SandboxedSdk getLoadedSdk(SdkSandboxManager mSdkSandboxManager, String sdkName)
            throws Exception {
        final FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
        mSdkSandboxManager.loadSdk(sdkName, new Bundle(), Runnable::run, callback);
        if (!callback.isLoadSdkSuccessful()) {
            Assume.assumeTrue(
                    "Skipping test because Sdk Sandbox is disabled",
                    callback.getLoadSdkErrorCode()
                            != SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED);
            throw callback.getLoadSdkException();
        }
        return callback.getSandboxedSdk();
    }

    private void setView(ActivityScenario scenario, SdkSandboxManager mSdkSandboxManager)
            throws Exception {
        AtomicReference<RequestSurfacePackageException> surfacePackageException =
                new AtomicReference<>(null);
        scenario.onActivity(
                activity -> {
                    final SurfaceView renderedView = activity.findViewById(R.id.rendered_view);
                    final FakeRequestSurfacePackageCallback surfacePackageCallback =
                            new FakeRequestSurfacePackageCallback();

                    Bundle params = new Bundle();
                    params.putInt(EXTRA_WIDTH_IN_PIXELS, renderedView.getWidth());
                    params.putInt(EXTRA_HEIGHT_IN_PIXELS, renderedView.getHeight());
                    params.putInt(EXTRA_DISPLAY_ID, activity.getDisplay().getDisplayId());
                    params.putBinder(EXTRA_HOST_TOKEN, renderedView.getHostToken());

                    mSdkSandboxManager.requestSurfacePackage(
                            mSdkName, params, Runnable::run, surfacePackageCallback);

                    if (!surfacePackageCallback.isRequestSurfacePackageSuccessful()) {
                        surfacePackageException.set(
                                surfacePackageCallback.getSurfacePackageException());
                    } else {
                        renderedView.setChildSurfacePackage(
                                surfacePackageCallback.getSurfacePackage());
                        renderedView.setVisibility(View.VISIBLE);
                        renderedView.setZOrderOnTop(true);
                    }
                });
        if (surfacePackageException.get() != null) {
            throw surfacePackageException.get();
        }
    }
}
