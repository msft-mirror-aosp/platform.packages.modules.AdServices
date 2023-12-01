/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.customaudience;

import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.CustomAudienceFixture.getValidBiddingLogicUriByBuyer;

import static com.android.adservices.service.common.AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT;
import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_BIDDING_LOGIC_URI_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.BIDDING_LOGIC_URI_FIELD_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.CUSTOM_AUDIENCE_CLASS_NAME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.google.common.collect.ImmutableList;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class CustomAudienceBiddingLogicUriValidatorTest {
    public static final int CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B =
            CommonFixture.FLAGS_FOR_TEST.getFledgeCustomAudienceMaxBiddingLogicUriSizeB();
    private final CustomAudienceBiddingLogicUriValidator mValidator =
            new CustomAudienceBiddingLogicUriValidator(
                    CUSTOM_AUDIENCE_MAX_BIDDING_LOGIC_URI_SIZE_B);

    @Test
    public void testGetValidationViolation_nullBiddingLogicUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.getValidationViolations(null, VALID_BUYER_1));
    }

    @Test
    public void testGetValidationViolation_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.getValidationViolations(
                                getValidBiddingLogicUriByBuyer(VALID_BUYER_1), null));
    }

    @Test
    public void testAddValidation_nullBiddingLogicUri_throws() {
        assertThrows(
                NullPointerException.class,
                () -> mValidator.addValidation(null, VALID_BUYER_1, new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullBuyer_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                                null,
                                new ImmutableList.Builder<>()));
    }

    @Test
    public void testAddValidation_nullViolations_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mValidator.addValidation(
                                getValidBiddingLogicUriByBuyer(VALID_BUYER_1),
                                VALID_BUYER_1,
                                null));
    }

    @Test
    public void testValidator_valid() {
        // Assert valid bidding logic uri does not throw.
        mValidator.validate(getValidBiddingLogicUriByBuyer(VALID_BUYER_1), VALID_BUYER_1);
    }

    @Test
    public void testValidator_malformedUri() {
        // Construct uri with mismatched buyer.
        Uri invalidUri = getValidBiddingLogicUriByBuyer(VALID_BUYER_2);

        // Assert buyer mismatch causes failure.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidator.validate(invalidUri, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CUSTOM_AUDIENCE_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                                AD_TECH_ROLE_BUYER,
                                                VALID_BUYER_1,
                                                AD_TECH_ROLE_BUYER,
                                                BIDDING_LOGIC_URI_FIELD_NAME,
                                                VALID_BUYER_2))));
    }

    @Test
    public void testValidator_exceedsSizeLimit() {
        // Use a validator with a clearly small size limit.
        CustomAudienceBiddingLogicUriValidator mValidatorWithSmallLimits =
                new CustomAudienceBiddingLogicUriValidator(1);

        // Construct a valid uri which will now be too big for the validator.
        Uri tooLongUri = getValidBiddingLogicUriByBuyer(VALID_BUYER_1);

        // Assert failed validation.
        Exception exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> mValidatorWithSmallLimits.validate(tooLongUri, VALID_BUYER_1));

        // Assert error message is as expected.
        assertThat(exception)
                .hasMessageThat()
                .isEqualTo(
                        String.format(
                                Locale.ENGLISH,
                                EXCEPTION_MESSAGE_FORMAT,
                                CUSTOM_AUDIENCE_CLASS_NAME,
                                ImmutableList.of(
                                        String.format(
                                                Locale.ENGLISH,
                                                VIOLATION_BIDDING_LOGIC_URI_TOO_LONG,
                                                1,
                                                tooLongUri
                                                        .toString()
                                                        .getBytes(StandardCharsets.UTF_8)
                                                        .length))));
    }
}