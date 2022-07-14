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

import android.adservices.adselection.AdSelectionConfig;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.net.InternetDomainName;

import java.util.Objects;

/** This class runs the validation of the {@link AdSelectionConfig} subfields. */
public class AdSelectionConfigValidator implements Validator<AdSelectionConfig> {

    @VisibleForTesting
    static final String SELLER_SHOULD_NOT_BE_NULL_OR_EMPTY =
            "The AdSelectionConfig's seller should not be null nor empty.";

    @VisibleForTesting
    static final String SELLER_HAS_MISSING_DOMAIN_NAME =
            "The AdSelectionConfig seller has missing domain name.";

    @VisibleForTesting
    static final String SELLER_IS_AN_INVALID_DOMAIN_NAME =
            "The AdSelectionConfig seller is an invalid domain name.";

    @VisibleForTesting
    static final String DECISION_LOGIC_URL_SHOULD_HAVE_PRESENT_HOST =
            "The AdSelectionConfig decisionLogicUrl should have present host.";

    @VisibleForTesting
    static final String SELLER_AND_DECISION_LOGIC_URL_ARE_INCONSISTENT =
            "The seller host name %s and the seller-provided "
                    + "decision logic urls host name %s are not"
                    + " consistent.";

    @Override
    public ImmutableList<String> getValidationViolations(
            @NonNull AdSelectionConfig adSelectionConfig) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        if (Objects.isNull(adSelectionConfig)) {
            violations.add("The adSelectionConfig should not be null.");
        }
        violations.addAll(
                validateSellerAndSellerDecisionUrls(
                        adSelectionConfig.getSeller(), adSelectionConfig.getDecisionLogicUrl()));

        return violations.build();
    }

    /**
     * Validate the seller and seller-provided decision_logic_url in the {@link AdSelectionConfig}.
     *
     * @param seller is the string name of the ssp.
     * @param decisionLogicUrl is the seller provided decision logic url.
     * @return a list of strings of messages from each violation.
     */
    private ImmutableList<String> validateSellerAndSellerDecisionUrls(
            @NonNull String seller, @NonNull Uri decisionLogicUrl) {
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        String sellerHost = Uri.parse("https://" + seller).getHost();
        if (isStringNullOrEmpty(seller)) {
            violations.add(SELLER_SHOULD_NOT_BE_NULL_OR_EMPTY);
        } else if (Objects.isNull(sellerHost) || sellerHost.isEmpty()) {
            violations.add(SELLER_HAS_MISSING_DOMAIN_NAME);
        } else if (!Objects.equals(sellerHost, seller) || !InternetDomainName.isValid(seller)) {
            violations.add(SELLER_IS_AN_INVALID_DOMAIN_NAME);
        }

        if (Objects.isNull(decisionLogicUrl)) {
            violations.add("The AdSelectionConfig's decisionLogicUrl should be specified.");
        } else {
            String decisionLogicUrlHost = decisionLogicUrl.getHost();
            if (isStringNullOrEmpty(decisionLogicUrlHost)) {
                violations.add(DECISION_LOGIC_URL_SHOULD_HAVE_PRESENT_HOST);
            } else if (!seller.isEmpty()
                    && !Objects.isNull(sellerHost)
                    && !sellerHost.isEmpty()
                    && !decisionLogicUrlHost.equalsIgnoreCase(sellerHost)) {
                violations.add(
                        String.format(
                                SELLER_AND_DECISION_LOGIC_URL_ARE_INCONSISTENT,
                                sellerHost,
                                decisionLogicUrlHost));
            }
        }
        return violations.build();
    }

    private boolean isStringNullOrEmpty(@Nullable String str) {
        return Objects.isNull(str) || str.isEmpty();
    }
}
