/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CustomAudienceOverrideTest {
    private static final String OWNER = "owner";
    private static final String BUYER = "buyer";
    private static final String NAME = "name";
    private static final String APP_PACKAGE_NAME = "appPackageName";
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final String TRUSTED_BIDDING_DATA = "{\"trusted_bidding_data\":1}";

    @Test
    public void testBuildDBAdSelectionOverride() {
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(OWNER)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setDecisionLogicJS(DECISION_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        assertEquals(dbCustomAudienceOverride.getOwner(), OWNER);
        assertEquals(dbCustomAudienceOverride.getBuyer(), BUYER);
        assertEquals(dbCustomAudienceOverride.getName(), NAME);
        assertEquals(dbCustomAudienceOverride.getAppPackageName(), APP_PACKAGE_NAME);
        assertEquals(dbCustomAudienceOverride.getDecisionLogicJS(), DECISION_LOGIC_JS);
        assertEquals(dbCustomAudienceOverride.getTrustedBiddingData(), TRUSTED_BIDDING_DATA);
    }

    @Test
    public void testThrowsExceptionWithNoOwner() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setBuyer(BUYER)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoBuyer() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(OWNER)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(OWNER)
                            .setBuyer(BUYER)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoAppPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(OWNER)
                            .setBuyer(BUYER)
                            .setName(NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDecisionLogicJS() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(OWNER)
                            .setBuyer(BUYER)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoTrustedBiddingData() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(OWNER)
                            .setBuyer(BUYER)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .build();
                });
    }
}
