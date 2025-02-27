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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;
import android.platform.test.annotations.DisabledOnRavenwood;

import com.android.adservices.mockito.AndroidMocker;
import com.android.adservices.shared.testing.DeviceSideTestCase;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// NOTE: ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
/**
 * Base class for all {@link AndroidMocker} implementations.
 *
 * @param <T> mocker implementation
 */
@SuppressWarnings("DirectInvocationOnMock")
public abstract class AndroidMockerTestCase<T extends AndroidMocker> extends DeviceSideTestCase {

    private static final int FLAG = 42;

    private final Intent mIntent = new Intent();
    // Cannot initialize right away is it's only available on T+
    private ResolveInfoFlags mResolveInfoFlags;
    @Mock private PackageManager mMockPm;
    @Mock private Context mMockContext;

    @Rule public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    protected abstract T getMocker();

    @Before
    public final void verifyMocker() {
        assertWithMessage("getMocker()").that(getMocker()).isNotNull();
    }

    @Before
    public final void setFixtures() {
        if (sdkLevel.isAtLeastT()) {
            mResolveInfoFlags = ResolveInfoFlags.of(FLAG);
        }
    }

    @Test
    public final void testQueryIntentService_nullArgs() {
        T mocker = getMocker();
        assertThrows(NullPointerException.class, () -> mocker.mockQueryIntentService(null));
        assertThrows(
                NullPointerException.class,
                () -> mocker.mockQueryIntentService(mMockPm, (ResolveInfo[]) null));
    }

    @Test
    public final void testQueryIntentService_nullResolveInfo() {
        // queryIntentServices() Javadoc doesn't mention not returning null ResolveInfo objects in
        // the returned list, so it's not our business to care about that...
        getMocker().mockQueryIntentService(mMockPm, new ResolveInfo[] {null});

        var byIntFlag = mMockPm.queryIntentServices(mIntent, FLAG);
        expect.withMessage("queryIntentServices(intFlags)").that(byIntFlag).isNotNull();
        expect.withMessage("queryIntentServices(intFlags)")
                .that(byIntFlag)
                .containsExactly((ResolveInfo) null);
        if (sdkLevel.isAtLeastT()) {
            var byResolveInfoFlags = mMockPm.queryIntentServices(mIntent, mResolveInfoFlags);
            expect.withMessage("queryIntentServices(ResolveInfoFlags)")
                    .that(byResolveInfoFlags)
                    .isNotNull();
            expect.withMessage("queryIntentServices(ResolveInfoFlags)")
                    .that(byResolveInfoFlags)
                    .containsExactly((ResolveInfo) null);
        }
    }

    // TODO(b/335935200): context.getPackageManager() is not available on Ravenwood
    @DisabledOnRavenwood(blockedBy = Context.class)
    @Test
    public final void testQueryIntentService_notMock() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        getMocker()
                                .mockQueryIntentService(
                                        mContext.getPackageManager(), new ResolveInfo[] {null}));
    }

    @Test
    public final void testQueryIntentService_noArgs() {
        getMocker().mockQueryIntentService(mMockPm);

        var byIntFlag = mMockPm.queryIntentServices(mIntent, FLAG);
        expect.withMessage("queryIntentServices(intFlags)").that(byIntFlag).isEmpty();
        if (sdkLevel.isAtLeastT()) {
            var byResolveInfoFlags = mMockPm.queryIntentServices(mIntent, mResolveInfoFlags);
            expect.withMessage("queryIntentServices(ResolveInfoFlags)")
                    .that(byResolveInfoFlags)
                    .isEmpty();
        }
    }

    @Test
    public final void testQueryIntentService_multipleArgs() {
        ResolveInfo info1 = new ResolveInfo();
        info1.priority = 1;
        ResolveInfo info2 = new ResolveInfo();
        info2.priority = 2;

        getMocker().mockQueryIntentService(mMockPm, info1, info2);

        var byIntFlag = mMockPm.queryIntentServices(mIntent, FLAG);
        expect.withMessage("queryIntentServices(intFlags)")
                .that(byIntFlag)
                .containsExactly(info1, info2)
                .inOrder();
        if (sdkLevel.isAtLeastT()) {
            var byResolveInfoFlags = mMockPm.queryIntentServices(mIntent, mResolveInfoFlags);
            expect.withMessage("queryIntentServices(ResolveInfoFlags)")
                    .that(byResolveInfoFlags)
                    .containsExactly(info1, info2)
                    .inOrder();
        }
    }

    @Test
    public final void testMockGetApplicationContext_null() {
        assertThrows(
                NullPointerException.class,
                () -> getMocker().mockGetApplicationContext(/* mockContext= */ null, mMockContext));
    }

    @Test
    public final void testMockGetApplicationContext_notMock() {
        assertThrows(
                IllegalArgumentException.class,
                () -> getMocker().mockGetApplicationContext(mContext, mContext));
    }

    @Test
    public final void testMockGetApplicationContext() {
        getMocker().mockGetApplicationContext(mMockContext, mContext);

        var actual = mMockContext.getApplicationContext();

        expect.withMessage("getApplicationContext()").that(actual).isSameInstanceAs(mContext);
    }

    @Test
    public final void testMockGetApplicationContext_nullAppContext() {
        getMocker().mockGetApplicationContext(mMockContext, /* appContext= */ null);

        var actual = mMockContext.getApplicationContext();

        expect.withMessage("getApplicationContext()").that(actual).isNull();
    }
}
