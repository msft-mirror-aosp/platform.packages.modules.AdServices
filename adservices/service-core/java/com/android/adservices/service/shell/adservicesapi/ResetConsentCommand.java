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

import static android.adservices.common.AdServicesCommonManager.MODULE_ADID;
import static android.adservices.common.AdServicesCommonManager.MODULE_MEASUREMENT;
import static android.adservices.common.AdServicesCommonManager.MODULE_ON_DEVICE_PERSONALIZATION;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_APP_SIGNALS;
import static android.adservices.common.AdServicesCommonManager.MODULE_PROTECTED_AUDIENCE;
import static android.adservices.common.AdServicesCommonManager.MODULE_TOPICS;
import static android.adservices.common.AdServicesModuleUserChoice.USER_CHOICE_UNKNOWN;

import static com.android.adservices.service.consent.AdServicesApiType.FLEDGE;
import static com.android.adservices.service.consent.AdServicesApiType.MEASUREMENTS;
import static com.android.adservices.service.consent.AdServicesApiType.TOPICS;
import static com.android.adservices.service.consent.ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
import static com.android.adservices.service.consent.ConsentManager.UNKNOWN;
import static com.android.adservices.service.shell.AdServicesShellCommandHandler.TAG;
import static com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_RESET_CONSENT_DATA;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_SUCCESS;

import android.adservices.common.AdServicesModuleUserChoice;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.util.SparseIntArray;

import androidx.annotation.RequiresApi;

import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.shell.AbstractShellCommand;
import com.android.adservices.service.shell.ShellCommandResult;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.modules.utils.build.SdkLevel;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * This class implements reset_consent_bits shell command.
 *
 * <p>It will reset all the consent related bits.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class ResetConsentCommand extends AbstractShellCommand {

    public static final String CMD_RESET_CONSENT_DATA = "reset-consent-data";

    public static final String HELP_RESET_CONSENT_DATA =
            COMMAND_PREFIX + " " + CMD_RESET_CONSENT_DATA + "\n Clear all user consent data.";

    @Override
    public ShellCommandResult run(PrintWriter out, PrintWriter err, String[] args) {
        if (args.length != 2) {
            return invalidArgsError(HELP_RESET_CONSENT_DATA, err, COMMAND_RESET_CONSENT_DATA, args);
        }
        ConsentManager consentManager = ConsentManager.getInstance();
        Context context = ApplicationContextSingleton.get();

        consentManager.recordUserManualInteractionWithConsent(NO_MANUAL_INTERACTIONS_RECORDED);
        consentManager.disable(context, MEASUREMENTS);

        if (SdkLevel.isAtLeastS()) {
            consentManager.recordNotificationDisplayed(false);
            consentManager.recordGaUxNotificationDisplayed(false);
            consentManager.disable(context, TOPICS);
            consentManager.disable(context, FLEDGE);
        }

        if (SdkLevel.isAtLeastT()) {
            consentManager.recordPasNotificationDisplayed(false);
            consentManager.recordPasNotificationOpened(false);
        }
        consentManager.setU18NotificationDisplayed(false);
        consentManager.setU18Account(false);

        // Reset enrollment data
        List<AdServicesModuleUserChoice> adServicesUserChoiceList = new ArrayList<>();
        SparseIntArray moduleStates = new SparseIntArray();
        List<Integer> moduleList =
                List.of(
                        MODULE_MEASUREMENT,
                        MODULE_PROTECTED_AUDIENCE,
                        MODULE_PROTECTED_APP_SIGNALS,
                        MODULE_TOPICS,
                        MODULE_ON_DEVICE_PERSONALIZATION,
                        MODULE_ADID);

        for (int moduleCode : moduleList) {
            moduleStates.put(moduleCode, UNKNOWN);
            adServicesUserChoiceList.add(
                    new AdServicesModuleUserChoice(moduleCode, USER_CHOICE_UNKNOWN));
        }
        consentManager.setUserChoices(adServicesUserChoiceList);
        consentManager.setModuleStates(moduleStates);

        String msg =
                "Consent data has been reset. Current enrollment data is: \n"
                        + consentManager.getModuleEnrollmentState();

        Log.i(TAG, msg);
        out.print(msg);

        return toShellCommandResult(RESULT_SUCCESS, COMMAND_RESET_CONSENT_DATA);
    }

    @Override
    public String getCommandName() {
        return CMD_RESET_CONSENT_DATA;
    }

    @Override
    public int getMetricsLoggerCommand() {
        return COMMAND_RESET_CONSENT_DATA;
    }

    @Override
    public String getCommandHelp() {
        return HELP_RESET_CONSENT_DATA;
    }
}
