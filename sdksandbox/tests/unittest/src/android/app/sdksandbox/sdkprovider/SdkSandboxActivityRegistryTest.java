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

package android.app.sdksandbox.sdkprovider;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.StatsdUtil;
import android.app.sdksandbox.sandboxactivity.ActivityContextInfo;
import android.app.sdksandbox.testutils.FakeSdkSandboxActivityRegistryInjector;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

@RunWith(JUnit4.class)
public class SdkSandboxActivityRegistryTest {

    private static final String SDK_NAME = "SDK_NAME";
    private static final long TIME_SANDBOX_ACTIVITY_START_INITIATED = 1;
    private static final long TIME_EVENT_STARTED = 10;
    private static final long TIME_EVENT_FINISHED = 100;
    private static final long TIME_SANDBOX_ACTIVITY_CREATION_FINISHED = 101;
    private SdkSandboxActivityRegistry mRegistry;
    private SdkSandboxActivityHandler mHandler;
    private SandboxedSdkContext mSdkContext;
    private SdkSandboxLocalSingleton mSdkSandboxLocalSingleton;
    private ISdkToServiceCallback mServiceCallback;
    private FakeSdkSandboxActivityRegistryInjector mInjector;

    @Before
    public void setUp() {
        assumeTrue(SdkLevel.isAtLeastU());
        mSdkSandboxLocalSingleton = Mockito.mock(SdkSandboxLocalSingleton.class);
        mServiceCallback = Mockito.mock(ISdkToServiceCallback.class);
        Mockito.when(mSdkSandboxLocalSingleton.getSdkToServiceCallback())
                .thenReturn(mServiceCallback);
        mInjector = new FakeSdkSandboxActivityRegistryInjector(mSdkSandboxLocalSingleton);

        mRegistry = SdkSandboxActivityRegistry.getInstance(mInjector);
        mHandler = Mockito.spy(activity -> {});
        mSdkContext = Mockito.mock(SandboxedSdkContext.class);
        Mockito.when(mSdkContext.getSdkName()).thenReturn(SDK_NAME);
    }

    @After
    public void tearDown() {
        if (SdkLevel.isAtLeastU()) {
            try {
                mRegistry.unregister(mHandler);
            } catch (IllegalArgumentException e) {
                // safe to ignore, it is already unregistered
            }
            mInjector.resetTimeSeries();
        }
    }

    @Test
    public void testRegisterSdkSandboxActivityHandler() {
        IBinder token1 = mRegistry.register(mSdkContext, mHandler);
        IBinder token2 = mRegistry.register(mSdkContext, mHandler);
        assertThat(token2).isEqualTo(token1);
    }

