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
package com.android.adservices.service.common;

import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.DumpHelper.mockDump;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import android.app.adservices.AdServicesManager;
import android.content.Context;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Before;
import org.junit.Test;

import java.io.PrintWriter;

@MockStatic(FlagsFactory.class)
public final class AdServicesInternalProviderTest extends AdServicesExtendedMockitoTestCase {

    private final AdServicesInternalProvider mProvider = new AdServicesInternalProvider();

    @Before
    public void setFixtures() {
        // Need to set it always otherwise testDump() methods would throw
        mocker.mockGetFlags(mMockFlags);
    }

    @Test
    public void testDump_appContextSingletonNotSet() throws Exception {
        ApplicationContextSingleton.setForTests(/* context= */ null);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains(ApplicationContextSingleton.ERROR_MESSAGE_SET_NOT_CALLED);
    }

    @Test
    public void testDump_appContextSingletonSet() throws Exception {
        Context appContext = mocker.setApplicationContextSingleton();

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains("ApplicationContextSingleton: " + appContext);
    }

    @Test
    @SpyStatic(AppManifestConfigMetricsLogger.class)
    public void testDump_includesAppManifestConfigMetricsLogger() throws Exception {
        mocker.setApplicationContextSingleton();
        String amcmDump = "I dump, therefore I am";
        mockDump(
                () -> AppManifestConfigMetricsLogger.dump(any(), any()),
                /* pwArgIndex= */ 1,
                amcmDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(amcmDump);
    }

    @Test
    @SpyStatic(AdServicesManager.class)
    public void testDump_includesAdservicesManagerDump() throws Exception {
        String mgrDump = "A Manager dumps no Name";
        mockDump(() -> AdServicesManager.dump(any()), /* pwArgIndex= */ 0, mgrDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(mgrDump);
    }

    @Test
    @SpyStatic(DebugFlags.class)
    public void testDump_includesDebugFlagsDump() throws Exception {
        DebugFlags mockDebugFlags = mock(DebugFlags.class);
        mocker.mockGetDebugFlags(mockDebugFlags);
        String expectedDump = "The Bug is on the Table";
        // Ideally we should have a Dumper.mockDump() method that could be used below...
        doAnswer(
                        (inv) -> {
                            ((PrintWriter) inv.getArgument(0)).println(expectedDump);
                            return null;
                        })
                .when(mockDebugFlags)
                .dump(any());

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    public void testDump_includesFlagsDump() throws Exception {
        String expectedDump = "I flag, therefore I am!";
        // Ideally we should have a Dumper.mockDump() method that could be used below...
        doAnswer(
                        (inv) -> {
                            ((PrintWriter) inv.getArgument(0)).println(expectedDump);
                            return null;
                        })
                .when(mMockFlags)
                .dump(any(), any());

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }
}
