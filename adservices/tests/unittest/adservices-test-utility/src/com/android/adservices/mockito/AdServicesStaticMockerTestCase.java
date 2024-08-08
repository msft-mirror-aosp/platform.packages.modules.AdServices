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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import com.android.adservices.common.AdServicesUnitTestCase;
import com.android.adservices.mockito.AbstractStaticMocker.ClassNotSpiedOrMockedException;
import com.android.adservices.service.FakeFlagsFactory;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.spe.AdServicesJobScheduler;
import com.android.adservices.spe.AdServicesJobServiceFactory;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
/**
 * Base class for all {@link AdServicesStaticMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AdServicesStaticMockerTestCase<T extends AdServicesStaticMocker>
        extends AdServicesUnitTestCase {

    @Mock private Flags mMockFlags;

    @Mock private AdServicesJobScheduler mMockAdServicesJobScheduler;

    @Mock private AdServicesJobServiceFactory mMockAdServicesJobServiceFactory;

    @Mock private AdServicesLoggerImpl mMockAdServicesLoggerImpl;

    @Rule(order = 11)
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    protected abstract T getMocker(StaticClassChecker checker);

    private T getMocker() {
        return getMocker(extendedMockito);
    }

    // TODO(b/314969513): workaround because ExtendedMockitoExpectationsMockerTest always returns
    // the same mocker (ExtendedMockitoExpectations.mocker) - should be removed once that class is
    // gone
    private T getMockerWithStaticClassChecker() {
        T mocker = getMocker();
        var checker = mocker.getStaticClassChecker();
        assumeTrue(
                "mocker uses a different checker ("
                        + checker
                        + " instead of "
                        + extendedMockito
                        + ")",
                checker == extendedMockito);
        return mocker;
    }

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker(extendedMockito)).isNotNull();
    }

    @Test
    public final void testMockGetFlags_null() {
        assertThrows(NullPointerException.class, () -> getMocker().mockGetFlags(null));
    }

    @Test
    public final void testMockGetFlags_staticClassNotMocked() {
        T mocker = getMockerWithStaticClassChecker();
        assertThrows(ClassNotSpiedOrMockedException.class, () -> mocker.mockGetFlags(mMockFlags));
    }

    @Test
    @MockStatic(FlagsFactory.class)
    public final void testMockGetFlags() {
        getMocker().mockGetFlags(mMockFlags);

        var actual = FlagsFactory.getFlags();

        expect.withMessage("FlagsFactory.getFlags()").that(actual).isSameInstanceAs(mMockFlags);
    }

    @Test
    @MockStatic(FlagsFactory.class)
    public final void testMockGetFlagsForTesting() {
        getMocker().mockGetFlagsForTesting();

        var actual = FlagsFactory.getFlags();

        expect.withMessage("FlagsFactory.getFlags()").that(actual).isNotNull();
        expect.withMessage("FlagsFactory.getFlags()")
                .that(actual)
                .isInstanceOf(FakeFlagsFactory.TestFlags.class);
    }

    @Test
    public final void testMockSpeJobScheduler_null() {
        assertThrows(NullPointerException.class, () -> getMocker().mockSpeJobScheduler(null));
    }

    @Test
    public final void testMockSpeJobScheduler_staticClassNotMocked() {
        T mocker = getMockerWithStaticClassChecker();
        assertThrows(
                ClassNotSpiedOrMockedException.class,
                () -> mocker.mockSpeJobScheduler(mMockAdServicesJobScheduler));
    }

    // TODO(b/357944639): remove annotation if AdServicesJobScheduler doesn't depend on it anymore
    @SuppressWarnings("NewApi")
    @Test
    @MockStatic(AdServicesJobScheduler.class)
    public final void testMockSpeJobScheduler() {
        getMocker().mockSpeJobScheduler(mMockAdServicesJobScheduler);

        var actual = AdServicesJobScheduler.getInstance();

        expect.withMessage("AdServicesJobScheduler.getInstance()")
                .that(actual)
                .isSameInstanceAs(mMockAdServicesJobScheduler);
    }

    @Test
    public final void testMockAdServicesLoggerImpl_null() {
        assertThrows(NullPointerException.class, () -> getMocker().mockAdServicesLoggerImpl(null));
    }

    @Test
    public final void testMockAdServicesLoggerImpl_staticClassNotMocked() {
        T mocker = getMockerWithStaticClassChecker();
        assertThrows(
                ClassNotSpiedOrMockedException.class,
                () -> mocker.mockAdServicesLoggerImpl(mMockAdServicesLoggerImpl));
    }

    @Test
    @MockStatic(AdServicesLoggerImpl.class)
    public final void testMockAdServicesLoggerImpl() {
        getMocker().mockAdServicesLoggerImpl(mMockAdServicesLoggerImpl);

        var actual = AdServicesLoggerImpl.getInstance();

        expect.withMessage("AdServicesLoggerImpl.getInstance()")
                .that(actual)
                .isSameInstanceAs(mMockAdServicesLoggerImpl);
    }
}
