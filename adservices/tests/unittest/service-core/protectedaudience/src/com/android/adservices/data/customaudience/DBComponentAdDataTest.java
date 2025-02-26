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

import static android.adservices.common.ComponentAdDataFixture.getValidRenderUriByBuyer;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.net.Uri;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Assert;
import org.junit.Test;

public class DBComponentAdDataTest extends AdServicesUnitTestCase {
    private static final Uri RENDER_URI = getValidRenderUriByBuyer(CommonFixture.VALID_BUYER_1, 1);
    private static final String RENDER_ID = "render_id";

    @Test
    public void testBuildDBComponentAdData_BuilderSuccess() {
        DBComponentAdData dbComponentAdData =
                DBComponentAdData.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setRenderUri(RENDER_URI)
                        .setRenderId(RENDER_ID)
                        .build();

        expect.withMessage("Owner")
                .that(dbComponentAdData.getOwner())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        expect.withMessage("Buyer")
                .that(dbComponentAdData.getBuyer())
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.withMessage("Name")
                .that(dbComponentAdData.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.withMessage("Render uri")
                .that(dbComponentAdData.getRenderUri())
                .isEqualTo(RENDER_URI);
        expect.withMessage("Render id").that(dbComponentAdData.getRenderId()).isEqualTo(RENDER_ID);
    }

    @Test
    public void testBuildDBComponentAdData_CreateSuccess() {
        DBComponentAdData dbComponentAdData =
                DBComponentAdData.create(
                        CustomAudienceFixture.VALID_OWNER,
                        CommonFixture.VALID_BUYER_1,
                        CustomAudienceFixture.VALID_NAME,
                        RENDER_URI,
                        RENDER_ID);
        expect.withMessage("Owner")
                .that(dbComponentAdData.getOwner())
                .isEqualTo(CustomAudienceFixture.VALID_OWNER);
        expect.withMessage("Buyer")
                .that(dbComponentAdData.getBuyer())
                .isEqualTo(CommonFixture.VALID_BUYER_1);
        expect.withMessage("Name")
                .that(dbComponentAdData.getName())
                .isEqualTo(CustomAudienceFixture.VALID_NAME);
        expect.withMessage("Render uri")
                .that(dbComponentAdData.getRenderUri())
                .isEqualTo(RENDER_URI);
        expect.withMessage("Render id").that(dbComponentAdData.getRenderId()).isEqualTo(RENDER_ID);
    }

    @Test
    public void testBuildDBComponentAdData_WithNullOwner_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBComponentAdData.builder()
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .setRenderUri(RENDER_URI)
                                .setRenderId(RENDER_ID)
                                .build());
    }

    @Test
    public void testBuildDBComponentAdData_WithNullBuyer_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBComponentAdData.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .setRenderUri(RENDER_URI)
                                .setRenderId(RENDER_ID)
                                .build());
    }

    @Test
    public void testBuildDBComponentAdData_WithNullName_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBComponentAdData.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setRenderUri(RENDER_URI)
                                .setRenderId(RENDER_ID)
                                .build());
    }

    @Test
    public void testBuildDBComponentAdData_WithNullRenderUri_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBComponentAdData.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .setRenderId(RENDER_ID)
                                .build());
    }

    @Test
    public void testBuildDBComponentAdData_WithNullRenderId_ThrowsException() {
        Assert.assertThrows(
                IllegalStateException.class,
                () ->
                        DBComponentAdData.builder()
                                .setOwner(CustomAudienceFixture.VALID_OWNER)
                                .setBuyer(CommonFixture.VALID_BUYER_1)
                                .setName(CustomAudienceFixture.VALID_NAME)
                                .setRenderUri(RENDER_URI)
                                .build());
    }
}
