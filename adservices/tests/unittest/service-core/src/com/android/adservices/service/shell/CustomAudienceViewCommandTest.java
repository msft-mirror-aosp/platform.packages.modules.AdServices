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

import static com.android.adservices.service.shell.CustomAudienceHelper.fromJson;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;

import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;

import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

import java.util.List;

public final class CustomAudienceViewCommandTest extends ShellCommandTest {

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("example.com");
    private static final DBCustomAudience CUSTOM_AUDIENCE_1 =
            DBCustomAudienceFixture.getValidBuilderByBuyer(BUYER).setDebuggable(true).build();

    @Mock private CustomAudienceDao mCustomAudienceDao;

    @Test
    public void testRun_happyPath_returnsSuccess() throws Exception {
        when(mCustomAudienceDao.getCustomAudienceByPrimaryKey(
                        CUSTOM_AUDIENCE_1.getOwner(),
                        CUSTOM_AUDIENCE_1.getBuyer(),
                        CUSTOM_AUDIENCE_1.getName()))
                .thenReturn(CUSTOM_AUDIENCE_1);

        Result actualResult =
                run(
                        new CustomAudienceViewCommand(mCustomAudienceDao),
                        CustomAudienceViewCommand.CMD,
                        "--owner=" + CUSTOM_AUDIENCE_1.getOwner(),
                        "--buyer=" + CUSTOM_AUDIENCE_1.getBuyer().toString(),
                        "--name=" + CUSTOM_AUDIENCE_1.getName());

        expectSuccess(actualResult);
        assertThat(fromJson(new JSONObject(actualResult.mOut))).isEqualTo(CUSTOM_AUDIENCE_1);
    }

    @Test
    public void testRun_notPresent_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(List.of(CUSTOM_AUDIENCE_1));

        Result actualResult =
                run(
                        new CustomAudienceViewCommand(mCustomAudienceDao),
                        CustomAudienceViewCommand.CMD,
                        "--owner=" + CUSTOM_AUDIENCE_1.getOwner(),
                        "--buyer=" + CUSTOM_AUDIENCE_1.getBuyer().toString(),
                        "--name=" + CUSTOM_AUDIENCE_1.getName());

        expectSuccess(actualResult, "{}");
    }

    @Test
    public void testRun_presentButNotDebuggable_returnsEmpty() {
        when(mCustomAudienceDao.listDebuggableCustomAudiencesByOwnerAndBuyer(
                        CUSTOM_AUDIENCE_1.getOwner(), CUSTOM_AUDIENCE_1.getBuyer()))
                .thenReturn(
                        List.of(CUSTOM_AUDIENCE_1.cloneToBuilder().setDebuggable(false).build()));

        Result actualResult =
                run(
                        new CustomAudienceViewCommand(mCustomAudienceDao),
                        CustomAudienceViewCommand.CMD,
                        "--owner=" + CUSTOM_AUDIENCE_1.getOwner(),
                        "--buyer=" + CUSTOM_AUDIENCE_1.getBuyer().toString(),
                        "--name=" + CUSTOM_AUDIENCE_1.getName());

        expectSuccess(actualResult, "{}");
    }
}
