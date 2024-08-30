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

package com.android.server.adservices.errorlogging;

import static org.mockito.Mockito.when;

import com.android.adservices.common.AdServicesMockitoTestCase;
import com.android.adservices.shared.errorlogging.StatsdAdServicesErrorLogger;
import com.android.server.adservices.Flags;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public final class AdServicesErrorLoggerImplTest extends AdServicesMockitoTestCase {
    @Mock private StatsdAdServicesErrorLogger mStatsdLoggerMock;

    // TODO(b/358120731): create an AdServicesServerExtendedMockitoTestCase class instead, which
    // contains a mMockFlags
    @Mock private Flags mMockServerFlags;

    private AdServicesErrorLoggerImpl mErrorLogger;

    // TODO(b/343741206): Remove suppress warning once the lint is fixed.
    @SuppressWarnings("VisibleForTests")
    @Before
    public void setUp() {
        enableErrorLogging(/* enable= */ false);
        mErrorLogger = new AdServicesErrorLoggerImpl(mMockServerFlags, mStatsdLoggerMock);
    }

    @Test
    public void testIsEnabled() {
        int errorCode = 1;
        expect.that(mErrorLogger.isEnabled(errorCode)).isFalse();

        enableErrorLogging(true);

        expect.that(mErrorLogger.isEnabled(errorCode)).isTrue();
    }

    private void enableErrorLogging(boolean enable) {
        when(mMockServerFlags.getEnableCelForSystemServer()).thenReturn(enable);
    }
}
