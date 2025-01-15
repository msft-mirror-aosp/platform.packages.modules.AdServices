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

package com.android.adservices.service.measurement.inputverification;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.hardware.input.InputManager;
import android.os.Parcel;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VerifiedMotionEvent;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementClickVerificationStats;
import com.android.modules.utils.testing.TestableDeviceConfig;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.concurrent.TimeUnit;

public final class ClickVerifierTest extends AdServicesExtendedMockitoTestCase {

    private static final String SOURCE_REGISTRANT = "source_registrant";
    private static final long MEASUREMENT_WINDOW_ONE_MINUTE = TimeUnit.MINUTES.toMillis(1);

    @Mock private InputManager mInputManager;

    @Mock private AdServicesLogger mAdServicesLogger;

    @Mock private VerifiedMotionEvent mVerifiedMotionEvent;

    private ClickVerifier mClickVerifier;

    @Override
    protected AdServicesExtendedMockitoRule getAdServicesExtendedMockitoRule() {
        return newDefaultAdServicesExtendedMockitoRuleBuilder()
                .addStaticMockFixtures(TestableDeviceConfig::new)
                .build();
    }

    @Before
    public void before() {
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs()).thenReturn(100L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(false);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(false);
        mClickVerifier = new ClickVerifier(mInputManager, mMockFlags, mAdServicesLogger);
    }

    @Test
    public void testInputEventOutsideTimeRangeReturnsFalse() {
        InputEvent eventOutsideRange = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(eventOutsideRange)).thenReturn(mVerifiedMotionEvent);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs() + 1;
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                eventOutsideRange, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testVerifiedInputEventInsideTimeRangeReturnsTrue() {
        InputEvent eventInsideRange = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(eventInsideRange)).thenReturn(mVerifiedMotionEvent);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                eventInsideRange, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testInputEventNotValidatedBySystem() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testInputEvent_verifyByInputEventFlagDisabled_Verified() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testVerifiedInputEvent_underUseLimit_verified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testVerifiedInputEvent_overUseLimit_notVerified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testMultipleUnverifiedInputEvents_allVerified() {
        // Setup
        InputEvent inputEvent1 = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        InputEvent inputEvent2 = MotionEvent.obtain(10, 10, MotionEvent.ACTION_DOWN, 0, 0, 0);
        InputEvent inputEvent3 = MotionEvent.obtain(20, 20, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(any())).thenReturn(null);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent1, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent2, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent3, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testUnverifiedInputEvent_motionEvent_underUseLimit_verified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testUnverifiedInputEvent_keyEvent_underUseLimit_verified() {
        // Setup
        InputEvent inputEvent = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testUnverifiedInputEvent_motionEvent_overUseLimit_notVerified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testCopiedUnverifiedInputEvent_overUseLimit_notVerified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(false);
        when(mInputManager.verifyInputEvent(any())).thenReturn(null);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();

        // Copied InputEvent should be equal to the original InputEvent.
        InputEvent copiedInputEvent = MotionEvent.obtain((MotionEvent) inputEvent);

        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                copiedInputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();

        // Parcelled and un-parcelled InputEvent should be equal to the original InputEvent.
        Parcel p = Parcel.obtain();
        inputEvent.writeToParcel(p, /* flags */ 0);
        p.setDataPosition(0);
        InputEvent parcelInputEvent = InputEvent.CREATOR.createFromParcel(p);
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                parcelInputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isFalse();
    }

    @Test
    public void testClickDeduplicationNotEnabled_inputEventStillVerified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(false);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testClickDeduplicationNotEnforced_inputEventStillVerified() {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long registerTimestamp = mFakeFlags.getMeasurementRegistrationInputEventValidWindowMs();
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(registerTimestamp);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(false);

        // Execution and verification.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testClickDeduplication_cachedInputEventIsEvictedAfterWindowHasPassed()
            throws InterruptedException {
        // Setup
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        long mockedWindow = 200L;
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(/* value */ mockedWindow);
        long registerTimestamp = mockedWindow;
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);

        // Execute
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();

        // Wait enough time has passed that the InputEvent should have been evicted from the cache.
        Thread.sleep(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs());

        // The second call should pass since the entry has been evicted.
        assertThat(
                        mClickVerifier.isInputEventVerifiable(
                                inputEvent, registerTimestamp, SOURCE_REGISTRANT))
                .isTrue();
    }

    @Test
    public void testLog_inputEventVerified() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(MEASUREMENT_WINDOW_ONE_MINUTE);
        long registerTimestamp = MEASUREMENT_WINDOW_ONE_MINUTE / 2;
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.NAVIGATION.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mMockFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(true)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setClickDeduplicationEnabled(true)
                        .setClickDeduplicationEnforced(true)
                        .setMaxSourcesPerClick(mMockFlags.getMeasurementMaxSourcesPerClick())
                        .setCurrentRegistrationUnderClickDeduplicationLimit(true)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }

