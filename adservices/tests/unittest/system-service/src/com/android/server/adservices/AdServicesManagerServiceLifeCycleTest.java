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
package com.android.server.adservices;

import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetLocalManager;
import static com.android.adservices.mockito.ExtendedMockitoExpectations.mockGetLocalManagerNotFound;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;
import com.android.server.LocalManagerRegistry;
import com.android.server.sdksandbox.SdkSandboxManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

public final class AdServicesManagerServiceLifeCycleTest {

    @Mock private Context mContext;
    @Mock private AdServicesManagerService mService;
    @Mock private SdkSandboxManagerLocal mSdkSandboxManagerLocal;

    private AdServicesManagerService.Lifecycle mLifecycle;
    private StaticMockitoSession mMockSession;

    // TODO(b/281577492): use ExtendedMockitoRule and remove these 2 session methods
    private void startMockitoSession() {
        mMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(LocalManagerRegistry.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void finishMockitoSession() {
        mMockSession.finishMocking();
    }

    @Before
    public void setUp() {
        startMockitoSession();
        mLifecycle = new AdServicesManagerService.Lifecycle(mContext, mService);
        mockGetLocalManager(SdkSandboxManagerLocal.class, mSdkSandboxManagerLocal);
    }

    @Ignore("Need to mock publishService(), will be done in a follow-up CL")
    @Test
    public void testOnStart_noSdkSandboxManagerLocal() {
        mockGetLocalManagerNotFound(SdkSandboxManagerLocal.class);

        assertThrows(IllegalStateException.class, () -> mLifecycle.onStart());
    }

    @Ignore("Need to mock publishService(), will be done in a follow-up CL")
    @Test
    public void testOnStart() {
        mLifecycle.onStart();

        verify(mSdkSandboxManagerLocal).registerAdServicesManagerService(mService, true);
    }
}
