/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityHandler;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;
import android.app.sdksandbox.testutils.FakeSdkSandboxActivityRegistryInjector;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.android.modules.utils.build.SdkLevel;
import com.android.server.sdksandbox.DeviceSupportedBaseTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class SandboxedActivityTest extends DeviceSupportedBaseTest {

    private static final String SDK_NAME = "SDK_NAME";
    private SandboxedSdkContext mSdkContext;
    private SdkSandboxActivityRegistry mRegistry;
    private SdkSandboxLocalSingleton mSdkSandboxLocalSingleton;
    private ISdkToServiceCallback mServiceCallback;
    private FakeInjector mInjector;
    private FakeSdkSandboxActivityRegistryInjector mActivityRegistryInjector;

    static class FakeInjector extends SandboxedActivity.Injector {
        private final SdkSandboxActivityRegistry mRegistry;

        FakeInjector(SdkSandboxActivityRegistry registry) {
            mRegistry = registry;
        }

        @Override
        SdkSandboxActivityRegistry getSdkSandboxActivityRegistry() {
            return mRegistry;
        }
    }

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
        mSdkSandboxLocalSingleton = Mockito.mock(SdkSandboxLocalSingleton.class);
        mServiceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(mServiceCallback);
        mActivityRegistryInjector =
                new FakeSdkSandboxActivityRegistryInjector(mSdkSandboxLocalSingleton);

        mRegistry = SdkSandboxActivityRegistry.getInstance(mActivityRegistryInjector);
        mInjector = new FakeInjector(mRegistry);
        mSdkContext = Mockito.mock(SandboxedSdkContext.class);
        Mockito.when(mSdkContext.getSdkName()).thenReturn(SDK_NAME);
    }

    @Test
    public void testSandboxedActivityCreation() {
        SdkSandboxActivityHandler sdkSandboxActivityHandler = Mockito.spy(activity -> {});
        IBinder token = mRegistry.register(mSdkContext, sdkSandboxActivityHandler);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity = new SandboxedActivity(mInjector);
                            Intent intent = buildIntent(sandboxedActivity, token);
                            sandboxedActivity.setIntent(intent);

                            sandboxedActivity.notifySdkOnActivityCreation();

                            ArgumentCaptor<SandboxedActivity> sandboxedActivityArgumentCaptor =
                                    ArgumentCaptor.forClass(SandboxedActivity.class);
                            Mockito.verify(sdkSandboxActivityHandler)
                                    .onActivityCreated(sandboxedActivityArgumentCaptor.capture());
                            assertThat(sandboxedActivityArgumentCaptor.getValue())
                                    .isEqualTo(sandboxedActivity);
                        },
                        1000);
    }

    @Test
    public void testSandboxedActivityFinishIfNoIntentExtras() {
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity =
                                    Mockito.spy(new SandboxedActivity(mInjector));
                            sandboxedActivity.setIntent(new Intent());

                            sandboxedActivity.notifySdkOnActivityCreation();
                            assertThat(sandboxedActivity.isFinishing()).isTrue();
                        },
                        1000);
    }

    @Test
    public void testSandboxedActivityFinishIfNoIntentExtrasNotHavingTheHandlerToken() {
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity =
                                    Mockito.spy(new SandboxedActivity(mInjector));

                            Intent intent = new Intent();
                            Bundle extras = new Bundle();
                            intent.putExtras(extras);
                            sandboxedActivity.setIntent(intent);

                            sandboxedActivity.notifySdkOnActivityCreation();
                            assertThat(sandboxedActivity.isFinishing()).isTrue();
                        },
                        1000);
    }

    @Test
    public void testSandboxedActivityFinishIfHandlerTokenIsWrongType() {
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity =
                                    Mockito.spy(new SandboxedActivity(mInjector));

                            Intent intent = new Intent();
                            Bundle extras = new Bundle();
                            extras.putString(
                                    sandboxedActivity.getSandboxedActivityHandlerKey(), "");
                            intent.putExtras(extras);
                            sandboxedActivity.setIntent(intent);

                            sandboxedActivity.notifySdkOnActivityCreation();
                            assertThat(sandboxedActivity.isFinishing()).isTrue();
                        },
                        1000);
    }

    @Test
    public void testSandboxedActivityFinishIfHandlerNotRegistered() {
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity =
                                    Mockito.spy(new SandboxedActivity(mInjector));
                            Intent intent = buildIntent(sandboxedActivity, new Binder());
                            sandboxedActivity.setIntent(intent);

                            sandboxedActivity.notifySdkOnActivityCreation();
                            assertThat(sandboxedActivity.isFinishing()).isTrue();
                        },
                        1000);
    }

    @Test
    public void testMultipleSandboxedActivitiesForTheSameHandler() {
        SdkSandboxActivityHandler sdkSandboxActivityHandler = activity -> {};
        IBinder token = mRegistry.register(mSdkContext, sdkSandboxActivityHandler);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity1 =
                                    Mockito.spy(new SandboxedActivity(mInjector));
                            Intent intent = buildIntent(sandboxedActivity1, token);

                            sandboxedActivity1.setIntent(intent);
                            sandboxedActivity1.notifySdkOnActivityCreation();

                            SandboxedActivity sandboxedActivity2 =
                                    Mockito.spy(new SandboxedActivity(mInjector));
                            sandboxedActivity2.setIntent(intent);
                            sandboxedActivity2.notifySdkOnActivityCreation();

                            Mockito.verify(sandboxedActivity1, Mockito.never()).finish();
                            Mockito.verify(sandboxedActivity1, Mockito.never()).finish();
                        },
                        1000);
    }

    /** The activity base context should be wrapped in a new instance of `SandboxedSdkContext`. */
    @Test
    public void testAttachBaseContextWrapsTheBaseContextIfCustomizedSdkContextFlagIsEnabled() {
        SdkSandboxActivityHandler sdkSandboxActivityHandler = Mockito.spy(activity -> {});
        IBinder token = mRegistry.register(mSdkContext, sdkSandboxActivityHandler);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            SandboxedActivity sandboxedActivity = new SandboxedActivity(mInjector);
                            Intent intent = buildIntent(sandboxedActivity, token);
                            sandboxedActivity.setIntent(intent);

                            Context activityBaseContext = Mockito.mock(Context.class);

                            Mockito.when(mSdkContext.createContextWithNewBase(activityBaseContext))
                                    .thenCallRealMethod();
                            Mockito.when(mSdkContext.getBaseContext()).thenCallRealMethod();

                            sandboxedActivity.attachBaseContext(activityBaseContext);

                            SandboxedSdkContext newBaseContext =
                                    (SandboxedSdkContext) sandboxedActivity.getBaseContext();
                            assertThat(newBaseContext).isNotNull();
                            assertThat(newBaseContext.getBaseContext())
                                    .isEqualTo(activityBaseContext);
                        },
                        1000);
    }

    private Intent buildIntent(SandboxedActivity sandboxedActivity, IBinder token) {
        Intent intent = new Intent();
        intent.setAction(SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY);
        Bundle extras = new Bundle();
        extras.putBinder(sandboxedActivity.getSandboxedActivityHandlerKey(), token);
        intent.putExtras(extras);
        return intent;
    }
}
