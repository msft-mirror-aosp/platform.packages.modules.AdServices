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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.ResolveInfoFlags;
import android.content.pm.ResolveInfo;

import com.android.adservices.shared.SharedMockitoTestCase;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

// ErrorProne complains that mock objects should not be called directly, but in this test
// they need to, as the test verifies that they would return what is set by the mock
// expectaction methods.
@SuppressWarnings("DirectInvocationOnMock")
public final class AndroidMockitoMockerTest extends SharedMockitoTestCase {

    private static final int FLAG = 42;
    // Cannot initialize right away is it's only available on T+
    private ResolveInfoFlags mResolveInfoFlags;

    private final AndroidMockitoMocker mMocker = new AndroidMockitoMocker();

    private final Intent mIntent = new Intent();
    @Mock private PackageManager mMockPm;

    @Before
    public void setFixtures() {
        if (sdkLevel.isAtLeastT()) {
            mResolveInfoFlags = ResolveInfoFlags.of(FLAG);
        }
    }

    @Test
    public void testQueryIntentService_nullPm() {
        assertThrows(NullPointerException.class, () -> mMocker.mockQueryIntentService(null));
    }

    @Test
    public void testQueryIntentService_nullArgs() {
        mMocker.mockQueryIntentService(mMockPm, (ResolveInfo[]) null);

        var byIntFlag = mMockPm.queryIntentServices(mIntent, FLAG);
        expect.withMessage("queryIntentServices(intFlags)").that(byIntFlag).isNull();
        if (sdkLevel.isAtLeastT()) {
            var byResolveInfoFlags = mMockPm.queryIntentServices(mIntent, mResolveInfoFlags);
            expect.withMessage("queryIntentServices(ResolveInfoFlags)")
                    .that(byResolveInfoFlags)
                    .isNull();
        }
    }

    @Test
    public void testQueryIntentService_noArgs() {
        mMocker.mockQueryIntentService(mMockPm);

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
    public void testQueryIntentService_multipleArgs() {
        ResolveInfo info1 = new ResolveInfo();
        info1.priority = 1;
        ResolveInfo info2 = new ResolveInfo();
        info2.priority = 2;

        mMocker.mockQueryIntentService(mMockPm, info1, info2);

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
}