    @Test
    public void testLogClickVerification_inputEventNotVerifiedBySystem() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(null);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(MEASUREMENT_WINDOW_ONE_MINUTE);
        long registerTimestamp = MEASUREMENT_WINDOW_ONE_MINUTE / 2;
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.EVENT.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mMockFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(false)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setClickDeduplicationEnabled(true)
                        .setClickDeduplicationEnforced(true)
                        .setMaxSourcesPerClick(mMockFlags.getMeasurementMaxSourcesPerClick())
                        .setCurrentRegistrationUnderClickDeduplicationLimit(true)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }

    @Test
    public void testLogClickVerification_inputEventOutsideValidWindow() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(MEASUREMENT_WINDOW_ONE_MINUTE);
        long registerTimestamp = MEASUREMENT_WINDOW_ONE_MINUTE * 2;
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.EVENT.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mMockFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(true)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setClickDeduplicationEnabled(true)
                        .setClickDeduplicationEnforced(true)
                        .setMaxSourcesPerClick(mMockFlags.getMeasurementMaxSourcesPerClick())
                        .setCurrentRegistrationUnderClickDeduplicationLimit(true)
                        .build();

        verify(mAdServicesLogger).logMeasurementClickVerificationStats(eq(stats));
    }

    @Test
    public void testLogClickVerification_inputEventIsDuplicate() {
        InputEvent inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 0, 0, 0);
        when(mInputManager.verifyInputEvent(inputEvent)).thenReturn(mVerifiedMotionEvent);
        when(mMockFlags.getMeasurementIsClickVerifiedByInputEvent()).thenReturn(true);
        when(mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                .thenReturn(MEASUREMENT_WINDOW_ONE_MINUTE);
        long registerTimestamp = MEASUREMENT_WINDOW_ONE_MINUTE * 2;
        when(mMockFlags.getMeasurementIsClickDeduplicationEnabled()).thenReturn(true);
        when(mMockFlags.getMeasurementIsClickDeduplicationEnforced()).thenReturn(true);
        when(mMockFlags.getMeasurementMaxSourcesPerClick()).thenReturn(1L);
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);
        mClickVerifier.isInputEventVerifiable(inputEvent, registerTimestamp, SOURCE_REGISTRANT);

        MeasurementClickVerificationStats stats =
                MeasurementClickVerificationStats.builder()
                        .setSourceType(Source.SourceType.EVENT.getIntValue())
                        .setInputEventPresent(true)
                        .setSystemClickVerificationEnabled(
                                mMockFlags.getMeasurementIsClickVerifiedByInputEvent())
                        .setSystemClickVerificationSuccessful(true)
                        .setInputEventDelayMillis(registerTimestamp)
                        .setValidDelayWindowMillis(
                                mMockFlags.getMeasurementRegistrationInputEventValidWindowMs())
                        .setSourceRegistrant(SOURCE_REGISTRANT)
                        .setClickDeduplicationEnabled(true)
                        .setClickDeduplicationEnforced(true)
                        .setMaxSourcesPerClick(mMockFlags.getMeasurementMaxSourcesPerClick())
                        .setCurrentRegistrationUnderClickDeduplicationLimit(false)
                        .build();

        ArgumentCaptor<MeasurementClickVerificationStats> argCaptor =
                ArgumentCaptor.forClass(MeasurementClickVerificationStats.class);
        verify(mAdServicesLogger, times(2))
                .logMeasurementClickVerificationStats(argCaptor.capture());
        assertEquals(stats, argCaptor.getAllValues().get(1));
    }
}
