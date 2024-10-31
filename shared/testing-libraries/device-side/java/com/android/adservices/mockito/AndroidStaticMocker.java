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

package com.android.adservices.mockito;

/** Helper interface providing common expectations for static methods on Android SDK. */
public interface AndroidStaticMocker {

    /**
     * Mocks a call to {@link Binder#getCallingUidOrThrow()}, returning {@code uid}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Binder}.
     */
    void mockGetCallingUidOrThrow(int uid);

    /**
     * Same as {@link #mockGetCallingUidOrThrow(int)}, but using the {@code uid} of the calling
     * process.
     *
     * <p>Typically used when code under test calls {@link Binder#getCallingUidOrThrow()} and the
     * test doesn't care about the result, but it needs to be mocked otherwise the real call would
     * fail (as the test is not running inside a binder transaction).
     */
    void mockGetCallingUidOrThrow();

    /** Mocks a call to {@link SdkLevel#isAtLeastR()}, returning {@code isIt}. */
    void mockIsAtLeastR(boolean isIt);

    /** Mocks a call to {@link SdkLevel#isAtLeastS()}, returning {@code isIt}. */
    void mockIsAtLeastS(boolean isIt);

    /** Mocks a call to {@link SdkLevel#isAtLeastT()}, returning {@code isIt}. */
    void mockIsAtLeastT(boolean isIt);

    /** Mocks a call to SDK level to return R */
    void mockSdkLevelR();

    /** Mocks a call to SDK level to return S */
    void mockSdkLevelS();

    /**
     * Mocks a call to {@link ActivityManager#getCurrentUser()}, returning {@code user}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link ActivityManager}.
     */
    void mockGetCurrentUser(int user);

    // NOTE: current tests are only intercepting d, ,  and e, but we could add more methods on
    // demand (even one that takes Level...levels)

    /**
     * Statically spy on {@code Log.d} for that {@code tag}.
     *
     * @return object that can be used to assert the {@code Log.d} calls.
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Log}.
     */
    LogInterceptor interceptLogD(String tag);

    /**
     * Statically spy on {@code Log.v} for that {@code tag}.
     *
     * @return object that can be used to assert the {@code Log.v} calls.
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Log}.
     */
    LogInterceptor interceptLogV(String tag);

    /**
     * Statically spy on {@code Log.e} for that {@code tag}.
     *
     * @return object that can be used to assert the {@code Log.e} calls.
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link Log}.
     */
    LogInterceptor interceptLogE(String tag);
}
