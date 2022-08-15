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
import android.adservices.common.AdTechIdentifier;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.adservices.service.common.Validator;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableCollection;
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
    static final String TRUSTED_SCORING_SIGNALS_URI_TYPE = "Trusted Scoring Signal";

    @VisibleForTesting static final String DECISION_LOGIC_URI_TYPE = "Decision Logic";

    @VisibleForTesting
    static final String URI_SHOULD_HAVE_PRESENT_HOST =
            "The AdSelectionConfig %s URI should have a valid host.";

    @VisibleForTesting
    static final String SELLER_AND_URI_HOST_ARE_INCONSISTENT =
            "The seller hostname \"%s\" and the seller-provided "
                    + "hostname \"%s\" are not "
                    + "consistent in \"%s\" URI.";

    @VisibleForTesting
    static final String URI_IS_NOT_ABSOLUTE = "The AdSelection %s URI should be absolute";

    @VisibleForTesting
    static final String URI_IS_NOT_HTTPS = "The AdSelection %s URI is not secured by https";

    private static final String HTTPS_SCHEME = "https";

    @Override
    public void addValidation(
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull ImmutableCollection.Builder<String> violations) {
        if (Objects.isNull(adSelectionConfig)) {
            violations.add("The adSelectionConfig should not be null.");
        }
        violations.addAll(validateSeller(adSelectionConfig.getSeller()));
        violations.addAll(
                validateSellerDecisionUrls(
                        adSelectionConfig.getSeller(), adSelectionConfig.getDecisionLogicUri()));
        violations.addAll(
                validateTrustedSignalsUri(
                        adSelectionConfig.getSeller(),
                        adSelectionConfig.getTrustedScoringSignalsUri()));
    }

    /**
     * Validate the seller and seller-provided decision_logic_url in the {@link AdSelectionConfig}.
     *
     * <p>TODO(b/238849930) Replace seller validation with validation in AdTechIdentifier
     *
     * @param seller is the string name of the ssp.
     * @param decisionLogicUri is the seller provided decision logic url.
     * @return a list of strings of messages from each violation.
     */
    private ImmutableList<String> validateSellerDecisionUrls(
            @NonNull AdTechIdentifier seller, @NonNull Uri decisionLogicUri) {
        return validateUriAndSellerHost(DECISION_LOGIC_URI_TYPE, decisionLogicUri, seller);
    }

    /**
     * Validate the seller and seller-provided decision_logic_url in the {@link AdSelectionConfig}.
     *
     * @param seller is the string name of the ssp.
     * @param trustedSignalsUri is the seller provided URI to fetch trusted scoring signals.
     * @return a list of strings of messages from each violation.
     */
    private ImmutableList<String> validateTrustedSignalsUri(
            @NonNull AdTechIdentifier seller, @NonNull Uri trustedSignalsUri) {
        return validateUriAndSellerHost(
                TRUSTED_SCORING_SIGNALS_URI_TYPE, trustedSignalsUri, seller);
    }

    // TODO(b/238658332) fold this validation into the AdTechIdentifier class
    private ImmutableList<String> validateSeller(@NonNull AdTechIdentifier sellerId) {
        String seller = sellerId.toString();
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        String sellerHost = Uri.parse("https://" + seller).getHost();
        if (isStringNullOrEmpty(seller)) {
            violations.add(SELLER_SHOULD_NOT_BE_NULL_OR_EMPTY);
        } else if (Objects.isNull(sellerHost) || sellerHost.isEmpty()) {
            violations.add(SELLER_HAS_MISSING_DOMAIN_NAME);
        } else if (!Objects.equals(sellerHost, seller) || !InternetDomainName.isValid(seller)) {
            violations.add(SELLER_IS_AN_INVALID_DOMAIN_NAME);
        }

        return violations.build();
    }

    private ImmutableList<String> validateUriAndSellerHost(
            @NonNull String uriType, @NonNull Uri uri, @NonNull AdTechIdentifier sellerId) {
        String seller = sellerId.toString();
        ImmutableList.Builder<String> violations = new ImmutableList.Builder<>();
        if (!uri.isAbsolute()) {
            violations.add(String.format(URI_IS_NOT_ABSOLUTE, uriType));
        } else if (!uri.getScheme().equals(HTTPS_SCHEME)) {
            violations.add(String.format(URI_IS_NOT_HTTPS, uriType));
        }

        String sellerHost = Uri.parse("https://" + seller).getHost();
        String uriHost = uri.getHost();
        if (isStringNullOrEmpty(uriHost)) {
            violations.add(String.format(URI_SHOULD_HAVE_PRESENT_HOST, uriType));
        } else if (!seller.isEmpty()
                && !Objects.isNull(sellerHost)
                && !sellerHost.isEmpty()
                && !uriHost.equalsIgnoreCase(sellerHost)) {
            violations.add(
                    String.format(
                            SELLER_AND_URI_HOST_ARE_INCONSISTENT, sellerHost, uriHost, uriType));
        }
        return violations.build();
    }

    private boolean isStringNullOrEmpty(@Nullable String str) {
        return Objects.isNull(str) || str.isEmpty();
    }
}
