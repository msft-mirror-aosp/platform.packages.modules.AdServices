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
package android.adservices.adselection;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains the configuration of the ad selection process.
 *
 * <p>Instances of this class are created by SDKs to be provided as arguments to the {@link
 * AdSelectionManager#selectAds} and {@link AdSelectionManager#reportImpression} methods in {@link
 * AdSelectionManager}.
 */
// TODO(b/233280314): investigate on adSelectionConfig optimization by merging mCustomAudienceBuyers
//  and mPerBuyerSignals.
public final class AdSelectionConfig implements Parcelable {
    @NonNull private final AdTechIdentifier mSeller;
    @NonNull private final Uri mDecisionLogicUri;
    @NonNull private final List<AdTechIdentifier> mCustomAudienceBuyers;
    @NonNull private final AdSelectionSignals mAdSelectionSignals;
    @NonNull private final AdSelectionSignals mSellerSignals;
    @NonNull private final Map<AdTechIdentifier, AdSelectionSignals> mPerBuyerSignals;
    @NonNull private final Uri mTrustedScoringSignalsUri;

    @NonNull
    public static final Creator<AdSelectionConfig> CREATOR =
            new Creator<AdSelectionConfig>() {
                @Override
                public AdSelectionConfig createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);
                    return new AdSelectionConfig(in);
                }

                @Override
                public AdSelectionConfig[] newArray(int size) {
                    return new AdSelectionConfig[size];
                }
            };

    private AdSelectionConfig(
            @NonNull AdTechIdentifier seller,
            @NonNull Uri decisionLogicUri,
            @NonNull List<AdTechIdentifier> customAudienceBuyers,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull AdSelectionSignals sellerSignals,
            @NonNull Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals,
            @NonNull Uri trustedScoringSignalsUri) {
        this.mSeller = seller;
        this.mDecisionLogicUri = decisionLogicUri;
        this.mCustomAudienceBuyers = customAudienceBuyers;
        this.mAdSelectionSignals = adSelectionSignals;
        this.mSellerSignals = sellerSignals;
        this.mPerBuyerSignals = perBuyerSignals;
        this.mTrustedScoringSignalsUri = trustedScoringSignalsUri;
    }

    private AdSelectionConfig(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mSeller = AdTechIdentifier.CREATOR.createFromParcel(in);
        mDecisionLogicUri = Uri.CREATOR.createFromParcel(in);
        mCustomAudienceBuyers = in.createTypedArrayList(AdTechIdentifier.CREATOR);
        mAdSelectionSignals = AdSelectionSignals.CREATOR.createFromParcel(in);
        mSellerSignals = AdSelectionSignals.CREATOR.createFromParcel(in);
        mPerBuyerSignals = bundleToAdTechSignalMap(Bundle.CREATOR.createFromParcel(in));
        mTrustedScoringSignalsUri = Uri.CREATOR.createFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mSeller.writeToParcel(dest, flags);
        mDecisionLogicUri.writeToParcel(dest, flags);
        dest.writeTypedList(mCustomAudienceBuyers);
        mAdSelectionSignals.writeToParcel(dest, flags);
        mSellerSignals.writeToParcel(dest, flags);
        dest.writeBundle(adTechSignalMapToBundle(mPerBuyerSignals));
        mTrustedScoringSignalsUri.writeToParcel(dest, flags);
    }

    /**
     * Converts {@code Map<AdTechIdentifier, AdSelectionSignals>} to {@link Bundle} for {@link
     * #writeToParcel(Parcel, int)}.
     */
    private static Bundle adTechSignalMapToBundle(
            @NonNull Map<AdTechIdentifier, AdSelectionSignals> map) {
        Objects.requireNonNull(map);
        Bundle result = new Bundle();
        for (Map.Entry<AdTechIdentifier, AdSelectionSignals> entry : map.entrySet()) {
            result.putParcelable(entry.getKey().toString(), entry.getValue());
        }
        return result;
    }

    /**
     * Converts {@link Bundle} to {@code Map<AdTechIdentifier, AdSelectionSignals>} for constructing
     * an {@link AdSelectionConfig} object from a {@link Parcel}.
     */
    private static Map<AdTechIdentifier, AdSelectionSignals> bundleToAdTechSignalMap(
            @NonNull Bundle bundle) {
        Objects.requireNonNull(bundle);

        Map<AdTechIdentifier, AdSelectionSignals> result = new HashMap<>();
        for (String key : bundle.keySet()) {
            result.put(
                    AdTechIdentifier.fromString(key),
                    bundle.getParcelable(key, AdSelectionSignals.class));
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdSelectionConfig)) return false;
        AdSelectionConfig that = (AdSelectionConfig) o;
        return Objects.equals(mSeller, that.mSeller)
                && Objects.equals(mDecisionLogicUri, that.mDecisionLogicUri)
                && Objects.equals(mCustomAudienceBuyers, that.mCustomAudienceBuyers)
                && Objects.equals(mAdSelectionSignals, that.mAdSelectionSignals)
                && Objects.equals(mSellerSignals, that.mSellerSignals)
                && Objects.equals(mPerBuyerSignals, that.mPerBuyerSignals)
                && Objects.equals(mTrustedScoringSignalsUri, that.mTrustedScoringSignalsUri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSeller,
                mDecisionLogicUri,
                mCustomAudienceBuyers,
                mAdSelectionSignals,
                mSellerSignals,
                mPerBuyerSignals,
                mTrustedScoringSignalsUri);
    }

    /** @return a AdTechIdentifier of the seller, for example "www.example-ssp.com" */
    @NonNull
    public AdTechIdentifier getSeller() {
        return mSeller;
    }

    /**
     * @return the URL used to retrieve the JS code containing the seller/SSP scoreAd function used
     *     during the ad selection and reporting processes
     */
    @NonNull
    public Uri getDecisionLogicUri() {
        return mDecisionLogicUri;
    }

    /**
     * @return a list of custom audience buyers allowed by the SSP to participate in the ad
     *     selection process
     */
    @NonNull
    public List<AdTechIdentifier> getCustomAudienceBuyers() {
        return mCustomAudienceBuyers;
    }

    /**
     * @return JSON in an AdSelectionSignals object, fetched from the AdSelectionConfig and consumed
     *     by the JS logic fetched from the DSP, represents signals given to the participating
     *     buyers in the ad selection and reporting processes.
     */
    @NonNull
    public AdSelectionSignals getAdSelectionSignals() {
        return mAdSelectionSignals;
    }

    /**
     * @return JSON in an AdSelectionSignals object, provided by the SSP and consumed by the JS
     *     logic fetched from the SSP, represents any information that the SSP used in the ad
     *     scoring process to tweak the results of the ad selection process (e.g. brand safety
     *     checks, excluded contextual ads).
     */
    @NonNull
    public AdSelectionSignals getSellerSignals() {
        return mSellerSignals;
    }

    /**
     * @return a Map of buyers and AdSelectionSignals, fetched from the AdSelectionConfig and
     *     consumed by the JS logic fetched from the DSP, representing any information that each
     *     buyer would provide during ad selection to participants (such as bid floor, ad selection
     *     type, etc.)
     */
    @NonNull
    public Map<AdTechIdentifier, AdSelectionSignals> getPerBuyerSignals() {
        return mPerBuyerSignals;
    }

    /**
     * @return URL endpoint of sell-side trusted signal from which creative specific realtime
     *     information can be fetched from.
     */
    @NonNull
    public Uri getTrustedScoringSignalsUri() {
        return mTrustedScoringSignalsUri;
    }

    /** Builder for {@link AdSelectionConfig} object. */
    public static final class Builder {
        private AdTechIdentifier mSeller;
        private Uri mDecisionLogicUri;
        private List<AdTechIdentifier> mCustomAudienceBuyers;
        private AdSelectionSignals mAdSelectionSignals = AdSelectionSignals.EMPTY;
        private AdSelectionSignals mSellerSignals = AdSelectionSignals.EMPTY;
        private Map<AdTechIdentifier, AdSelectionSignals> mPerBuyerSignals = Collections.emptyMap();
        private Uri mTrustedScoringSignalsUri;

        public Builder() {}

        /**
         * Sets the seller identifier.
         *
         * <p>See {@link #getSeller()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSeller(@NonNull AdTechIdentifier seller) {
            Objects.requireNonNull(seller);

            this.mSeller = seller;
            return this;
        }

        /**
         * Sets the URI used to fetch decision logic for use in the ad selection process.
         *
         * <p>See {@link #getDecisionLogicUri()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setDecisionLogicUri(@NonNull Uri decisionLogicUri) {
            Objects.requireNonNull(decisionLogicUri);

            this.mDecisionLogicUri = decisionLogicUri;
            return this;
        }

        /**
         * Sets the list of allowed buyers.
         *
         * <p>See {@link #getCustomAudienceBuyers()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setCustomAudienceBuyers(
                @NonNull List<AdTechIdentifier> customAudienceBuyers) {
            Objects.requireNonNull(customAudienceBuyers);

            this.mCustomAudienceBuyers = customAudienceBuyers;
            return this;
        }

        /**
         * Sets the signals provided to buyers during ad selection bid generation.
         *
         * <p>If not set, defaults to the empty JSON.
         *
         * <p>See {@link #getAdSelectionSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setAdSelectionSignals(
                @NonNull AdSelectionSignals adSelectionSignals) {
            Objects.requireNonNull(adSelectionSignals);

            this.mAdSelectionSignals = adSelectionSignals;
            return this;
        }

        /**
         * Set the signals used to modify ad selection results.
         *
         * <p>If not set, defaults to the empty JSON.
         *
         * <p>See {@link #getSellerSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSellerSignals(
                @NonNull AdSelectionSignals sellerSignals) {
            Objects.requireNonNull(sellerSignals);

            this.mSellerSignals = sellerSignals;
            return this;
        }

        /**
         * Sets the signals provided by each buyer during ad selection.
         *
         * <p>If not set, defaults to an empty map.
         *
         * <p>See {@link #getPerBuyerSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setPerBuyerSignals(
                @NonNull Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals) {
            Objects.requireNonNull(perBuyerSignals);

            this.mPerBuyerSignals = perBuyerSignals;
            return this;
        }

        /**
         * Sets the URL endpoint of sell-side trusted signal from which creative specific realtime
         * information can be fetched from.
         *
         * <p>See {@link #getTrustedScoringSignalsUri()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setTrustedScoringSignalsUri(
                @NonNull Uri trustedScoringSignalsUri) {
            Objects.requireNonNull(trustedScoringSignalsUri);

            this.mTrustedScoringSignalsUri = trustedScoringSignalsUri;
            return this;
        }

        /**
         * Builds an {@link AdSelectionConfig} instance.
         *
         * @throws NullPointerException if any required params are null
         */
        @NonNull
        public AdSelectionConfig build() {
            Objects.requireNonNull(mSeller);
            Objects.requireNonNull(mDecisionLogicUri);
            Objects.requireNonNull(mCustomAudienceBuyers);
            Objects.requireNonNull(mAdSelectionSignals);
            Objects.requireNonNull(mSellerSignals);
            Objects.requireNonNull(mPerBuyerSignals);
            Objects.requireNonNull(mTrustedScoringSignalsUri);
            return new AdSelectionConfig(
                    mSeller,
                    mDecisionLogicUri,
                    mCustomAudienceBuyers,
                    mAdSelectionSignals,
                    mSellerSignals,
                    mPerBuyerSignals,
                    mTrustedScoringSignalsUri);
        }
    }
}
