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
package com.android.adservices.shared.testing;

import static com.android.adservices.shared.testing.Logger.LogLevel.DEBUG;
import static com.android.adservices.shared.testing.Logger.LogLevel.ERROR;
import static com.android.adservices.shared.testing.Logger.LogLevel.INFO;
import static com.android.adservices.shared.testing.Logger.LogLevel.VERBOSE;
import static com.android.adservices.shared.testing.Logger.LogLevel.WARNING;
import static com.android.adservices.shared.testing.Logger.LogLevel.WTF;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import android.util.Log;

import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(Log.class)
public final class DeviceSideDynamicLoggerTest extends SharedExtendedMockitoTestCase {

    private final Throwable mThrowable = new Throwable("D'OH!");
    private final DynamicLogger mLogger = DynamicLogger.getInstance();

    @Test
    public void testToString() {
        expect.withMessage("toString()")
                .that(mLogger.toString())
                .isEqualTo("DynamicLogger[android.util.Log]");
    }

    @Test
    public void testWtf() {
        // Log.wtf() with some Android Configurations will trigger a process to terminate.
        doReturn(0).when(() -> Log.wtf(anyString(), anyString()));
        mLogger.log(WTF, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.wtf(mTag, "message in a bottle"));
    }

    @Test
    public void testWtf_withThrowable() {
        // Log.wtf() with some Android Configurations will trigger a process to terminate.
        doReturn(0).when(() -> Log.wtf(anyString(), anyString(), any()));
        mLogger.log(WTF, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.wtf(mTag, "message in a bottle", mThrowable));
    }

    @Test
    public void testE() {
        mLogger.log(ERROR, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.e(mTag, "message in a bottle"));
    }

    @Test
    public void testE_withThrowable() {
        mLogger.log(ERROR, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.e(mTag, "message in a bottle", mThrowable));
    }

    @Test
    public void testW() {
        mLogger.log(WARNING, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.w(mTag, "message in a bottle"));
    }

    @Test
    public void testW_withThrowable() {
        mLogger.log(WARNING, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.w(mTag, "message in a bottle", mThrowable));
    }

    @Test
    public void testI() {
        mLogger.log(INFO, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.i(mTag, "message in a bottle"));
    }

    @Test
    public void testI_withThrowable() {
        mLogger.log(INFO, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.i(mTag, "message in a bottle", mThrowable));
    }

    @Test
    public void testD() {
        mLogger.log(DEBUG, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.d(mTag, "message in a bottle"));
    }

    @Test
    public void testD_withThrowable() {
        mLogger.log(DEBUG, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.d(mTag, "message in a bottle", mThrowable));
    }

    @Test
    public void testV() {
        mLogger.log(VERBOSE, mTag, "%s %s", "message", "in a bottle");

        verify(() -> Log.v(mTag, "message in a bottle"));
    }

    @Test
    public void testV_withThrowable() {
        mLogger.log(VERBOSE, mTag, mThrowable, "%s %s", "message", "in a bottle");

        verify(() -> Log.v(mTag, "message in a bottle", mThrowable));
    }
}
