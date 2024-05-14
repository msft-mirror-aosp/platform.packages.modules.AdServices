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

import static org.junit.Assert.assertThrows;

import android.os.Binder;
import android.os.Process;

import com.android.adservices.mockito.AbstractStaticMocker.ClassNotSpiedOrMockedException;
import com.android.adservices.shared.SharedExtendedMockitoTestCase;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.junit.function.ThrowingRunnable;

public final class AndroidExtendedMockitoMockerTest extends SharedExtendedMockitoTestCase {

    private final AndroidExtendedMockitoMocker mMocker =
            new AndroidExtendedMockitoMocker(extendedMockito);

    @Test
    public void testMockGetCallingUidOrThrow_noArgs_notSpied() {
        assertFailsWhenClassNotSpied(Binder.class, () -> mMocker.mockGetCallingUidOrThrow());
    }

    @Test
    @SpyStatic(Binder.class)
    public void testMockGetCallingUidOrThrow_noArgs() {
        int myUid = Process.myUid();

        mMocker.mockGetCallingUidOrThrow();

        expect.withMessage("uid").that(Binder.getCallingUidOrThrow()).isEqualTo(myUid);
    }

    @Test
    public void testMockGetCallingUidOrThrow_notSpied() {
        assertFailsWhenClassNotSpied(Binder.class, () -> mMocker.mockGetCallingUidOrThrow(42));
    }

    @Test
    @SpyStatic(Binder.class)
    public void testMockGetCallingUidOrThrow() {
        mMocker.mockGetCallingUidOrThrow(42);

        expect.withMessage("uid").that(Binder.getCallingUidOrThrow()).isEqualTo(42);
    }

    private void assertFailsWhenClassNotSpied(Class<?> clazz, ThrowingRunnable r) {
        ClassNotSpiedOrMockedException e = assertThrows(ClassNotSpiedOrMockedException.class, r);
        expect.withMessage("missing class").that(e.getMissingClass()).isEqualTo(clazz);
        expect.withMessage("spied / mocked classes").that(e.getSpiedOrMockedClasses()).isEmpty();
    }
}