    @Test
    public void testRegisterSdkSandboxActivityHandler_NewHandler_CallsStatsd()
            throws RemoteException {
        mInjector.setLatencyTimeSeries(List.of(TIME_EVENT_STARTED, TIME_EVENT_FINISHED));

        mRegistry.register(mSdkContext, mHandler);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        StatsdUtil
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__PUT_SDK_SANDBOX_ACTIVITY_HANDLER,
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        (int) (TIME_EVENT_FINISHED - TIME_EVENT_STARTED));
    }

    @Test
    public void testUnregisterSdkSandboxActivityHandler() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        mRegistry.unregister(mHandler);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            IllegalArgumentException exception =
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    mRegistry.notifyOnActivityCreation(
                                                            intent, activity));
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "There is no registered "
                                                    + "SdkSandboxActivityHandler to notify");
                        },
                        1000);
    }

    @Test
    public void testUnregisterSdkSandboxActivityHandler_ExistingHandler_CallsStatsd()
            throws RemoteException {
        mRegistry.register(mSdkContext, mHandler);
        mInjector.setLatencyTimeSeries(List.of(TIME_EVENT_STARTED, TIME_EVENT_FINISHED));

        mRegistry.unregister(mHandler);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        StatsdUtil
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__REMOVE_SDK_SANDBOX_ACTIVITY_HANDLER,
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        (int) (TIME_EVENT_FINISHED - TIME_EVENT_STARTED));
    }

    @Test
    public void testNotifyOnActivityCreation() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);

                            ArgumentCaptor<Activity> activityArgumentCaptor =
                                    ArgumentCaptor.forClass(Activity.class);
                            Mockito.verify(mHandler)
                                    .onActivityCreated(activityArgumentCaptor.capture());
                            assertThat(activityArgumentCaptor.getValue()).isEqualTo(activity);
                        },
                        1000);
    }

    @Test
    public void testNotifyOnActivityCreationMultipleTimeSucceed() {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);
                            mRegistry.notifyOnActivityCreation(intent, activity);
                        },
                        1000);
    }

    @Test
    public void testNotifyOnActivityCreation_CallsStatsd() throws RemoteException {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        mInjector.setLatencyTimeSeries(
                List.of(
                        TIME_EVENT_STARTED,
                        TIME_EVENT_FINISHED,
                        TIME_SANDBOX_ACTIVITY_CREATION_FINISHED));

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);
                        },
                        1000);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        StatsdUtil
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__NOTIFY_SDK_ON_ACTIVITY_CREATION,
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        (int) (TIME_EVENT_FINISHED - TIME_EVENT_STARTED));
        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__TOTAL,
                        StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                        (int)
                                (TIME_SANDBOX_ACTIVITY_CREATION_FINISHED
                                        - TIME_SANDBOX_ACTIVITY_START_INITIATED));
    }

    @Test
    public void testNotifyOnActivityCreation_MultipleTimes_CallsStatsd() throws RemoteException {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);
                            mRegistry.notifyOnActivityCreation(intent, activity);
                        },
                        1000);

        Mockito.verify(mServiceCallback, Mockito.times(2))
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(
                                StatsdUtil
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__NOTIFY_SDK_ON_ACTIVITY_CREATION),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS),
                        Mockito.anyInt());
    }

    @Test
    public void testNotifyOnActivityCreation_MultipleTimes_CallsStatsdForTotalLatencyOnce()
            throws RemoteException {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            mRegistry.notifyOnActivityCreation(intent, activity);
                            mRegistry.notifyOnActivityCreation(intent, activity);
                        },
                        1000);

        Mockito.verify(mServiceCallback, Mockito.times(1))
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__TOTAL),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS),
                        Mockito.anyInt());
    }

    @Test
    public void testNotifyOnActivityCreation_ExtraParamsMissing_CallsStatsd()
            throws RemoteException {
        Intent intent = new Intent();

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            IllegalArgumentException exception =
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    mRegistry.notifyOnActivityCreation(
                                                            intent, activity));
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "Extra params of the intent are missing the IBinder"
                                                    + " value for the key ("
                                                    + SdkSandboxManager
                                                            .EXTRA_SANDBOXED_ACTIVITY_HANDLER
                                                    + ")");
                        },
                        1000);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(
                                StatsdUtil
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__NOTIFY_SDK_ON_ACTIVITY_CREATION),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE),
                        Mockito.anyInt());
        // Total sandbox activity creation latency should only be logged if successful.
        Mockito.verify(mServiceCallback, Mockito.never())
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__TOTAL),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    @Test
    public void testNotifyOnActivityCreation_NotRegisteredHandler_CallsStatsd()
            throws RemoteException {
        IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);
        mRegistry.unregister(mHandler);

        new Handler(Looper.getMainLooper())
                .runWithScissors(
                        () -> {
                            Activity activity = new Activity();
                            IllegalArgumentException exception =
                                    assertThrows(
                                            IllegalArgumentException.class,
                                            () ->
                                                    mRegistry.notifyOnActivityCreation(
                                                            intent, activity));
                            assertThat(exception.getMessage())
                                    .isEqualTo(
                                            "There is no registered SdkSandboxActivityHandler to"
                                                    + " notify");
                        },
                        1000);

        Mockito.verify(mServiceCallback)
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(
                                StatsdUtil
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__NOTIFY_SDK_ON_ACTIVITY_CREATION),
                        Mockito.eq(
                                StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE),
                        Mockito.anyInt());
        Mockito.verify(mServiceCallback, Mockito.never())
                .logSandboxActivityApiLatencyFromSandbox(
                        Mockito.eq(StatsdUtil.SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__TOTAL),
                        Mockito.anyInt(),
                        Mockito.anyInt());
    }

    @Test
    public void testUnregisterAllHandlersForSdkName() {
        SdkSandboxActivityHandler handler1Sdk1 = activity -> {};
        SdkSandboxActivityHandler handler2Sdk1 = activity -> {};

        // Register SDK1 handlers
        IBinder token1Sdk1 = mRegistry.register(mSdkContext, handler1Sdk1);
        IBinder token2Sdk1 = mRegistry.register(mSdkContext, handler2Sdk1);

        // Before unregistering, registering the same handlers should return the same tokens.
        assertThat(mRegistry.register(mSdkContext, handler1Sdk1)).isEqualTo(token1Sdk1);
        assertThat(mRegistry.register(mSdkContext, handler2Sdk1)).isEqualTo(token2Sdk1);

        // Unregistering SDK1 handlers
        mRegistry.unregisterAllActivityHandlersForSdk(SDK_NAME);

        // Registering SDK1 handlers should return different tokens as they are unregistered.
        assertThat(mRegistry.register(mSdkContext, handler1Sdk1)).isNotEqualTo(token1Sdk1);
        assertThat(mRegistry.register(mSdkContext, handler2Sdk1)).isNotEqualTo(token2Sdk1);
    }

    /**
     * Ensure that ActivityContextInfo returned from the SdkSandboxActivityRegistry has the right
     * fields.
     */
    @Test
    public void testGetActivityContextInfo() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        ActivityContextInfo contextInfo = mRegistry.getContextInfo(intent);

        assertThat(ActivityContextInfo.CONTEXT_FLAGS)
                .isEqualTo(Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        contextInfo.getSdkApplicationInfo();
        Mockito.verify(mSdkContext, Mockito.times(1)).getApplicationInfo();
    }

    /** Ensure that the handler has to be registered for retrieving the ActivityContextInfo . */
    @Test
    public void testGetActivityContextInfoIsNullForNonRegisteredHandlers() {
        final Intent intent = buildSandboxActivityIntent(new Binder());

        assertThat(mRegistry.getContextInfo(intent)).isNull();
    }

    /**
     * Test retrieving the SDK context from SdkSandboxActivityRegistry, passing an intent refers to
     * the registered handler.
     */
    @Test
    public void testGetSdkContext() {
        final IBinder token = mRegistry.register(mSdkContext, mHandler);
        Intent intent = buildSandboxActivityIntent(token);

        SandboxedSdkContext sdkContext = mRegistry.getSdkContext(intent);
        assertThat(sdkContext).isEqualTo(mSdkContext);
    }

    /** Ensure that handler has to be registered to retrieve the SDK context. */
    @Test
    public void testGetSdkContextIsNullForUnregisteredIntent() {
        Intent intent = buildSandboxActivityIntent(new Binder());

        SandboxedSdkContext sdkContext = mRegistry.getSdkContext(intent);
        assertThat(sdkContext).isNull();
    }

    private Intent buildSandboxActivityIntent(IBinder token) {
        final Intent intent = new Intent();
        final Bundle extras = new Bundle();
        extras.putBinder("android.app.sdksandbox.extra.SANDBOXED_ACTIVITY_HANDLER", token);
        extras.putLong(
                "android.app.sdksandbox.extra.EXTRA_SANDBOXED_ACTIVITY_INITIATION_TIME",
                TIME_SANDBOX_ACTIVITY_START_INITIATED);
        intent.putExtras(extras);
        return intent;
    }
}
