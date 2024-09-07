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

package com.android.adservices.service.shell.customaudience;

import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_REFRESH;
import static com.android.adservices.service.stats.ShellCommandStats.Command;
import static com.android.adservices.service.stats.ShellCommandStats.RESULT_GENERIC_ERROR;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.shell.ShellCommandTestCase;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public final class CustomAudienceRefreshCommandTest
        extends ShellCommandTestCase<CustomAudienceRefreshCommand> {

    public static final Instant OVERRIDE_CURRENT_TIME = Instant.now();
    public static final Clock OVERRIDE_CLOCK =
            Clock.fixed(OVERRIDE_CURRENT_TIME, ZoneId.systemDefault());
    private static final DBCustomAudience CUSTOM_AUDIENCE =
            DBCustomAudienceFixture.getValidBuilderByBuyer(
                            AdTechIdentifier.fromString("example.com"))
                    .build();
    private static final String OWNER = CUSTOM_AUDIENCE.getOwner();
    private static final AdTechIdentifier BUYER = CUSTOM_AUDIENCE.getBuyer();
    private static final String NAME = CUSTOM_AUDIENCE.getName();
    private static final DBCustomAudienceBackgroundFetchData CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA =
            DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                    .setOwner(OWNER)
                    .setIsDebuggable(CUSTOM_AUDIENCE.isDebuggable())
                    .build();

    @Command private static final int EXPECTED_COMMAND = COMMAND_CUSTOM_AUDIENCE_REFRESH;
    @Mock private BackgroundFetchRunner mBackgroundFetchRunnerMock;
    @Mock private CustomAudienceDao mCustomAudienceDao;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testRun_withValidCustomAudience_returnsSuccess() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        BackgroundFetchRunner.UpdateResultType.SUCCESS)));

        ShellCommandTestCase.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock, times(1))
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectSuccess(result, EXPECTED_COMMAND);
    }

    @Test
    public void testRun_withInvalidCustomAudienceBackgroundFetchData_returnsGenericFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(null);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        BackgroundFetchRunner.UpdateResultType.SUCCESS)));

        ShellCommandTestCase.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock, never())
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                CustomAudienceRefreshCommand.OUTPUT_ERROR_NO_CUSTOM_AUDIENCE,
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withFailedBackgroundFetch_returnsTimeoutFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        BackgroundFetchRunner.UpdateResultType
                                                .NETWORK_READ_TIMEOUT_FAILURE)));

        ShellCommandTestCase.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock)
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                String.format(
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_WITH_MESSAGE,
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_NETWORK),
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withFailedBackgroundFetch_returnsUnknownFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenReturn(
                        FluentFuture.from(
                                Futures.immediateFuture(
                                        BackgroundFetchRunner.UpdateResultType.UNKNOWN)));

        ShellCommandTestCase.Result result =
                runRefreshCustomAudienceCommand(
                        CustomAudienceRefreshCommand.BACKGROUND_FETCH_TIMEOUT_FINAL_SECONDS);

        verify(mBackgroundFetchRunnerMock)
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                String.format(
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_WITH_MESSAGE,
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_UNKNOWN),
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void testRun_withTimedOutBackgroundFetch_returnsTimeoutFailure() {
        when(mCustomAudienceDao.getDebuggableCustomAudienceByPrimaryKey(OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        OWNER, BUYER, NAME))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        when(mBackgroundFetchRunnerMock.updateCustomAudience(
                        OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(
                                        Futures.immediateFuture(
                                                BackgroundFetchRunner.UpdateResultType
                                                        .NETWORK_READ_TIMEOUT_FAILURE)));

        ShellCommandTestCase.Result result = runRefreshCustomAudienceCommand(0);

        verify(mBackgroundFetchRunnerMock)
                .updateCustomAudience(OVERRIDE_CURRENT_TIME, CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA);
        expectFailure(
                result,
                String.format(
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_WITH_MESSAGE,
                        CustomAudienceRefreshCommand.OUTPUT_ERROR_NETWORK),
                EXPECTED_COMMAND,
                RESULT_GENERIC_ERROR);
    }

    @Test
    public void test_getCommandName() {
        assertThat(
                        new CustomAudienceRefreshCommand(
                                        mBackgroundFetchRunnerMock,
                                        mCustomAudienceDao,
                                        AdServicesExecutors.getScheduler())
                                .getCommandName())
                .isEqualTo(CustomAudienceRefreshCommand.CMD);
    }

    @Test
    public void test_getCommandHelp() {
        assertThat(
                        new CustomAudienceRefreshCommand(
                                        mBackgroundFetchRunnerMock,
                                        mCustomAudienceDao,
                                        AdServicesExecutors.getScheduler())
                                .getCommandHelp())
                .isEqualTo(CustomAudienceRefreshCommand.HELP);
    }

    private ShellCommandTestCase.Result runRefreshCustomAudienceCommand(int timeoutInSeconds) {
        return run(
                new CustomAudienceRefreshCommand(
                        mBackgroundFetchRunnerMock,
                        mCustomAudienceDao,
                        timeoutInSeconds,
                        OVERRIDE_CLOCK,
                        AdServicesExecutors.getScheduler()),
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceRefreshCommand.CMD,
                "--owner",
                OWNER,
                "--buyer",
                BUYER.toString(),
                "--name",
                NAME);
    }
}
