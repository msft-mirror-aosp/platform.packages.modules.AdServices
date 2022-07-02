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

package com.android.adservices.service.adselection;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.net.Uri;

import org.junit.Test;

import java.util.Arrays;

public class AdSelectionConfigValidatorTest {
    private static final String EMPTY_STRING = "";
    private static final String SELLER_VALID = "developer.android.com";
    private static final String SELLER_INVALID = "developer%$android.com";
    private static final String SELLER_NO_HOST = "test@";
    private static final Uri DECISION_LOGIC_URL_CONSISTENT =
            Uri.parse("https://developer.android.com/test/decisions_logic_urls");
    private static final Uri DECISION_LOGIC_URL_NO_HOST = Uri.parse("test/decisions_logic_urls");
    private static final Uri DECISION_LOGIC_URL_INCONSISTENT =
            Uri.parse("https://developer%$android.com/test/decisions_logic_urls");

    private String generateInconsistentSellerAndDecisionLogicUrlMessage(
            String seller, Uri decisionLogicUrl) {
        return String.format(
                AdSelectionConfigValidator.SELLER_AND_DECISION_LOGIC_URL_ARE_INCONSISTENT,
                Uri.parse("https://" + seller).getHost(),
                decisionLogicUrl.getHost());
    }

    @Test
    public void testVerifyAdSelectionConfigSuccess() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_CONSISTENT)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        adSelectionConfigValidator.validate(adSelectionConfig);
    }

    @Test
    public void testVerifyEmptySeller() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(EMPTY_STRING)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_CONSISTENT)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> adSelectionConfigValidator.validate(adSelectionConfig));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                "Invalid object of type %s. The violations are: %s",
                                AdSelectionConfig.class.getName(),
                                Arrays.asList(
                                        AdSelectionConfigValidator
                                                .SELLER_SHOULD_NOT_BE_NULL_OR_EMPTY)));
    }

    @Test
    public void testVerifyInvalidSeller() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_INVALID)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_CONSISTENT)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> adSelectionConfigValidator.validate(adSelectionConfig));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                "Invalid object of type %s. The violations are: %s",
                                AdSelectionConfig.class.getName(),
                                Arrays.asList(
                                        AdSelectionConfigValidator.SELLER_HAS_INVALID_DOMAIN_NAME,
                                        generateInconsistentSellerAndDecisionLogicUrlMessage(
                                                SELLER_INVALID, DECISION_LOGIC_URL_CONSISTENT))));
    }

    @Test
    public void testVerifyNoHostSeller() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_NO_HOST)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_CONSISTENT)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> adSelectionConfigValidator.validate(adSelectionConfig));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                "Invalid object of type %s. The violations are: %s",
                                AdSelectionConfig.class.getName(),
                                Arrays.asList(
                                        AdSelectionConfigValidator
                                                .SELLER_HAS_MISSING_DOMAIN_NAME)));
    }

    @Test
    public void testVerifyNoHostDecisionLogicUrl() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_NO_HOST)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> adSelectionConfigValidator.validate(adSelectionConfig));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                "Invalid object of type %s. The violations are: %s",
                                AdSelectionConfig.class.getName(),
                                Arrays.asList(
                                        AdSelectionConfigValidator
                                                .DECISION_LOGIC_URL_SHOULD_HAVE_PRESENT_HOST)));
    }

    @Test
    public void testVerifyInconsistentSellerUrls() {
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUrl(DECISION_LOGIC_URL_INCONSISTENT)
                        .build();
        AdSelectionConfigValidator adSelectionConfigValidator = new AdSelectionConfigValidator();
        IllegalArgumentException thrown =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> adSelectionConfigValidator.validate(adSelectionConfig));
        assertThat(thrown)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                "Invalid object of type %s. The violations are: %s",
                                AdSelectionConfig.class.getName(),
                                Arrays.asList(
                                        generateInconsistentSellerAndDecisionLogicUrlMessage(
                                                SELLER_VALID, DECISION_LOGIC_URL_INCONSISTENT))));
    }
}
