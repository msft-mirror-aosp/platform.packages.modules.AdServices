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

package com.android.adservices.service.shell.adselection;

import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.shell.adselection.ConsentedDebugEnableArgs.EXPIRY_IN_DAYS_ARG_NAME;
import static com.android.adservices.service.shell.adselection.ConsentedDebugEnableArgs.SECRET_DEBUG_TOKEN_ARG_NAME;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.CMD;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.DISABLE_ERROR;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.DISABLE_SUB_CMD;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.DISABLE_SUCCESS;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.ENABLE_ERROR;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.ENABLE_SUB_CMD;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.ENABLE_SUCCESS;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.HELP;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.JSON_CREATION_TIME;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.JSON_DEBUG_TOKEN;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.JSON_EXPIRY;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.JSON_IS_CONSENTED;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.VIEW_ERROR;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.VIEW_SUB_CMD;
import static com.android.adservices.service.shell.adselection.ConsentedDebugShellCommand.VIEW_SUCCESS_NO_CONFIGURATION;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.adservices.common.CommonFixture;

import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.adselection.DBConsentedDebugConfiguration;
import com.android.adservices.service.shell.ShellCommandTest;

import com.google.common.truth.Truth;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

public class ConsentedDebugShellCommandTest extends ShellCommandTest<ConsentedDebugShellCommand> {
    private static final String DEBUG_TOKEN = UUID.randomUUID().toString();
    private static final String EXPIRY_IN_DAYS = "2";
    @Mock private ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;

