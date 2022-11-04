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

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.test.annotation.UiThreadTest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * This class contains all the binding logic needed for a test suite within an SDK. To set up SDK
 * tests, extend this class. To attach a custom view to your tests, override beforeEachTest() to
 * return your view (for example a WebView).
 */
public abstract class SdkSandboxTestScenarioRunner extends SandboxedSdkProvider {
    private static final String TAG = SdkSandboxTestScenarioRunner.class.getName();

    private Object mTestInstance;
    private @Nullable IBinder mBinder;

    /**
     * This API allows you to provide a separate test class to execute tests against. It will
     * default to the class instance it is already attached to.
     */
    public Object getTestInstance() {
        return this;
    }

    public View beforeEachTest(Context windowContext, Bundle params, int width, int height) {
        return new View(windowContext);
    }

    @Override
    public final View getView(Context windowContext, Bundle params, int width, int height) {
        return beforeEachTest(windowContext, params, width, height);
    }

    @Override
    public final SandboxedSdk onLoadSdk(Bundle params) {
        mTestInstance = getTestInstance();
        mBinder = params.getBinder(ISdkSandboxTestExecutor.TEST_AUTHOR_DEFINED_BINDER);

        ISdkSandboxTestExecutor.Stub testExecutor =
                new ISdkSandboxTestExecutor.Stub() {
                    public void executeTest(
                            String testName,
                            Bundle testParams,
                            ISdkSandboxResultCallback resultCallback) {
                        try {
                            // We allow test authors to write a test without the bundle parameters
                            // for convenience.
                            // We will first look for the test name with a bundle parameter
                            // if we don't find that, we will load the test without a parameter.
                            boolean hasParams = true;
                            Method testMethod =
                                    findTest(testName, /*throwException*/ false, Bundle.class);
                            if (testMethod == null) {
                                hasParams = false;
                                testMethod = findTest(testName, /*throwException*/ true);
                            }

                            if (testMethod.isAnnotationPresent(UiThreadTest.class)) {
                                // The method reference has to be final before being
                                // used inside a lambda to ensure the variant stays the
                                // same
                                final Method method = testMethod;
                                final boolean params = hasParams;
                                new Handler(Looper.getMainLooper())
                                        .post(
                                                () -> {
                                                    invokeTestMethod(
                                                            method,
                                                            params,
                                                            testParams,
                                                            resultCallback);
                                                });
                            } else {
                                invokeTestMethod(testMethod, hasParams, testParams, resultCallback);
                            }
                        } catch (NoSuchMethodException error) {
                            try {
                                resultCallback.onError(getStackTrace(error));
                            } catch (RemoteException e) {
                                Log.e(TAG, "Failed to find method and report back");
                            }
                        }
                    }
                };

        return new SandboxedSdk(testExecutor);
    }

    @Nullable
    protected IBinder getCustomInterface() {
        return mBinder;
    }

    private Method findTest(String testName, boolean throwException, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        try {
            return mTestInstance.getClass().getMethod(testName, parameterTypes);
        } catch (NoSuchMethodException error) {
            if (throwException) {
                throw error;
            }
            return null;
        }
    }

    private void invokeTestMethod(
            final Method method,
            final boolean hasParams,
            final Bundle params,
            final ISdkSandboxResultCallback resultCallback) {
        try {
            if (hasParams) {
                method.invoke(mTestInstance, params);
            } else {
                method.invoke(mTestInstance);
            }
            resultCallback.onResult();
        } catch (Exception error) {
            String errorStackTrace = getStackTrace(error);

            try {
                resultCallback.onError(errorStackTrace);
            } catch (Exception ex) {
                if (error.getCause() instanceof AssertionError) {
                    Log.e(TAG, "Assertion failed on invoked method " + errorStackTrace);
                } else if (error.getCause() instanceof InvocationTargetException) {
                    Log.e(TAG, "Invocation target failed " + errorStackTrace);
                } else if (error.getCause() instanceof NoSuchMethodException) {
                    Log.e(TAG, "Test method not found " + errorStackTrace);
                } else {
                    Log.e(TAG, "Test execution failed " + errorStackTrace);
                }
            }
        }
    }

    private String getStackTrace(Exception error) {
        StringWriter errorStackTrace = new StringWriter();
        PrintWriter errorWriter = new PrintWriter(errorStackTrace);
        error.getCause().printStackTrace(errorWriter);
        return errorStackTrace.toString();
    }
}
