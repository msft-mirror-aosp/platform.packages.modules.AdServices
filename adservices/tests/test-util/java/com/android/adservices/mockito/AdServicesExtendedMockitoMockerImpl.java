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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import android.app.ActivityManager;
import android.os.Binder;
import android.os.Process;
import android.util.Log;

import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.testing.LogEntry.Level;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Supplier;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import java.util.Objects;

public final class AdServicesExtendedMockitoMockerImpl implements AdServicesExtendedMockitoMocker {

    private static final String TAG = AdServicesExtendedMockitoMocker.class.getSimpleName();

    private final StaticClassChecker mStaticClassChecker;
    private final Supplier<String> mTestNameSupplier;

    public AdServicesExtendedMockitoMockerImpl(
            StaticClassChecker staticClassChecker, Supplier<String> testNameSupplier) {
        mStaticClassChecker = Objects.requireNonNull(staticClassChecker);
        mTestNameSupplier = Objects.requireNonNull(testNameSupplier);
    }

    @Override
    public void mockGetFlags(Flags mockedFlags) {
        logV("mockGetFlags(%s)", mockedFlags);
        assertSpiedOrMocked(FlagsFactory.class);
        doReturn(mockedFlags).when(FlagsFactory::getFlags);
    }

    @Override
    public void mockGetFlagsForTesting() {
        mockGetFlags(FakeFlagsFactory.getFlagsForTest());
    }

    @Override
    public void mockGetCallingUidOrThrow(int uid) {
        logV("mockGetCallingUidOrThrow(%d)", uid);
        mockBinderGetCallingUidOrThrow(uid);
    }

    @Override
    public void mockGetCallingUidOrThrow() {
        int uid = Process.myUid();
        logV("mockGetCallingUidOrThrow(Process.myUid=%d)", uid);
        mockBinderGetCallingUidOrThrow(uid);
    }

    @Override
    public void mockIsAtLeastR(boolean isIt) {
        logV("mockIsAtLeastR(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastR);
    }

    @Override
    public void mockIsAtLeastS(boolean isIt) {
        logV("mockIsAtLeastS(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastS);
    }

    @Override
    public void mockIsAtLeastT(boolean isIt) {
        logV("mockIsAtLeastT(%b)", isIt);
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(isIt).when(SdkLevel::isAtLeastT);
    }

    @Override
    public void mockSdkLevelR() {
        logV("mockSdkLevelR()");
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(true).when(SdkLevel::isAtLeastR);
        doReturn(false).when(SdkLevel::isAtLeastS);
        doReturn(false).when(SdkLevel::isAtLeastSv2);
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(false).when(SdkLevel::isAtLeastU);
    }

    @Override
    public void mockGetCurrentUser(int user) {
        logV("mockGetCurrentUser(user=%d)", user);
        assertSpiedOrMocked(ActivityManager.class);
        doReturn(user).when(ActivityManager::getCurrentUser);
    }

    @Override
    public LogInterceptor interceptLogV(String tag) {
        logV("interceptLogV(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, Level.VERBOSE);
    }

    @Override
    public LogInterceptor interceptLogE(String tag) {
        logV("interceptLogE(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, Level.ERROR);
    }

    @Override
    public void mockSpeJobScheduler(AdServicesJobScheduler mockedAdServicesJobScheduler) {
        logV("mockSpeJobScheduler(%s)", mockedAdServicesJobScheduler);
        assertSpiedOrMocked(AdServicesJobScheduler.class);
        doReturn(mockedAdServicesJobScheduler).when(AdServicesJobScheduler::getInstance);
    }

    @Override
    public void mockAdServicesJobServiceFactory(
            AdServicesJobServiceFactory mockedAdServicesJobServiceFactory) {
        logV("mockAdServicesJobServiceFactory(%s)", mockedAdServicesJobServiceFactory);
        assertSpiedOrMocked(AdServicesJobServiceFactory.class);
        doReturn(mockedAdServicesJobServiceFactory).when(AdServicesJobServiceFactory::getInstance);
    }

    // NOTE: current tests are only intercepting v and e, but we could add more methods on demand
    // (even one that takes Level...levels)

    @FormatMethod
    private void logV(@FormatString String fmt, Object... args) {
        Log.v(TAG, "on " + mTestNameSupplier.get() + ": " + String.format(fmt, args));
    }

    // mock only, don't log
    private void mockBinderGetCallingUidOrThrow(int uid) {
        assertSpiedOrMocked(Binder.class);
        doReturn(uid).when(Binder::getCallingUidOrThrow);
    }

    private void assertSpiedOrMocked(Class<?> clazz) {
        if (!mStaticClassChecker.isSpiedOrMocked(clazz)) {
            // TODO(b/314969513): show what's mocked (would require either a new method on
            // StaticClassChecker, or use it's toString();
            throw new IllegalStateException("Test doesn't static spy or mock " + clazz.getName());
        }
    }

    /** Trivial Javadoc used to make checkstyle happy. */
    public interface StaticClassChecker {
        /** Trivial Javadoc used to make checkstyle happy. */
        boolean isSpiedOrMocked(Class<?> clazz);
    }
}
