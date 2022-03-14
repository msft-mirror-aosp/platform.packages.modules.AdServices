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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Contains the configuration of the ad selection process.
 *
 * Instances of this class are created by SDKs to be provided as arguments to the
 * {@code runAdSelection} and {@code reportImpression} methods in {@code AdSelectionService}.
 * TODO(b/211030283): properly link runAdSelection javadoc
 * TODO(b/212300065): properly link reportImpression javadoc
 *
 * Hiding for future implementation and review for public exposure.
 * @hide
 */
public final class AdSelectionConfig implements Parcelable {
    @NonNull
    private final String mSeller;
    @NonNull
    private final Uri mDecisionLogicUrl;
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
                public AdSelectionConfig createFromParcel(Parcel in) {
                    return new AdSelectionConfig(in);
                }

                @Override
                public AdSelectionConfig[] newArray(int size) {
                    return new AdSelectionConfig[size];
                }
            };

    private AdSelectionConfig(
            @NonNull String seller,
            @NonNull Uri decisionLogicUrl,
            @NonNull List<String> customAudienceBuyers,
            @NonNull String adSelectionSignals,
            @NonNull String sellerSignals,
            @NonNull Map<String, String> perBuyerSignals,
            @NonNull List<AdWithBid> contextualAds) {
        this.mSeller = seller;
        this.mDecisionLogicUrl = decisionLogicUrl;
        this.mCustomAudienceBuyers = customAudienceBuyers;
        this.mAdSelectionSignals = adSelectionSignals;
        this.mSellerSignals = sellerSignals;
        this.mPerBuyerSignals = perBuyerSignals;
        this.mContextualAds = contextualAds;
    }

    private AdSelectionConfig(@NonNull Parcel in) {
        mSeller = in.readString();
        mDecisionLogicUrl = Uri.CREATOR.createFromParcel(in);
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
        dest.writeString(mSeller);
        mDecisionLogicUrl.writeToParcel(dest, flags);
        dest.writeStringList(mCustomAudienceBuyers);
        dest.writeString(mAdSelectionSignals);
        dest.writeString(mSellerSignals);
        dest.writeBundle(mapToBundle(mPerBuyerSignals));
        dest.writeTypedList(mContextualAds);
    }

    /** Converts {@link Map} to {@link Bundle} for {@link #writeToParcel(Parcel, int)}. */
    private static Bundle mapToBundle(Map<String, String> stringMap) {
        Bundle result = new Bundle();
        stringMap.forEach(result::putString);
        return result;
    }

    /**
     * Converts {@link Bundle} to {@link Map} for constructing an {@link AdSelectionConfig}
     * object from a {@link Parcel}.
     */
    private static Map<String, String> bundleToMap(Bundle bundle) {
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
                && Objects.equals(mDecisionLogicUrl, that.mDecisionLogicUrl)
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
                mDecisionLogicUrl,
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
     * during the ad selection and reporting processes
     */
    @NonNull
    public Uri getDecisionLogicUrl() {
        return mDecisionLogicUrl;
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
     * @return an opaque String provided by the SSP representing signals given to the participating
     * buyers in the ad selection process to generate a bid
     */
    @NonNull
    public String getAdSelectionSignals() {
        return mAdSelectionSignals;
    }

    /**
     * @return an opaque String used in the ad scoring process that represents any information that
     * the SSP would have used to tweak the results of the ad selection process (e.g. brand
     * safety checks, excluded contextual ads)
     */
    @NonNull
    public String getSellerSignals() {
        return mSellerSignals;
    }

    /**
     * @return a Map of buyers and opaque strings representing any information that each buyer
     * would provide during ad selection to participants (such as bid floor, ad selection type,
     * etc.)
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
        private Uri mDecisionLogicURL;
        private List<String> mCustomAudienceBuyers;
        private String mAdSelectionSignals;
        private String mSellerSignals;
        private Map<String, String> mPerBuyerSignals;
        private List<AdWithBid> mContextualAds;

        public Builder() {
        }

        /**
         * Sets the seller identifier.
         *
         * See {@link #getSeller()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSeller(@NonNull String seller) {
            this.mSeller = seller;
            return this;
        }

        /**
         * Sets the URL used to fetch decision logic for use in the ad selection process.
         *
         * See {@link #getDecisionLogicUrl()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setDecisionLogicUrl(@NonNull Uri decisionLogicURL) {
            this.mDecisionLogicURL = decisionLogicURL;
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
            this.mCustomAudienceBuyers = customAudienceBuyers;
            return this;
        }

        /**
         * Sets the opaque signals provided to buyers during ad selection bid generation.
         *
         * See {@link #getAdSelectionSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setAdSelectionSignals(@NonNull String adSelectionSignals) {
            this.mAdSelectionSignals = adSelectionSignals;
            return this;
        }

        /**
         * Set the opaque signals used to modify ad selection results.
         *
         * See {@link #getSellerSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setSellerSignals(@NonNull String sellerSignals) {
            this.mSellerSignals = sellerSignals;
            return this;
        }

        /**
         * Sets the opaque signals provided by each buyer during ad selection.
         *
         * See {@link #getPerBuyerSignals()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setPerBuyerSignals(
                @NonNull Map<String, String> perBuyerSignals) {
            this.mPerBuyerSignals = perBuyerSignals;
            return this;
        }

        /**
         * Sets the list of contextual ads and bids that will participate in the ad selection
         * process.  Contextual ads and ad information that should be excluded from the ad selection
         * and still be visible to the ad selection JS logic should instead be embedded in seller
         * signals.
         *
         * See {@link #getContextualAds()} for more details.
         */
        @NonNull
        public AdSelectionConfig.Builder setContextualAds(@NonNull List<AdWithBid> contextualAds) {
            this.mContextualAds = contextualAds;
            return this;
        }

        /**
         * Builds an {@link AdSelectionConfig} instance.
         *
         * @throws NullPointerException if any params are null
         */
        @NonNull
        public AdSelectionConfig build() {
            Objects.requireNonNull(mSeller);
            Objects.requireNonNull(mDecisionLogicURL);
            Objects.requireNonNull(mCustomAudienceBuyers);
            Objects.requireNonNull(mAdSelectionSignals);
            Objects.requireNonNull(mSellerSignals);
            Objects.requireNonNull(mPerBuyerSignals);
            Objects.requireNonNull(mContextualAds);
            return new AdSelectionConfig(
                    mSeller,
                    mDecisionLogicURL,
                    mCustomAudienceBuyers,
                    mAdSelectionSignals,
                    mSellerSignals,
                    mPerBuyerSignals,
                    mContextualAds);
        }
    }
}
