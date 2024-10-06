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
package com.android.adservices.common;

import android.annotation.CallSuper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import com.android.adservices.mockito.AndroidMocker;
import com.android.adservices.mockito.AndroidMockitoMocker;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.Flags;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

// NOTE: currently no subclass needs a custom mocker; once they do, this class should be split
// into a AdServicesMockerLessMockitoTestCase (similar to AdServiceExtendedMockitoTestCase /
// AdServicesMockerLessExtendedMockitoTestCase)
/**
 * Base class for all unit tests that use "regular Mockito" (i.e., not {@code ExtendedMockito} - for
 * those, use {@link AdServicesExtendedMockitoTestCase} instead)
 */
public abstract class AdServicesMockitoTestCase extends AdServicesUnitTestCase {

    @Mock protected Context mMockContext;
    @Mock protected Flags mMockFlags;
    @Mock protected DebugFlags mMockDebugFlags;

    /** Spy the {@link AdServicesUnitTestCase#mContext} */
    @Spy protected final Context mSpyContext = mContext;

    @Rule(order = 10)
    public final MockitoRule mockito = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    /** Provides common expectations. */
    public final Mocker mocker = new Mocker();

    /** Provides common expectations. */
    public static final class Mocker implements AndroidMocker {

        private final AndroidMocker mAndroidMocker = new AndroidMockitoMocker();

        @Override
        public void mockQueryIntentService(PackageManager mockPm, ResolveInfo... resolveInfos) {
            mAndroidMocker.mockQueryIntentService(mockPm, resolveInfos);
        }

        @Override
        public void mockGetApplicationContext(Context mockContext, Context appContext) {
            mAndroidMocker.mockGetApplicationContext(mockContext, appContext);
        }
    }

    // TODO(b/361555631): rename to testAdServicesMockitoTestCaseFixtures() and annotate
    // it with @MetaTest
    @CallSuper
    @Override
    protected void assertValidTestCaseFixtures() throws Exception {
        super.assertValidTestCaseFixtures();

        checkProhibitedMockitoFields(AdServicesMockitoTestCase.class, this);
    }
}
