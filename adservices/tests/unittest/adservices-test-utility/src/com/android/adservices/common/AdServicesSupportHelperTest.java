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

package com.android.adservices.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

/** Unit tests for {@link AdServicesSupportHelper}. */
public final class AdServicesSupportHelperTest extends AdServicesMockitoTestCase {
    private static final String INTENT_ACTION_NAME = "SomeService";
    @Mock private PackageManager mMockPackageManager;
    private AdServicesSupportHelper mAdServicesSupportHelper;

    @Before
    public void setup() {
        mAdServicesSupportHelper = new AdServicesSupportHelper(mSpyContext);

        doReturn(mMockPackageManager).when(mSpyContext).getPackageManager();
    }

    @Test
    public void testIsAndroidServiceAvailable_true() {
        mockPackageManagerQueryIntentService(List.of(new ResolveInfo()));

        assertThat(mAdServicesSupportHelper.isAndroidServiceAvailable(INTENT_ACTION_NAME)).isTrue();
    }

    @Test
    public void testIsAndroidServiceAvailable_false() {
        mockPackageManagerQueryIntentService(/* resolveInfos= */ null);

        expect.withMessage(
                        "isAdIdProviderAvailable() when PackageManager returns a null list of"
                                + " ResolveInfo.")
                .that(mAdServicesSupportHelper.isAndroidServiceAvailable(INTENT_ACTION_NAME))
                .isFalse();

        mockPackageManagerQueryIntentService(List.of());

        expect.withMessage(
                        "isAdIdProviderAvailable() when PackageManager returns an empty list of"
                                + " ResolveInfo.")
                .that(mAdServicesSupportHelper.isAndroidServiceAvailable(INTENT_ACTION_NAME))
                .isFalse();

        mockPackageManagerQueryIntentService(List.of(new ResolveInfo(), new ResolveInfo()));

        expect.withMessage(
                        "isAdIdProviderAvailable() when PackageManager returns a 2-element list of"
                                + " ResolveInfo.")
                .that(mAdServicesSupportHelper.isAndroidServiceAvailable(INTENT_ACTION_NAME))
                .isFalse();
    }

    private void mockPackageManagerQueryIntentService(List<ResolveInfo> resolveInfos) {
        when(mMockPackageManager.queryIntentServices(any(), anyInt())).thenReturn(resolveInfos);
    }
}
