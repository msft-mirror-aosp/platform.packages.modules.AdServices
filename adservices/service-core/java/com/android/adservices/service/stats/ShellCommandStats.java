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

package com.android.adservices.service.stats;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.util.Locale;
import java.util.Objects;

/** Class for {@code ADSERVICES_SHELL_COMMAND_CALLED} atom */
public final class ShellCommandStats {

    public static final int COMMAND_UNKNOWN =
            AdServicesStatsLog.AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_UNSET;
    public static final int COMMAND_ECHO =
            AdServicesStatsLog.AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_ECHO;
    public static final int COMMAND_IS_ALLOWED_ATTRIBUTION_ACCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_IS_ALLOWED_ATTRIBUTION_ACCESS;
    public static final int COMMAND_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS;
    public static final int COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS;
    public static final int COMMAND_IS_ALLOWED_AD_SELECTION_ACCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_IS_ALLOWED_AD_SELECTION_ACCESS;
    public static final int COMMAND_IS_ALLOWED_TOPICS_ACCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_IS_ALLOWED_TOPICS_ACCESS;

    public static final int COMMAND_ENABLE_ADSERVICES =
            AdServicesStatsLog.AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_ENABLE_ADSERVICES;
    public static final int COMMAND_RESET_CONSENT_DATA =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_RESET_CONSENT_DATA;

    public static final int COMMAND_CUSTOM_AUDIENCE_VIEW =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_CUSTOM_AUDIENCE_VIEW;
    public static final int COMMAND_CUSTOM_AUDIENCE_LIST =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_CUSTOM_AUDIENCE_LIST;
    public static final int COMMAND_CUSTOM_AUDIENCE_REFRESH =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_CUSTOM_AUDIENCE_REFRESH;


    public static final int COMMAND_AD_SELECTION_CONSENTED_DEBUG_ENABLE =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_AD_SELECTION_CONSENTED_DEBUG_ENABLE;
    public static final int COMMAND_AD_SELECTION_CONSENTED_DEBUG_DISABLE =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_AD_SELECTION_CONSENTED_DEBUG_DISABLE;
    public static final int COMMAND_AD_SELECTION_CONSENTED_DEBUG_VIEW =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_AD_SELECTION_CONSENTED_DEBUG_VIEW;
    public static final int COMMAND_AD_SELECTION_CONSENTED_DEBUG_HELP =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_AD_SELECTION_CONSENTED_DEBUG_HELP;
    public static final int COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA;

    public static final int COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__COMMAND__COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING;

    @IntDef({
        COMMAND_UNKNOWN,

        // Common commands
        COMMAND_ECHO,
        COMMAND_IS_ALLOWED_ATTRIBUTION_ACCESS,
        COMMAND_IS_ALLOWED_PROTECTED_SIGNALS_ACCESS,
        COMMAND_IS_ALLOWED_CUSTOM_AUDIENCE_ACCESS,
        COMMAND_IS_ALLOWED_AD_SELECTION_ACCESS,
        COMMAND_IS_ALLOWED_TOPICS_ACCESS,
        COMMAND_ENABLE_ADSERVICES,
        COMMAND_RESET_CONSENT_DATA,

        // Custom audience commands
        COMMAND_CUSTOM_AUDIENCE_VIEW,
        COMMAND_CUSTOM_AUDIENCE_LIST,
        COMMAND_CUSTOM_AUDIENCE_REFRESH,

        // Ad Selection commands
        COMMAND_AD_SELECTION_CONSENTED_DEBUG_ENABLE,
        COMMAND_AD_SELECTION_CONSENTED_DEBUG_DISABLE,
        COMMAND_AD_SELECTION_CONSENTED_DEBUG_VIEW,
        COMMAND_AD_SELECTION_CONSENTED_DEBUG_HELP,
        COMMAND_AD_SELECTION_GET_AD_SELECTION_DATA,

        // Protected App Signals commands
        COMMAND_APP_SIGNALS_GENERATE_INPUT_FOR_ENCODING
    })
    @Retention(SOURCE)
    public @interface Command {}

    public static final int RESULT_UNKNOWN =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_UNKNOWN;
    public static final int RESULT_SUCCESS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_SUCCESS;
    public static final int RESULT_GENERIC_ERROR =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_GENERIC_ERROR;
    public static final int RESULT_INVALID_ARGS =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_INVALID_ARGS;
    public static final int RESULT_TIMEOUT_ERROR =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_TIMEOUT_ERROR;
    public static final int RESULT_INVALID_COMMAND =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_INVALID_COMMAND;
    public static final int RESULT_NOT_ENABLED =
            AdServicesStatsLog
                    .AD_SERVICES_SHELL_COMMAND_CALLED__RESULT_CODE__COMMAND_RESULT_NOT_ENABLED;

    @IntDef({
        RESULT_UNKNOWN,
        RESULT_SUCCESS,
        RESULT_GENERIC_ERROR,
        RESULT_INVALID_ARGS,
        RESULT_TIMEOUT_ERROR,
        RESULT_INVALID_COMMAND,
        RESULT_NOT_ENABLED
    })
    @Retention(SOURCE)
    public @interface CommandResult {}

    private final int mCommand;
    private final int mResult;
    public final int mLatencyMillis;

    public ShellCommandStats(@Command int command, @CommandResult int result, int latencyMillis) {
        this.mCommand = command;
        this.mResult = result;
        this.mLatencyMillis = latencyMillis;
    }

    @Command
    public int getCommand() {
        return mCommand;
    }

    @CommandResult
    public int getResult() {
        return mResult;
    }

    public int getLatencyMillis() {
        return mLatencyMillis;
    }

    @Override
    public String toString() {
        return String.format(
                Locale.ENGLISH,
                "ShellCommandStats[command=%d, result=%d, latencyMillis=%d]",
                mCommand,
                mResult,
                mLatencyMillis);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ShellCommandStats other = (ShellCommandStats) obj;
        return mCommand == other.getCommand()
                && mResult == other.mResult
                && mLatencyMillis == other.getLatencyMillis();
    }

    @Override
    public int hashCode() {
        return Objects.hash(mCommand, mResult, mLatencyMillis);
    }
}
