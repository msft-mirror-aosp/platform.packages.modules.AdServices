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
package com.android.adservices.mockito;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

import android.util.Log;

import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.FlagsFactory;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.LocalManagerRegistry;

/**
 * Provides Mockito expectation for common calls.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 */
public final class ExtendedMockitoExpectations {

    private static final String TAG = ExtendedMockitoExpectations.class.getSimpleName();

    /**
     * Mocks a call to {@link LocalManagerRegistry#getManager(Class)}, returning the given {@code
     * manager}.
     */
    public static <T> void mockGetLocalManager(Class<T> managerClass, T manager) {
        Log.v(TAG, "mockGetLocalManager(" + managerClass + ", " + manager + ")");
        doReturn(manager).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    /** Mocks a call to {@link LocalManagerRegistry#getManager(Class)}, returning {@code null}. */
    public static void mockGetLocalManagerNotFound(Class<?> managerClass) {
        Log.v(TAG, "mockGetLocalManagerNotFound(" + managerClass + ")");
        doReturn(null).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastS()}, returning {@code isIt}. */
    public static void mockIsAtLeastS(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastS(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastS);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastSv2()}, returning {@code isIt}. */
    public static void mockIsAtLeastSv2(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastSv2(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastSv2);
    }

    /** Mocks a call to {@link SdkLevel#isAtLeastT()}, returning {@code isIt}. */
    public static void mockIsAtLeastT(boolean isIt) {
        Log.v(TAG, "mockIsAtLeastT(" + isIt + ")");
        doReturn(isIt).when(SdkLevel::isAtLeastT);
    }

    /**
     * Mocks a call to {@link ErrorLogUtil#e()}, does nothing.
     *
     * <p>Mocks behavior for both variants of the method.
     */
    public static void doNothingOnErrorLogUtilError() {
        doNothing().when(() -> ErrorLogUtil.e(any(), anyInt(), anyInt()));
        doNothing().when(() -> ErrorLogUtil.e(anyInt(), anyInt()));
    }

    /**
     * Mocks a call to {@link FlagsFactory#getFlags()}, returning {@link
     * FlagsFactory#getFlagsForTest()}
     */
    public static void mockGetFlagsForTest() {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);
    }

    /** Verifies {@link ErrorLogUtil#e()} was called with the expected values. */
    public static void verifyErrorLogUtilErrorWithException(
            int errorCode, int ppapiName, int numberOfInvocations) {
        verify(
                () -> {
                    ErrorLogUtil.e(any(), eq(errorCode), eq(ppapiName));
                },
                times(numberOfInvocations));
    }

    /** Verifies {@link ErrorLogUtil#e()} was called with the expected values. */
    public static void verifyErrorLogUtilError(
            int errorCode,
            int ppapiName,
            int numberOfInvocations) {
        verify(
                () -> {
                    ErrorLogUtil.e(eq(errorCode), eq(ppapiName));
                },
                times(numberOfInvocations));
    }

    private ExtendedMockitoExpectations() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
