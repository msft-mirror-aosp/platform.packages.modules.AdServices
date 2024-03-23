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

package com.android.adservices.service.shell;

import static com.android.adservices.service.shell.IsAllowedTopicsAccessCommand.CMD_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.adservices.service.shell.IsAllowedTopicsAccessCommand.HELP_IS_ALLOWED_TOPICS_ACCESS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import com.android.adservices.service.common.AppManifestConfigHelper;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;

@SpyStatic(AppManifestConfigHelper.class)
public final class IsAllowedTopicsAccessCommandTest
        extends ShellCommandTest<IsAllowedTopicsAccessCommand> {
    private static final String PKG_NAME = "d.h.a.r.m.a";
    private static final String ENROLLMENT_ID = "42";
    private static final String USES_SDK = "true";

    @Test
    public void testRunIsAllowedTopicsAccess_invalid() throws Exception {
        IsAllowedTopicsAccessCommand cmd = new IsAllowedTopicsAccessCommand();

        // no args
        runAndExpectInvalidArgument(
                cmd, HELP_IS_ALLOWED_TOPICS_ACCESS, CMD_IS_ALLOWED_TOPICS_ACCESS);
        // missing id
        runAndExpectInvalidArgument(
                cmd, HELP_IS_ALLOWED_TOPICS_ACCESS, CMD_IS_ALLOWED_TOPICS_ACCESS, PKG_NAME);

        // missing sdk
        runAndExpectInvalidArgument(
                cmd,
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                ENROLLMENT_ID);

        // non-boolean sdk
        runAndExpectInvalidArgument(
                cmd,
                HELP_IS_ALLOWED_TOPICS_ACCESS,
                CMD_IS_ALLOWED_TOPICS_ACCESS,
                PKG_NAME,
                ENROLLMENT_ID,
                "D'OH!");
    }

    @Test
    public void testRunIsAllowedTopicsAudiencesAccess_valid() throws Exception {
        IsAllowedTopicsAccessCommand cmd = new IsAllowedTopicsAccessCommand();
        doReturn(true)
                .when(
                        () ->
                                AppManifestConfigHelper.isAllowedTopicsAccess(
                                        /* useSandboxCheck= */ true, PKG_NAME, ENROLLMENT_ID));

        Result actualResult =
                run(cmd, CMD_IS_ALLOWED_TOPICS_ACCESS, PKG_NAME, ENROLLMENT_ID, USES_SDK);

        expectSuccess(actualResult, "true\n");
    }
}
