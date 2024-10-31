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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.shared.system.SystemContextSingleton;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;
import com.android.server.LocalManagerRegistry;
import com.android.server.sdksandbox.SdkSandboxManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SuppressWarnings("VisibleForTests") // TODO(b/343741206): remove when linter is fixed
@SpyStatic(LocalManagerRegistry.class)
public final class AdServicesManagerServiceLifeCycleTest extends AdServicesExtendedMockitoTestCase {

    @Mock private AdServicesManagerService mService;
    @Mock private SdkSandboxManagerLocal mSdkSandboxManagerLocal;

    // Need to use a spy to mock publishBinderService()
    private AdServicesManagerService.Lifecycle mSpyLifecycle;

    private Context mPreviousSystemContextSingleton;

    @Before
    public void setUp() {
        mPreviousSystemContextSingleton = SystemContextSingleton.setForTests(null);
        mSpyLifecycle = spy(new AdServicesManagerService.Lifecycle(mContext, mService));
        doNothing().when(mSpyLifecycle).publishBinderService();
        mockGetLocalManager(SdkSandboxManagerLocal.class, mSdkSandboxManagerLocal);
    }

    // TODO(b/285300419): use rule instead / remove this method and mPreviousSystemContextSingleton
    @After
    public void resetSystemContextSingleton() {
        SystemContextSingleton.setForTests(mPreviousSystemContextSingleton);
    }

    @Test
    public void testSystemSingletonContextSetOnConstructor() {
        // Should not have being set by test-only constructor
        expect.withMessage("SystemContextSingleton.get() after constructor from @Before")
                .that(SystemContextSingleton.getForTests())
                .isSameInstanceAs(mPreviousSystemContextSingleton);

        // It should be nill, but better reset in case some tests somehow set it...
        SystemContextSingleton.setForTests(null);

        // Constructor mitght throw an exception - like SecurityException when trying to register a
        // DeviceConfigReceiver - but it doesn't matter: at that point it should have set the
        // singleton already
        try {
            @SuppressWarnings("UnusedVariable")
            var unused = new AdServicesManagerService.Lifecycle(mMockContext);
        } catch (RuntimeException e) {
            mLog.d("Ignoring exception thrown at constructor: %s", e);
        }

        expect.withMessage("SystemContextSingleton.get() after new constructor")
                .that(SystemContextSingleton.get())
                .isSameInstanceAs(mMockContext);
    }

    @Test
    public void testOnStart_noSdkSandboxManagerLocal() {
        mockGetLocalManagerNotFound(SdkSandboxManagerLocal.class);

        assertThrows(IllegalStateException.class, () -> mSpyLifecycle.onStart());
    }

    @Test
    public void testOnStart_binderRegistrationFails() {
        doThrow(new RuntimeException("D'OH!")).when(mSpyLifecycle).publishBinderService();

        mSpyLifecycle.onStart();

        verifyBinderPublished();
        verifyAdServiceRegisteredOnSdkManager(/* published= */ false);
    }

    @Test
    public void testOnStart() {
        mSpyLifecycle.onStart();

        verifyBinderPublished();
        verifyAdServiceRegisteredOnSdkManager(/* published= */ true);
    }

    private <T> void mockGetLocalManager(Class<T> managerClass, T manager) {
        mLog.v("mockGetLocalManager(%s, %s)", managerClass, manager);
        doReturn(manager).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    private void mockGetLocalManagerNotFound(Class<?> managerClass) {
        mLog.v("mockGetLocalManagerNotFound(%s)", managerClass);
        doReturn(null).when(() -> LocalManagerRegistry.getManager(managerClass));
    }

    private void verifyAdServiceRegisteredOnSdkManager(boolean published) {
        verify(mSdkSandboxManagerLocal).registerAdServicesManagerService(mService, published);
    }

    private void verifyBinderPublished() {
        verify(mSpyLifecycle).publishBinderService();
    }
}
