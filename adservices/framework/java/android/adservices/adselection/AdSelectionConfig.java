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
 * Instances of this class are created by SDKs to be provided as arguments to the
 * {@link AdSelectionManager#runAdSelection} and {@link AdSelectionManager#reportImpression} methods
 * in {@link AdSelectionManager}.
 */
// TODO(b/233280314): investigate on adSelectionConfig optimization by merging mCustomAudienceBuyers
//  and mPerBuyerSignals.
public final class AdSelectionConfig implements Parcelable {
    @NonNull
    private final String mSeller;
    @NonNull private final Uri mDecisionLogicUri;
    @NonNull
    private final List<String> mCustomAudienceBuyers;
    @NonNull
    private final String mAdSelectionSignals;
    @NonNull
    private final String mSellerSignals;
    @NonNull
    private final Map<String, String> mPerBuyerSignals;
    @NonNull
    private final List<AdWithBid> mContextualAds;

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
            @NonNull String seller,
            @NonNull Uri decisionLogicUri,
            @NonNull List<String> customAudienceBuyers,
            @NonNull String adSelectionSignals,
            @NonNull String sellerSignals,
            @NonNull Map<String, String> perBuyerSignals,
            @NonNull List<AdWithBid> contextualAds) {
        this.mSeller = seller;
        this.mDecisionLogicUri = decisionLogicUri;
        this.mCustomAudienceBuyers = customAudienceBuyers;
        this.mAdSelectionSignals = adSelectionSignals;
        this.mSellerSignals = sellerSignals;
        this.mPerBuyerSignals = perBuyerSignals;
        this.mContextualAds = contextualAds;
    }

    private AdSelectionConfig(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mSeller = in.readString();
        mDecisionLogicUri = Uri.CREATOR.createFromParcel(in);
        mCustomAudienceBuyers = in.createStringArrayList();
        mAdSelectionSignals = in.readString();
        mSellerSignals = in.readString();
        mPerBuyerSignals = bundleToMap(Bundle.CREATOR.createFromParcel(in));
        mContextualAds = in.createTypedArrayList(AdWithBid.CREATOR);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeString(mSeller);
        mDecisionLogicUri.writeToParcel(dest, flags);
        dest.writeStringList(mCustomAudienceBuyers);
        dest.writeString(mAdSelectionSignals);
        dest.writeString(mSellerSignals);
        dest.writeBundle(mapToBundle(mPerBuyerSignals));
        dest.writeTypedList(mContextualAds);
    }

    /** Converts {@link Map} to {@link Bundle} for {@link #writeToParcel(Parcel, int)}. */
    private static Bundle mapToBundle(@NonNull Map<String, String> stringMap) {
        Objects.requireNonNull(stringMap);
        Bundle result = new Bundle();
        stringMap.forEach(result::putString);
        return result;
    }

    /**
     * Converts {@link Bundle} to {@link Map} for constructing an {@link AdSelectionConfig}
     * object from a {@link Parcel}.
     */
    private static Map<String, String> bundleToMap(@NonNull Bundle bundle) {
        Objects.requireNonNull(bundle);

        Map<String, String> result = new HashMap<>();
        for (String key : bundle.keySet()) {
            result.put(key, bundle.getString(key));
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
                && Objects.equals(mContextualAds, that.mContextualAds);
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
                mContextualAds);
    }

    /**
     * @return a String identifier of the seller, for example "www.example-ssp.com"
     */
    @NonNull
    public String getSeller() {
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
     * selection process
     */
    @NonNull
    public List<String> getCustomAudienceBuyers() {
        return mCustomAudienceBuyers;
    }

    /**
     * @return a valid JSON object serialized as a String, fetched from the
     * AdSelectionConfig and consumed by the JS logic fetched from the DSP, represents signals
     * given to the participating buyers in the ad selection and reporting processes.
     */
    @NonNull
    public String getAdSelectionSignals() {
        return mAdSelectionSignals;
    }

    /**
     * @return a valid JSON object serialized as a String, provided by the SSP and
     * consumed by the JS logic fetched from the SSP, represents any information that the SSP used
     * in the ad scoring process to tweak the results of the ad selection process
     * (e.g. brand safety checks, excluded contextual ads).
     */
    @NonNull
    public String getSellerSignals() {
        return mSellerSignals;
    }

    /**
     * @return a Map of buyers and JSON object serialized strings, fetched from the
     * AdSelectionConfig and consumed by the JS logic fetched from the DSP, representing any
     * information that each buyer would provide during ad selection to participants
     * (such as bid floor, ad selection type, etc.)
     */
    @NonNull
    public Map<String, String> getPerBuyerSignals() {
        return mPerBuyerSignals;
    }

    /**
     * Any {@link AdWithBid} objects returned here will actively participate in the ad selection
     * process.  If an ad network wishes to exclude any contextual ads from ad selection but still
     * make those excluded ads, bids, and metadata visible to their JS code, the ad network should
     * embed this information in the seller signals.
     *
     * @return the list of contextual ads and corresponding bids, populated by the contextual
     * response received by the ad SDK, that participate in the ad selection process
     */
    @NonNull
    public List<AdWithBid> getContextualAds() {
        return mContextualAds;
    }

    /** Builder for {@link AdSelectionConfig} object. */
    public static final class Builder {
        private String mSeller;
        private Uri mDecisionLogicUri;
        private List<String> mCustomAudienceBuyers;
        private String mAdSelectionSignals = "{}";
        private String mSellerSignals = "{}";
        private Map<String, String> mPerBuyerSignals = Collections.emptyMap();
        private List<AdWithBid> mContextualAds = Collections.emptyList();

        public Builder() {
        }

        /**
         * Sets the seller identifier.
         *
         * See {@link #getSeller()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSeller(@NonNull String seller) {
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
         * See {@link #getCustomAudienceBuyers()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setCustomAudienceBuyers(
                @NonNull List<String> customAudienceBuyers) {
            Objects.requireNonNull(customAudienceBuyers);

            this.mCustomAudienceBuyers = customAudienceBuyers;
            return this;
        }

        /**
         * Sets the signals provided to buyers during ad selection bid generation.
         *
         * <p>If not set, defaults to an empty JSON object serialized as a String.
         *
         * <p>See {@link #getAdSelectionSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setAdSelectionSignals(@NonNull String adSelectionSignals) {
            Objects.requireNonNull(adSelectionSignals);

            this.mAdSelectionSignals = adSelectionSignals;
            return this;
        }

        /**
         * Set the signals used to modify ad selection results.
         *
         * <p>If not set, defaults to an empty JSON object serialized as a String.
         *
         * <p>See {@link #getSellerSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSellerSignals(@NonNull String sellerSignals) {
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
                @NonNull Map<String, String> perBuyerSignals) {
            Objects.requireNonNull(perBuyerSignals);

            this.mPerBuyerSignals = perBuyerSignals;
            return this;
        }

        /**
         * Sets the list of contextual ads and bids that will participate in the ad selection
         * process. Contextual ads and ad information that should be excluded from the ad selection
         * and still be visible to the ad selection JS logic should instead be embedded in seller
         * signals.
         *
         * <p>If not set, defaults to an empty list.
         *
         * <p>See {@link #getContextualAds()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setContextualAds(@NonNull List<AdWithBid> contextualAds) {
            Objects.requireNonNull(contextualAds);

            this.mContextualAds = contextualAds;
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
            Objects.requireNonNull(mContextualAds);
            return new AdSelectionConfig(
                    mSeller,
                    mDecisionLogicUri,
                    mCustomAudienceBuyers,
                    mAdSelectionSignals,
                    mSellerSignals,
                    mPerBuyerSignals,
                    mContextualAds);
        }
    }
}
