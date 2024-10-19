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

package com.android.adservices.data.customaudience;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

public class DBCustomAudienceToLeaveTest extends AdServicesUnitTestCase {

    private static final long VALID_UPDATE_ID = 1L;
    private static final String VALID_CA_NAME = "running_shoes";

    @Test
    public void testBuildDBCustomAudienceToLeave_BuilderSuccess() {
        DBCustomAudienceToLeave dBCustomAudienceToLeave =
                DBCustomAudienceToLeave.builder()
                        .setUpdateId(VALID_UPDATE_ID)
                        .setName(VALID_CA_NAME)
                        .build();

        expect.withMessage("Custom Audience To Leave Update Id")
                .that(dBCustomAudienceToLeave.getUpdateId())
                .isEqualTo(VALID_UPDATE_ID);
        expect.withMessage("Custom Audience To Leave Name")
                .that(dBCustomAudienceToLeave.getName())
                .isEqualTo(VALID_CA_NAME);
    }

    @Test
    public void testBuildDBCustomAudienceToLeave_CreateSuccess() {
        DBCustomAudienceToLeave dBCustomAudienceToLeave =
                DBCustomAudienceToLeave.create(VALID_UPDATE_ID, VALID_CA_NAME);

        expect.withMessage("Custom Audience To Leave Update Id")
                .that(dBCustomAudienceToLeave.getUpdateId())
                .isEqualTo(VALID_UPDATE_ID);
        expect.withMessage("Custom Audience To Leave Name")
                .that(dBCustomAudienceToLeave.getName())
                .isEqualTo(VALID_CA_NAME);
    }

    @Test
    public void testBuildDBCustomAudienceToLeave_WithNullName_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () -> DBCustomAudienceToLeave.builder().setUpdateId(VALID_UPDATE_ID).build());
    }

    @Test
    public void testBuildDBCustomAudienceToLeave_WithNullUpdateId_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () -> DBCustomAudienceToLeave.builder().setName(VALID_CA_NAME).build());
    }
}
