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

import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;

// TODO(b/324919960): rename to AdServicesStaticMocker for consistency
/** Helper interface providing common expectations for static methods on AdServices APIs. */
public interface AdServicesExtendedMockitoMocker {

    /**
     * Mocks a call of {@link FlagsFactory#getFlags()} to return the passed-in mocking {@link Flags}
     * object.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link FlagsFactory}.
     */
    void mockGetFlags(Flags mockedFlags);

    /**
     * Mocks a call to {@link FlagsFactory#getFlags()}, returning {@link
     * FakeFlagsFactory#getFlagsForTest()}
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link FlagsFactory}.
     */
    void mockGetFlagsForTesting();

    // TODO(b/338132355): move SPE methods to a new SharedMocker interface

    /**
     * Mocks a call to {@link AdServicesJobScheduler#getInstance()}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link AdServicesJobScheduler}.
     */
    void mockSpeJobScheduler(AdServicesJobScheduler mockedAdServicesJobScheduler);

    /**
     * Mocks a call to {@link AdServicesJobServiceFactory#getInstance()}.
     *
     * @throws IllegalStateException if test didn't call {@code spyStatic} / {@code mockStatic} (or
     *     equivalent annotations) on {@link AdServicesJobServiceFactory}.
     */
    void mockAdServicesJobServiceFactory(
            AdServicesJobServiceFactory mockedAdServicesJobServiceFactory);
}
