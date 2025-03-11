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

import com.android.adservices.shared.testing.Logger.LogLevel;
import com.android.modules.utils.build.SdkLevel;

// TODO(b/324919960): add unit test
/**
 * {@link AndroidStaticMocker} implementation that uses {@code ExtendedMockito}.
 *
 * <p><b>NOTE: </b> most expectations require {@code spyStatic()} or {@code mockStatic()} in the
 * {@link com.android.dx.mockito.inline.extended.StaticMockitoSession session} ahead of time - this
 * helper doesn't check that such calls were made, it's up to the caller to do so.
 */
public final class AndroidExtendedMockitoMocker extends AbstractStaticMocker
        implements AndroidStaticMocker {

    // TODO(b/338132355): create helper class to implement StaticClassChecker from rule
    public AndroidExtendedMockitoMocker(StaticClassChecker staticClassChecker) {
        super(staticClassChecker);
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
        doReturn(false).when(SdkLevel::isAtLeastV);
    }

    @Override
    public void mockSdkLevelS() {
        logV("mockSdkLevelS()");
        assertSpiedOrMocked(SdkLevel.class);
        doReturn(true).when(SdkLevel::isAtLeastR);
        doReturn(true).when(SdkLevel::isAtLeastS);
        doReturn(false).when(SdkLevel::isAtLeastSv2);
        doReturn(false).when(SdkLevel::isAtLeastT);
        doReturn(false).when(SdkLevel::isAtLeastU);
        doReturn(false).when(SdkLevel::isAtLeastV);
    }

    @Override
    public void mockGetCurrentUser(int user) {
        logV("mockGetCurrentUser(user=%d)", user);
        assertSpiedOrMocked(ActivityManager.class);
        doReturn(user).when(ActivityManager::getCurrentUser);
    }

    @Override
    public LogInterceptor interceptLogD(String tag) {
        logV("interceptLogD(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, LogLevel.DEBUG);
    }

    @Override
    public LogInterceptor interceptLogV(String tag) {
        logV("interceptLogV(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, LogLevel.VERBOSE);
    }

    @Override
    public LogInterceptor interceptLogE(String tag) {
        logV("interceptLogE(%s)", tag);
        assertSpiedOrMocked(Log.class);

        return LogInterceptor.forTagAndLevels(tag, LogLevel.ERROR);
    }

    // mock only, don't log
    private void mockBinderGetCallingUidOrThrow(int uid) {
        assertSpiedOrMocked(Binder.class);
        doReturn(uid).when(Binder::getCallingUidOrThrow);
    }
}
