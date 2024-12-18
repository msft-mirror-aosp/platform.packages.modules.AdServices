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

import static android.adservices.customaudience.CustomAudienceFixture.CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS;

import static com.android.adservices.service.shell.customaudience.CustomAudienceHelper.getCustomAudienceBackgroundFetchDataFromJson;
import static com.android.adservices.service.shell.customaudience.CustomAudienceHelper.getCustomAudienceFromJson;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_CUSTOM_AUDIENCE_VIEW;
import static com.android.adservices.service.stats.ShellCommandStats.Command;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.customaudience.DBCustomAudienceBackgroundFetchDataFixture;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceBackgroundFetchData;
import com.android.adservices.service.shell.ShellCommandTestCase;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.time.Clock;
import java.util.List;
import java.util.TimeZone;

public final class CustomAudienceViewCommandTest
        extends ShellCommandTestCase<CustomAudienceViewCommand> {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    private static final String CA_NAME = CustomAudienceFixture.VALID_NAME;
    private static final String OWNER = CustomAudienceFixture.VALID_OWNER;
    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER, CA_NAME)
                    .setOwner(OWNER)
                    .setDebuggable(true)
                    .build();
    private static final DBCustomAudienceBackgroundFetchData
            CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1 =
                    DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                            .setOwner(CUSTOM_AUDIENCE_1.getOwner())
                            .setIsDebuggable(CUSTOM_AUDIENCE_1.isDebuggable())
                            .build();
    @Command private static final int EXPECTED_COMMAND = COMMAND_CUSTOM_AUDIENCE_VIEW;
    @Mock private CustomAudienceDao mCustomAudienceDao;
    private final Clock mClock =
            Clock.fixed(CommonFixture.FIXED_NOW, TimeZone.getDefault().toZoneId());

    @Test
    public void testRun_missingArgument_returnsHelp() {
        runAndExpectInvalidArgument(
                new CustomAudienceViewCommand(
                        mCustomAudienceDao, mClock, CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS),
                CustomAudienceViewCommand.HELP,
                EXPECTED_COMMAND,
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceViewCommand.CMD,
                "--owner",
                "valid-owner");
    }

    @Test
    public void testRun_happyPath_returnsSuccess() throws Exception {
        when(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_1);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, EXPECTED_COMMAND);
        expect.that(getCustomAudienceFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        expect.that(getCustomAudienceBackgroundFetchDataFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
    }

    @Test
    public void testRun_eligibleForOnDeviceAuction_correctValueIsTrue() throws Exception {
        when(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_1);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
        when(mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(CUSTOM_AUDIENCE_1.getBuyer()),
                        mClock.instant(),
                        CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, EXPECTED_COMMAND);
        expect.that(getCustomAudienceFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        JSONObject jsonObject = new JSONObject(actualResult.mOut);
        expect.that(getCustomAudienceBackgroundFetchDataFromJson(jsonObject))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
        expect.that(jsonObject.getBoolean(CustomAudienceHelper.IS_ELIGIBLE_FOR_ON_DEVICE_AUCTION))
                .isEqualTo(true);
    }

    @Test
    public void testRun_ineligibleForOnDeviceAuction_correctValueIsFalse() throws Exception {
        when(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_1);
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
        when(mCustomAudienceDao.getActiveCustomAudienceByBuyers(
                        List.of(CUSTOM_AUDIENCE_1.getBuyer()),
                        mClock.instant(),
                        CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS))
                .thenReturn(List.of());

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, EXPECTED_COMMAND);
        expect.that(getCustomAudienceFromJson(new JSONObject(actualResult.mOut)))
                .isEqualTo(CUSTOM_AUDIENCE_1);
        JSONObject jsonObject = new JSONObject(actualResult.mOut);
        expect.that(getCustomAudienceBackgroundFetchDataFromJson(jsonObject))
                .isEqualTo(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);
        expect.that(jsonObject.getBoolean(CustomAudienceHelper.IS_ELIGIBLE_FOR_ON_DEVICE_AUCTION))
                .isEqualTo(false);
    }

    @Test
    public void testRun_notPresent_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}", EXPECTED_COMMAND);
    }

    @Test
    public void testRun_presentButNotDebuggable_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(
                        List.of(CUSTOM_AUDIENCE_1.cloneToBuilder().setDebuggable(false).build()));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_BACKGROUND_FETCH_DATA_1);

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}", EXPECTED_COMMAND);
    }

    @Test
    public void testRun_presentButBackgroundFetchDataNotDebuggable_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));
        when(mCustomAudienceDao.getDebuggableCustomAudienceBackgroundFetchDataByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(
                        DBCustomAudienceBackgroundFetchDataFixture.getValidBuilderByBuyer(BUYER)
                                .setOwner(OWNER)
                                .setIsDebuggable(false)
                                .build());

        Result actualResult = runCommandAndGetResult();

        expectSuccess(actualResult, "{}", EXPECTED_COMMAND);
    }

    @Test
    public void test_getCommandName() {
        assertThat(
                        new CustomAudienceViewCommand(
                                        mCustomAudienceDao,
                                        mClock,
                                        CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS)
                                .getCommandName())
                .isEqualTo(CustomAudienceViewCommand.CMD);
    }

    @Test
    public void test_getCommandHelp() {
        assertThat(
                        new CustomAudienceViewCommand(
                                        mCustomAudienceDao,
                                        mClock,
                                        CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS)
                                .getCommandHelp())
                .isEqualTo(CustomAudienceViewCommand.HELP);
    }

    private Result runCommandAndGetResult() {
        return run(
                new CustomAudienceViewCommand(
                        mCustomAudienceDao, mClock, CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS),
                CustomAudienceShellCommandFactory.COMMAND_PREFIX,
                CustomAudienceViewCommand.CMD,
                "--owner",
                OWNER,
                "--buyer",
                BUYER.toString(),
                "--name",
                CA_NAME);
    }
}