    @Test
    public void test_noSubCommand_failure() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD);
    }

    @Test
    public void test_unknownSubCommand_failure() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                "unknown");
    }

    @Test
    public void test_enableConsentedDebugging_success() {
        doNothing()
                .when(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));

        Result result = runEnableSubCommandAndGetResult(true);

        expectSuccess(result, ENABLE_SUCCESS);
        verify(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_defaultExpiry() {
        doNothing()
                .when(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));

        Result result = runEnableSubCommandAndGetResult(false);

        expectSuccess(result, ENABLE_SUCCESS);
        verify(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_emptyDebugToken() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                ENABLE_SUB_CMD,
                EXPIRY_IN_DAYS_ARG_NAME,
                EXPIRY_IN_DAYS);

        verify(mConsentedDebugConfigurationDao, never())
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_invalidDebugTokenLength() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                VIEW_SUB_CMD,
                SECRET_DEBUG_TOKEN_ARG_NAME,
                "small",
                EXPIRY_IN_DAYS_ARG_NAME,
                EXPIRY_IN_DAYS);

        verify(mConsentedDebugConfigurationDao, never())
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_invalidExpiry() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                VIEW_SUB_CMD,
                SECRET_DEBUG_TOKEN_ARG_NAME,
                DEBUG_TOKEN,
                EXPIRY_IN_DAYS_ARG_NAME,
                "Not a number");

        verify(mConsentedDebugConfigurationDao, never())
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_expiryGreaterThan30Days() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                VIEW_SUB_CMD,
                SECRET_DEBUG_TOKEN_ARG_NAME,
                DEBUG_TOKEN,
                EXPIRY_IN_DAYS_ARG_NAME,
                "400");

        verify(mConsentedDebugConfigurationDao, never())
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_invalidArgs() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                VIEW_SUB_CMD,
                SECRET_DEBUG_TOKEN_ARG_NAME,
                DEBUG_TOKEN,
                "--random_arg",
                "400");

        verify(mConsentedDebugConfigurationDao, never())
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_enableConsentedDebugging_persistConfigFail() {
        doThrow(new IllegalStateException("Exception in persistConsentedDebugConfiguration"))
                .when(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));

        Result result = runEnableSubCommandAndGetResult(true);

        expectFailure(result, ENABLE_ERROR);
        verify(mConsentedDebugConfigurationDao)
                .deleteExistingConsentedDebugConfigurationsAndPersist(
                        any(DBConsentedDebugConfiguration.class));
    }

    @Test
    public void test_disableConsentedDebugging_success() {
        doNothing().when(mConsentedDebugConfigurationDao).deleteAllConsentedDebugConfigurations();

        ShellCommandTest.Result result = runSubCommandAndGetResult(DISABLE_SUB_CMD);

        expectSuccess(result, DISABLE_SUCCESS);
    }

    @Test
    public void test_disableConsentedDebugging_invalidArgs() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                DISABLE_SUB_CMD,
                "extraArgs");
    }

    @Test
    public void test_disableConsentedDebugging_failure() {
        doThrow(new IllegalStateException("Exception in deleteAllConsentedDebugConfigurations"))
                .when(mConsentedDebugConfigurationDao)
                .deleteAllConsentedDebugConfigurations();

        ShellCommandTest.Result result = runSubCommandAndGetResult(DISABLE_SUB_CMD);

        expectFailure(result, DISABLE_ERROR);
    }

    @Test
    public void test_view_invalidArgs() {
        runAndExpectInvalidArgument(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                HELP,
                COMMAND_PREFIX,
                CMD,
                VIEW_SUB_CMD,
                "extraArgs");
    }

    @Test
    public void test_view_success() {
        boolean isConsented = true;
        String debugToken = UUID.randomUUID().toString();
        Instant creationTimestamp = CommonFixture.FIXED_NOW;
        Duration expiryDuration = Duration.ofDays(2);
        Instant expiryTimestamp = CommonFixture.FIXED_NOW.plus(expiryDuration);
        DBConsentedDebugConfiguration dbConsentedDebugConfiguration =
                DBConsentedDebugConfiguration.builder()
                        .setIsConsentProvided(isConsented)
                        .setDebugToken(debugToken)
                        .setCreationTimestamp(creationTimestamp)
                        .setExpiryTimestamp(expiryTimestamp)
                        .build();
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(Collections.singletonList(dbConsentedDebugConfiguration));

        Result result = runSubCommandAndGetResult(VIEW_SUB_CMD);

        expectSuccess(result);
        assertConsentedDebugConfigurationJson(
                result.mOut, isConsented, debugToken, creationTimestamp, expiryTimestamp);
    }

    @Test
    public void test_viewConsentedDebug_noEntryInDb() {
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(Collections.emptyList());

        Result result = runSubCommandAndGetResult(VIEW_SUB_CMD);

        expectSuccess(result, VIEW_SUCCESS_NO_CONFIGURATION);
    }

    @Test
    public void test_viewConsentedDebug_nullListFromDb() {
        Mockito.when(
                        mConsentedDebugConfigurationDao.getAllActiveConsentedDebugConfigurations(
                                Mockito.any(Instant.class), Mockito.anyInt()))
                .thenReturn(null);

        Result result = runSubCommandAndGetResult(VIEW_SUB_CMD);

        expectSuccess(result, VIEW_SUCCESS_NO_CONFIGURATION);
    }

    @Test
    public void test_viewConsentedDebug_exceptionReadingDb() {
        Mockito.doThrow(new IllegalStateException(""))
                .when(mConsentedDebugConfigurationDao)
                .getAllActiveConsentedDebugConfigurations(
                        Mockito.any(Instant.class), Mockito.anyInt());

        Result result = runSubCommandAndGetResult(VIEW_SUB_CMD);

        expectFailure(result, VIEW_ERROR);
    }

    private void assertConsentedDebugConfigurationJson(
            String jsonString,
            boolean expectedIsConsented,
            String expectedDebugToken,
            Instant expectedCreationTimestamp,
            Instant expectedExpiryTimestamp) {
        try {
            JSONObject consentedDebugConfigurationJson = new JSONObject(jsonString);
            Boolean isConsented = consentedDebugConfigurationJson.getBoolean(JSON_IS_CONSENTED);
            String debugToken = consentedDebugConfigurationJson.getString(JSON_DEBUG_TOKEN);
            Instant expiryTimestamp =
                    Instant.parse(consentedDebugConfigurationJson.getString(JSON_EXPIRY));
            Instant creationTime =
                    Instant.parse(consentedDebugConfigurationJson.getString(JSON_CREATION_TIME));

            Truth.assertThat(isConsented).isEqualTo(expectedIsConsented);
            Truth.assertThat(debugToken).isEqualTo(expectedDebugToken);
            Truth.assertThat(expiryTimestamp.getEpochSecond())
                    .isEqualTo(expectedExpiryTimestamp.getEpochSecond());
            Truth.assertThat(creationTime.getEpochSecond())
                    .isEqualTo(expectedCreationTimestamp.getEpochSecond());
        } catch (JSONException exception) {
            Assert.fail(
                    String.format(
                            "Failed to read a property from json %s. \n Exception: %s",
                            jsonString, exception.getMessage()));
        }
    }

    private Result runSubCommandAndGetResult(String subCommand) {
        return run(
                new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                COMMAND_PREFIX,
                ConsentedDebugShellCommand.CMD,
                subCommand);
    }

    private Result runEnableSubCommandAndGetResult(boolean addExpiry) {
        if (addExpiry) {
            return run(
                    new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                    COMMAND_PREFIX,
                    CMD,
                    ENABLE_SUB_CMD,
                    SECRET_DEBUG_TOKEN_ARG_NAME,
                    DEBUG_TOKEN,
                    EXPIRY_IN_DAYS_ARG_NAME,
                    EXPIRY_IN_DAYS);
        } else {
            return run(
                    new ConsentedDebugShellCommand(mConsentedDebugConfigurationDao),
                    COMMAND_PREFIX,
                    CMD,
                    ENABLE_SUB_CMD,
                    SECRET_DEBUG_TOKEN_ARG_NAME,
                    DEBUG_TOKEN);
        }
    }
}