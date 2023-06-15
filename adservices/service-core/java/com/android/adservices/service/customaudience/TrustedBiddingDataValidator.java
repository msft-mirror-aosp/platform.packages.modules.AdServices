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

package com.android.adservices.service.customaudience;

import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG;

import android.adservices.customaudience.TrustedBiddingData;
import android.annotation.NonNull;

import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.Validator;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;

import java.util.Locale;
import java.util.Objects;

/** Validator for {@link TrustedBiddingData}. */
public class TrustedBiddingDataValidator implements Validator<TrustedBiddingData> {
    @VisibleForTesting
    static final String TRUSTED_BIDDING_DATA_CLASS_NAME = TrustedBiddingData.class.getName();

    public static final String TRUSTED_BIDDING_URI_FIELD_NAME = "trusted bidding URI";

    @NonNull private final AdTechUriValidator mAdTechUriValidator;
    private final int mCustomAudienceMaxTrustedBiddingDataSizeB;

    public TrustedBiddingDataValidator(
            @NonNull String buyer, int customAudienceMaxTrustedBiddingDataSizeB) {
        Objects.requireNonNull(buyer);

        mAdTechUriValidator =
                new AdTechUriValidator(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        buyer,
                        TRUSTED_BIDDING_DATA_CLASS_NAME,
                        TRUSTED_BIDDING_URI_FIELD_NAME);
        mCustomAudienceMaxTrustedBiddingDataSizeB = customAudienceMaxTrustedBiddingDataSizeB;
    }

    /**
     * Validates the {@link TrustedBiddingData} as follows:
     *
     * <ul>
     *   <li>{@link TrustedBiddingData#getTrustedBiddingUri()} is well-formed.
     *   <li>Size is less than {@link #mCustomAudienceMaxTrustedBiddingDataSizeB}
     * </ul>
     *
     * @param trustedBiddingData the {@link TrustedBiddingData} instance to be validated.
     * @param violations the collection of violations to add to.
     */
    @Override
    public void addValidation(
            @NonNull TrustedBiddingData trustedBiddingData,
            @NonNull ImmutableCollection.Builder<String> violations) {
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(violations);

        // Validate trustedBiddingUri is well-formed.
        mAdTechUriValidator.addValidation(trustedBiddingData.getTrustedBiddingUri(), violations);

        // Validate the size is within limits.
        int trustedBiddingDataSize =
                DBTrustedBiddingData.fromServiceObject(trustedBiddingData).size();
        if (trustedBiddingDataSize > mCustomAudienceMaxTrustedBiddingDataSizeB) {
            violations.add(
                    String.format(
                            Locale.ENGLISH,
                            VIOLATION_TRUSTED_BIDDING_DATA_TOO_BIG,
                            mCustomAudienceMaxTrustedBiddingDataSizeB,
                            trustedBiddingDataSize));
        }
    }
}
