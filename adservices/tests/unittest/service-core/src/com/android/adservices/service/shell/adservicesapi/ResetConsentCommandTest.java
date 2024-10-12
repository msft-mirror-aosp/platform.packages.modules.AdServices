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

package com.android.adservices.service.shell.adservicesapi;

import static com.android.adservices.service.consent.AdServicesApiType.FLEDGE;
import static com.android.adservices.service.consent.AdServicesApiType.MEASUREMENTS;
import static com.android.adservices.service.consent.AdServicesApiType.TOPICS;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adservicesapi.ResetConsentCommand.CMD_RESET_CONSENT_DATA;
import static com.android.adservices.service.shell.adservicesapi.ResetConsentCommand.HELP_RESET_CONSENT_DATA;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;
import com.android.modules.utils.testing.ExtendedMockitoRule;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@ExtendedMockitoRule.SpyStatic(ConsentManager.class)
public final class ResetConsentCommandTest extends ShellCommandTestCase<ResetConsentCommand> {
    @Mock private ConsentManager mMockAdServicesCommonManager;

    @Before
    public void setup() {
        doReturn(mMockAdServicesCommonManager).when(ConsentManager::getInstance);
    }

    @Test
    public void testRun_invalid() {
        ResetConsentCommand resetConsentCommand = new ResetConsentCommand();

        // more than 1 arg
        runAndExpectInvalidArgument(
                resetConsentCommand,
                HELP_RESET_CONSENT_DATA,
                ShellCommandStats.COMMAND_RESET_CONSENT_DATA,
                COMMAND_PREFIX,
                CMD_RESET_CONSENT_DATA,
                "foo bar");
    }

    @Test
    public void testRun_valid() {
        Result actualResult =
                run(new ResetConsentCommand(), COMMAND_PREFIX, CMD_RESET_CONSENT_DATA);
        expectSuccess(
                actualResult,
                "Consent data has been reset.",
                ShellCommandStats.COMMAND_RESET_CONSENT_DATA);

        verify(mMockAdServicesCommonManager)
                .recordUserManualInteractionWithConsent(NO_MANUAL_INTERACTIONS_RECORDED);
        verify(mMockAdServicesCommonManager).disable(any(), eq(MEASUREMENTS));
        verify(mMockAdServicesCommonManager).recordNotificationDisplayed(false);
        verify(mMockAdServicesCommonManager).recordGaUxNotificationDisplayed(false);
        verify(mMockAdServicesCommonManager).disable(any(), eq(TOPICS));
        verify(mMockAdServicesCommonManager).disable(any(), eq(FLEDGE));

        if (sdkLevel.isAtLeastT()) {
            verify(mMockAdServicesCommonManager).recordPasNotificationDisplayed(false);
            verify(mMockAdServicesCommonManager).recordPasNotificationOpened(false);
        }
        verify(mMockAdServicesCommonManager).setU18NotificationDisplayed(false);
        verify(mMockAdServicesCommonManager).setU18Account(false);
    }

    @Test
    public void testGetCommandName_valid() {
        expect.that(new ResetConsentCommand().getCommandName()).isEqualTo(CMD_RESET_CONSENT_DATA);
    }

    @Test
    public void testGetCommandHelp_valid() {
        expect.that(new ResetConsentCommand().getCommandHelp()).isEqualTo(HELP_RESET_CONSENT_DATA);
    }
}
