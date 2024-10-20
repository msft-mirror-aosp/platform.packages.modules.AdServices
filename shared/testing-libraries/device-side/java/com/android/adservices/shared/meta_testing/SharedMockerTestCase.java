/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.shared.meta_testing;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.content.Context;

import com.android.adservices.mockito.SharedMocker;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.testing.DeviceSideTestCase;
import com.android.adservices.shared.util.Clock;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

/**
 * Base class for all {@link SharedMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("VisibleForTests") // TODO(b/343741206): Remove suppress warning once fixed.
public abstract class SharedMockerTestCase<T extends SharedMocker> extends DeviceSideTestCase {

    @Mock private Clock mMockClock;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    // TODO(b/285300419): use ApplicationContextSingleton rule / helper instead?
    @Test
    public final void testSetApplicationContextSingleton() {
        Context contextBefore = ApplicationContextSingleton.getForTests();
        try {
            Context context = getMocker().setApplicationContextSingleton();

            assertWithMessage("context").that(context).isNotNull();
            expect.withMessage("context").that(context).isNotSameInstanceAs(contextBefore);
            expect.withMessage("context.getApplicationContext()")
                    .that(context.getApplicationContext())
                    .isSameInstanceAs(context);
            expect.withMessage("ApplicationContextSingleton.get()")
                    .that(ApplicationContextSingleton.get())
                    .isSameInstanceAs(context);
        } finally {
            ApplicationContextSingleton.setForTests(contextBefore);
        }
    }

    @Test
    public void testMockSetApplicationContextSingleton_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockSetApplicationContextSingleton(null));
    }

    @Test
    public void testMockSetApplicationContextSingleton() {
        Context contextBefore = ApplicationContextSingleton.getForTests();
        try {
            getMocker().mockSetApplicationContextSingleton(mContext);

            expect.withMessage("ApplicationContextSingleton.get()")
                    .that(ApplicationContextSingleton.get())
                    .isSameInstanceAs(mContext);
        } finally {
            ApplicationContextSingleton.setForTests(contextBefore);
        }
    }

    @Test
    public final void testMockCurrentTimeMillis_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockCurrentTimeMillis(mMockClock, /* mockedValues...= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockCurrentTimeMillis(/* mockClock= */ null, 42));
    }

    @Test
    public final void testMockCurrentTimeMillis_oneValue() {
        getMocker().mockCurrentTimeMillis(mMockClock, 42);

        expect.withMessage("clock.currentTimeMillis()")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(42);
    }

    @Test
    public final void testMockCurrentTimeMillis_multipleValues() {
        getMocker().mockCurrentTimeMillis(mMockClock, 4, 8, 15, 16, 23, 42);

        expect.withMessage("1st clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(4);
        expect.withMessage("2nd clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(8);
        expect.withMessage("3rd clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(15);
        expect.withMessage("4th clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(16);
        expect.withMessage("5th clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(23);
        expect.withMessage("6th clock.currentTimeMillis() call")
                .that(mMockClock.currentTimeMillis())
                .isEqualTo(42);
    }

    @Test
    public final void testMockElapsedRealtime_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockElapsedRealtime(mMockClock, /* mockedValues...= */ null));
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockElapsedRealtime(/* mockClock= */ null, 42));
    }

    @Test
    public final void testMockElapsedRealtime_oneValue() {
        getMocker().mockElapsedRealtime(mMockClock, 42);

        expect.withMessage("clock.elapsedRealtime()")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(42);
    }

    @Test
    public final void testMockElapsedRealtime_multipleValues() {
        getMocker().mockElapsedRealtime(mMockClock, 4, 8, 15, 16, 23, 42);

        expect.withMessage("1st clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(4);
        expect.withMessage("2nd clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(8);
        expect.withMessage("3rd clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(15);
        expect.withMessage("4th clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(16);
        expect.withMessage("5th clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(23);
        expect.withMessage("6th clock.elapsedRealtime() call")
                .that(mMockClock.elapsedRealtime())
                .isEqualTo(42);
    }
}
