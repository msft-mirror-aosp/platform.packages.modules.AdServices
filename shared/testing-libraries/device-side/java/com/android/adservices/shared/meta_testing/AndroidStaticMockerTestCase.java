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

import android.os.Binder;
import android.os.Process;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.mockito.AbstractStaticMocker.ClassNotSpiedOrMockedException;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.mockito.AndroidStaticMocker;
import com.android.adservices.mockito.StaticClassChecker;
import com.android.adservices.shared.testing.DeviceSideTestCase;
import com.android.modules.utils.build.SdkLevel;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.quality.Strictness;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
/**
 * Base class for all {@link AndroidStaticMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
@DisabledOnRavenwood(reason = "Uses ExtendedMockito") // TODO(b/335935200): fix this
public abstract class AndroidStaticMockerTestCase<T extends AndroidStaticMocker>
        extends DeviceSideTestCase {

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    protected abstract T getMocker(StaticClassChecker checker);

    private T getMocker() {
        return getMocker(extendedMockito);
    }

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker(extendedMockito)).isNotNull();
    }

    @Test
    public final void testMockGetCallingUidOrThrow_noArgs_notSpied() {
        assertFailsWhenClassNotSpied(Binder.class, () -> getMocker().mockGetCallingUidOrThrow());
    }

    @Test
    @SpyStatic(Binder.class)
    public final void testMockGetCallingUidOrThrow_noArgs() {
        int myUid = Process.myUid();

        getMocker().mockGetCallingUidOrThrow();

        expect.withMessage("uid").that(Binder.getCallingUidOrThrow()).isEqualTo(myUid);
    }

    @Test
    public final void testMockGetCallingUidOrThrow_notSpied() {
        assertFailsWhenClassNotSpied(Binder.class, () -> getMocker().mockGetCallingUidOrThrow(42));
    }

    @Test
    @SpyStatic(Binder.class)
    public final void testMockGetCallingUidOrThrow() {
        getMocker().mockGetCallingUidOrThrow(42);

        expect.withMessage("uid").that(Binder.getCallingUidOrThrow()).isEqualTo(42);
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public final void testMockIsAtLeastR() {
        getMocker().mockIsAtLeastR(true);
        expect.withMessage("isAtLeastR()").that(SdkLevel.isAtLeastR()).isTrue();

        getMocker().mockIsAtLeastR(false);
        expect.withMessage("isAtLeastR()").that(SdkLevel.isAtLeastR()).isFalse();
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public final void testMockIsAtLeastS() {
        getMocker().mockIsAtLeastS(true);
        expect.withMessage("isAtLeastS()").that(SdkLevel.isAtLeastS()).isTrue();

        getMocker().mockIsAtLeastS(false);
        expect.withMessage("isAtLeastS()").that(SdkLevel.isAtLeastS()).isFalse();
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public final void testMockIsAtLeastT() {
        getMocker().mockIsAtLeastT(true);
        expect.withMessage("isAtLeastT()").that(SdkLevel.isAtLeastT()).isTrue();

        getMocker().mockIsAtLeastT(false);
        expect.withMessage("isAtLeastT()").that(SdkLevel.isAtLeastT()).isFalse();
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public final void testMockSdkLevelR() {
        getMocker().mockSdkLevelR();

        expect.withMessage("isAtLeastR").that(SdkLevel.isAtLeastR()).isTrue();
        expect.withMessage("isAtLeastS").that(SdkLevel.isAtLeastS()).isFalse();
        expect.withMessage("isAtLeastSv2").that(SdkLevel.isAtLeastSv2()).isFalse();
        expect.withMessage("isAtLeastT").that(SdkLevel.isAtLeastT()).isFalse();
        expect.withMessage("isAtLeastU").that(SdkLevel.isAtLeastU()).isFalse();
        expect.withMessage("isAtLeastV").that(SdkLevel.isAtLeastV()).isFalse();
    }

    @Test
    @SpyStatic(SdkLevel.class)
    public final void testMockSdkLevelS() {
        getMocker().mockSdkLevelS();

        expect.withMessage("isAtLeastR").that(SdkLevel.isAtLeastR()).isTrue();
        expect.withMessage("isAtLeastS").that(SdkLevel.isAtLeastS()).isTrue();
        expect.withMessage("isAtLeastSv2").that(SdkLevel.isAtLeastSv2()).isFalse();
        expect.withMessage("isAtLeastT").that(SdkLevel.isAtLeastT()).isFalse();
        expect.withMessage("isAtLeastU").that(SdkLevel.isAtLeastU()).isFalse();
        expect.withMessage("isAtLeastV").that(SdkLevel.isAtLeastV()).isFalse();
    }

    private void assertFailsWhenClassNotSpied(Class<?> clazz, ThrowingRunnable r) {
        ClassNotSpiedOrMockedException e = assertThrows(ClassNotSpiedOrMockedException.class, r);
        expect.withMessage("missing class").that(e.getMissingClass()).isEqualTo(clazz);
        expect.withMessage("spied / mocked classes").that(e.getSpiedOrMockedClasses()).isEmpty();
    }
}
