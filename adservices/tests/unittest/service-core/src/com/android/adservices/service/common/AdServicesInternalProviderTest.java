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

import static com.android.adservices.service.common.AdServicesInternalProvider.DUMP_ARG_FULL_QUIET;
import static com.android.adservices.service.common.AdServicesInternalProvider.DUMP_ARG_SHORT_QUIET;
import static com.android.adservices.shared.testing.common.DumpHelper.dump;
import static com.android.adservices.shared.testing.common.DumpHelper.mockDump;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import android.annotation.Nullable;
import android.app.Application;
import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.content.pm.ProviderInfo;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.service.DebugFlags;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.adservices.shared.testing.mockito.MockitoHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.io.PrintWriter;

public final class AdServicesInternalProviderTest extends AdServicesExtendedMockitoTestCase {

    private AdServicesInternalProvider mProvider;

    @Mock private Throttler mMockThrottler;

    @Mock private ConsentManager mMockConsentManager;

    @Before
    @After
    public void resetApplicationContextSingleton() {
        ApplicationContextSingleton.setForTests(/* context= */ null);
    }

    @Before
    public void setFixtures() {
        mProvider =
                new AdServicesInternalProvider(
                        mMockFlags, mMockThrottler, mMockConsentManager, mMockDebugFlags);
    }

    @Test
    public void testCustomApplicationContextOnSingleton_disabled() {
        mockAdservicesApplicationContextFlagEnabled(false);

        initializeProvider(mContext);

        Context appContext = ApplicationContextSingleton.get();
        assertWithMessage("ApplicationContextSingleton.get()").that(appContext).isNotNull();
        expect.withMessage("ApplicationContextSingleton.get()")
                .that(appContext)
                .isNotInstanceOf(AdServicesApplicationContext.class);
    }

    @Test
    public void testCustomApplicationContextOnSingleton_enabled() {
        mockAdservicesApplicationContextFlagEnabled(true);

        initializeProvider(mContext);

        Context appContext = ApplicationContextSingleton.get();
        assertWithMessage("ApplicationContextSingleton.get()")
                .that(appContext)
                .isInstanceOf(AdServicesApplicationContext.class);
        AdServicesApplicationContext customContext =
                AdServicesApplicationContext.class.cast(appContext);
        expect.withMessage("base context")
                .that(customContext.getBaseContext())
                .isSameInstanceAs(mContext);
    }

    @Test
    public void testDump_genericInfo() throws Exception {
        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()")
                .that(dump)
                .contains("App process: " + Application.getProcessName());
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
                .contains("ApplicationContext: " + appContext);
    }

    @Test
    public void testDump_appContextSingletonSet_customApplicationContext() throws Exception {
        mockAdservicesApplicationContextFlagEnabled(true);
        initializeProvider(mContext);

        AdServicesApplicationContext appContext =
                AdServicesApplicationContext.class.cast(ApplicationContextSingleton.get());
        String expectedDump = dump(pw -> appContext.dump(pw, /* args= */ null));

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    @MockStatic(AppManifestConfigMetricsLogger.class)
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
    @MockStatic(AdServicesManager.class)
    public void testDump_includesAdservicesManagerDump() throws Exception {
        String mgrDump = "A Manager dumps no Name";
        mockDump(() -> AdServicesManager.dump(any()), /* pwArgIndex= */ 0, mgrDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(mgrDump);
    }

    @Test
    @MockStatic(DebugFlags.class)
    public void testDump_includesDebugFlagsDump() throws Exception {
        String expectedDump = "The Bug is on the Table";
        mockDebugFlagsDump(expectedDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    public void testDump_includesFlagsDump() throws Exception {
        String expectedDump = "I flag, therefore I am!";
        mockFlagsDump(expectedDump, "Arg", "h", "!");

        String dump =
                dump(pw -> mProvider.dump(/* fd= */ null, pw, new String[] {"Arg", "h", "!"}));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    public void testDump_includesThrottlerDump() throws Exception {
        String expectedDump = "Slow ride, take it easy!";
        mockThrottlerDump(expectedDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    public void testDump_includesConsentManagerDump() throws Exception {
        String expectedDump = "As you wish!";
        mockConsentManagerDump(expectedDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, /* args= */ null));

        assertWithMessage("content of dump()").that(dump).contains(expectedDump);
    }

    @Test
    @MockStatic(DebugFlags.class)
    public void testDump_argShortQuiet() throws Exception {
        testDumpQuiet(DUMP_ARG_SHORT_QUIET);
    }

    @Test
    @MockStatic(DebugFlags.class)
    public void testDump_argFullQuiet() throws Exception {
        testDumpQuiet(DUMP_ARG_FULL_QUIET);
    }

    private void testDumpQuiet(String arg) throws Exception {
        String flagsDump = "Don't bother me, I'm flaggy";
        mockFlagsDump(flagsDump);
        String debugFlagsDump = "Don't bother me, I'm debuggy";
        mockDebugFlagsDump(debugFlagsDump);
        String throttlerDump = "Don't bother me, I'm throttled";
        mockThrottlerDump(throttlerDump);

        String dump = dump(pw -> mProvider.dump(/* fd= */ null, pw, new String[] {arg}));

        // Don't need to assert everything that's dumped, just what it isn't...
        assertWithMessage("content of dump()").that(dump).doesNotContain(flagsDump);
        assertWithMessage("content of dump()").that(dump).doesNotContain(debugFlagsDump);
        assertWithMessage("content of dump()").that(dump).doesNotContain(throttlerDump);
    }

    private void initializeProvider(Context context) {
        // attachInfo() will trigger onCreate()
        mProvider.attachInfo(context, new ProviderInfo());
    }

    private void mockAdservicesApplicationContextFlagEnabled(boolean value) {
        mocker.mockGetDeveloperSessionFeatureEnabled(value);
    }

    // TODO(b/371064777): Ideally we should have a DumpHelper.mockDump() method that could be used
    // below...

    private void mockDebugFlagsDump(String dump) {
        doAnswer(
                        (inv) -> {
                            mLog.d("%s", MockitoHelper.toString(inv));
                            ((PrintWriter) inv.getArgument(0)).println(dump);
                            return null;
                        })
                .when(mMockDebugFlags)
                .dump(any());
    }

    private void mockFlagsDump(String dump, @Nullable String... args) {
        doAnswer(
                        (inv) -> {
                            mLog.d("%s", MockitoHelper.toString(inv));
                            ((PrintWriter) inv.getArgument(0)).println(dump);
                            return null;
                        })
                .when(mMockFlags)
                .dump(any(), eq(args));
    }

    private void mockThrottlerDump(String dump) {
        doAnswer(
                        (inv) -> {
                            mLog.d("%s", MockitoHelper.toString(inv));
                            ((PrintWriter) inv.getArgument(0)).println(dump);
                            return null;
                        })
                .when(mMockThrottler)
                .dump(any());
    }

    private void mockConsentManagerDump(String dump) {
        doAnswer(
                        (inv) -> {
                            mLog.d("%s", MockitoHelper.toString(inv));
                            ((PrintWriter) inv.getArgument(0)).println(dump);
                            return null;
                        })
                .when(mMockConsentManager)
                .dump(any(), any());
    }
}
