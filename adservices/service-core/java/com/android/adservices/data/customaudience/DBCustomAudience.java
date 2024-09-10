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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_DEFAULT;
import static android.adservices.customaudience.CustomAudience.PRIORITY_DEFAULT;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.ProvidedTypeConverter;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.util.Preconditions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * POJO represents a Custom Audience Database Entity. TODO: Align on the class naming strategy.
 * (b/228095626)
 */
@Entity(
        tableName = DBCustomAudience.TABLE_NAME,
        primaryKeys = {"owner", "buyer", "name"})
@TypeConverters({DBCustomAudience.Converters.class})
public class DBCustomAudience {
    public static final String TABLE_NAME = "custom_audience";

    @ColumnInfo(name = "owner", index = true)
    @NonNull
    private final String mOwner;

    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    private final AdTechIdentifier mBuyer;

    @ColumnInfo(name = "name")
    @NonNull
    private final String mName;

    @ColumnInfo(name = "expiration_time", index = true)
    @NonNull
    private final Instant mExpirationTime;

    // TODO(b/234429221): Investigate and decide if should add an index on the activation_time.
    @ColumnInfo(name = "activation_time")
    @NonNull
    private final Instant mActivationTime;

    @ColumnInfo(name = "creation_time")
    @NonNull
    private final Instant mCreationTime;

    @ColumnInfo(name = "last_ads_and_bidding_data_updated_time", index = true)
    @NonNull
    private final Instant mLastAdsAndBiddingDataUpdatedTime;

    @ColumnInfo(name = "user_bidding_signals")
    @Nullable
    private final AdSelectionSignals mUserBiddingSignals;

    @Embedded(prefix = "trusted_bidding_data_")
    @Nullable
    private final DBTrustedBiddingData mTrustedBiddingData;

    @ColumnInfo(name = "bidding_logic_uri")
    @NonNull
    private final Uri mBiddingLogicUri;

    @ColumnInfo(name = "ads")
    @Nullable
    private final List<DBAdData> mAds;

    @ColumnInfo(name = "debuggable", defaultValue = "0")
    private final boolean mDebuggable;

    @ColumnInfo(name = "auction_server_request_flags", defaultValue = "0")
    @CustomAudience.AuctionServerRequestFlag
    private final int mAuctionServerRequestFlags;

    @ColumnInfo(name = "priority", defaultValue = "0.0")
    private final double mPriority;

    public DBCustomAudience(
            @NonNull String owner,
            @NonNull AdTechIdentifier buyer,
            @NonNull String name,
            @NonNull Instant expirationTime,
            @NonNull Instant activationTime,
            @NonNull Instant creationTime,
            @NonNull Instant lastAdsAndBiddingDataUpdatedTime,
            @Nullable AdSelectionSignals userBiddingSignals,
            @Nullable DBTrustedBiddingData trustedBiddingData,
            @NonNull Uri biddingLogicUri,
            @Nullable List<DBAdData> ads,
            boolean debuggable,
            @CustomAudience.AuctionServerRequestFlag int auctionServerRequestFlags,
            double priority) {
        Preconditions.checkStringNotEmpty(owner, "Owner must be provided");
        Objects.requireNonNull(buyer, "Buyer must be provided.");
        Preconditions.checkStringNotEmpty(name, "Name must be provided");
        Objects.requireNonNull(expirationTime, "Expiration time must be provided.");
        Objects.requireNonNull(creationTime, "Creation time must be provided.");
        Objects.requireNonNull(
                lastAdsAndBiddingDataUpdatedTime,
                "Last ads and bidding data updated time must be provided.");
        Objects.requireNonNull(biddingLogicUri, "Bidding logic uri must be provided.");

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mExpirationTime = expirationTime;
        mActivationTime = activationTime;
        mCreationTime = creationTime;
        mLastAdsAndBiddingDataUpdatedTime = lastAdsAndBiddingDataUpdatedTime;
        mUserBiddingSignals = userBiddingSignals;
        mTrustedBiddingData = trustedBiddingData;
        mBiddingLogicUri = biddingLogicUri;
        mAds = ads;
        mDebuggable = debuggable;
        mAuctionServerRequestFlags = auctionServerRequestFlags;
        mPriority = priority;
    }

    /**
     * Parse parcelable {@link CustomAudience} to storage model {@link DBCustomAudience}.
     *
     * @param parcelable the service model
     * @param callerPackageName the String package name for the calling application, used as the
     *     owner app identifier
     * @param currentTime the timestamp when calling the method
     * @param defaultExpireIn the default expiration from activation
     * @param adDataConversionStrategy Strategy to convert ads from DB
     * @param debuggable If the CA was created in a debuggable context
     * @param auctionServerRequestFlagsEnabled If auction server request flags are enabled.
     * @param sellerConfigurationFlagEnabled If the seller configuration flag is enabled.
     * @return storage model
     */
    @NonNull
    public static DBCustomAudience fromServiceObject(
            @NonNull CustomAudience parcelable,
            @NonNull String callerPackageName,
            @NonNull Instant currentTime,
            @NonNull Duration defaultExpireIn,
            @NonNull AdDataConversionStrategy adDataConversionStrategy,
            boolean debuggable,
            boolean auctionServerRequestFlagsEnabled,
            boolean sellerConfigurationFlagEnabled) {
        Objects.requireNonNull(parcelable);
        Objects.requireNonNull(callerPackageName);
        Objects.requireNonNull(currentTime);
        Objects.requireNonNull(defaultExpireIn);
        Objects.requireNonNull(adDataConversionStrategy);

        // Setting default value to be currentTime.
        // Make it easier at query for activated CAs.
        Instant activationTime =
                Optional.ofNullable(parcelable.getActivationTime()).orElse(currentTime);
        if (activationTime.isBefore(currentTime)) {
            activationTime = currentTime;
        }

        Instant expirationTime =
                Optional.ofNullable(parcelable.getExpirationTime())
                        .orElse(activationTime.plus(defaultExpireIn));

        Instant lastAdsAndBiddingDataUpdatedTime =
                parcelable.getAds().isEmpty()
                                || parcelable.getTrustedBiddingData() == null
                                || parcelable.getUserBiddingSignals() == null
                        ? Instant.EPOCH
                        : currentTime;

        return new DBCustomAudience.Builder()
                .setName(parcelable.getName())
                .setBuyer(parcelable.getBuyer())
                .setOwner(callerPackageName)
                .setActivationTime(activationTime)
                .setCreationTime(currentTime)
                .setLastAdsAndBiddingDataUpdatedTime(lastAdsAndBiddingDataUpdatedTime)
                .setExpirationTime(expirationTime)
                .setBiddingLogicUri(parcelable.getBiddingLogicUri())
                .setTrustedBiddingData(
                        DBTrustedBiddingData.fromServiceObject(parcelable.getTrustedBiddingData()))
                .setDebuggable(debuggable)
                .setAds(
                        parcelable.getAds().isEmpty()
                                ? null
                                : parcelable.getAds().stream()
                                        .map(
                                                parcelableAd ->
                                                        adDataConversionStrategy
                                                                .fromServiceObject(parcelableAd)
                                                                .build())
                                        .collect(Collectors.toList()))
                .setUserBiddingSignals(parcelable.getUserBiddingSignals())
                .setAuctionServerRequestFlags(
                        auctionServerRequestFlagsEnabled
                                ? parcelable.getAuctionServerRequestFlags()
                                : FLAG_AUCTION_SERVER_REQUEST_DEFAULT)
                .setPriority(
                        sellerConfigurationFlagEnabled
                                ? parcelable.getPriority()
                                : PRIORITY_DEFAULT)
                .build();
    }

    // TODO(b/321092996) Update this once {@link CustomAudienceUpdatableData} is updated with the
    // new field
    /**
     * Creates a copy of the current {@link DBCustomAudience} object updated with data from a {@link
     * CustomAudienceUpdatableData} object.
     */
    @NonNull
    public DBCustomAudience copyWithUpdatableData(
            @NonNull CustomAudienceUpdatableData updatableData) {
        Objects.requireNonNull(updatableData);

        if (!updatableData.getContainsSuccessfulUpdate()) {
            return this;
        }

        DBCustomAudience.Builder customAudienceBuilder =
                new Builder(this)
                        .setLastAdsAndBiddingDataUpdatedTime(
                                updatableData.getAttemptedUpdateTime());

        if (updatableData.getUserBiddingSignals() != null) {
            customAudienceBuilder.setUserBiddingSignals(updatableData.getUserBiddingSignals());
        }

        if (updatableData.getTrustedBiddingData() != null) {
            customAudienceBuilder.setTrustedBiddingData(updatableData.getTrustedBiddingData());
        }

        if (updatableData.getAds() != null) {
            customAudienceBuilder.setAds(updatableData.getAds());
        }

        return customAudienceBuilder.build();
    }

    /** The package name of the App that adds the user to this custom audience. */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /**
     * The ad-tech who can read this custom audience information and return back relevant ad
     * information. This is expected to be the domain’s name used in biddingLogicUri and
     * dailyUpdateUri.
     *
     * <p>Max length: 200 bytes
     */
    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    /**
     * Identifies the CustomAudience within the set of ones created for this combination of owner
     * and buyer.
     *
     * <p>Max length: 200 bytes
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Defines until when the CA end to be effective, this can be used to remove a user from this CA
     * only after a defined period of time.
     *
     * <p>Default to be 60 days after activation(Pending product confirm).
     *
     * <p>Should be within 1 year since activation.
     */
    @NonNull
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * Defines when the CA starts to be effective, this can be used to enroll a user to this CA only
     * after a defined interval (for example to track the fact that the user has not been using the
     * app in the last n days).
     *
     * <p>Should be within 1 year since creation.
     */
    @NonNull
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /** Returns the time the CA was created. */
    @NonNull
    public Instant getCreationTime() {
        return mCreationTime;
    }

    /** Returns the time the CA ads and bidding data was last updated. */
    @NonNull
    public Instant getLastAdsAndBiddingDataUpdatedTime() {
        return mLastAdsAndBiddingDataUpdatedTime;
    }

    /**
     * Signals needed for any on-device bidding for remarketing Ads. For instance, an App might
     * decide to store an embedding from their user features a model here while creating the custom
     * audience.
     */
    @Nullable
    public AdSelectionSignals getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    /**
     * An ad-tech can define what data needs to be fetched from a trusted server
     * (trusted_bidding_keys) and where it should be fetched from (trusted_bidding_uri).
     */
    @Nullable
    public DBTrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /** Returns the URI to fetch bidding logic js. */
    @NonNull
    public Uri getBiddingLogicUri() {
        return mBiddingLogicUri;
    }

    /** Returns Ads metadata that used to render an ad. */
    @Nullable
    public List<DBAdData> getAds() {
        return mAds;
    }

    /** Returns if CA was created in a debuggable context. */
    public boolean isDebuggable() {
        return mDebuggable;
    }

    /** Returns the bitfield of auction server request flags. */
    @CustomAudience.AuctionServerRequestFlag
    public int getAuctionServerRequestFlags() {
        return mAuctionServerRequestFlags;
    }

    /** Returns the priority value for the CA */
    public double getPriority() {
        return mPriority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DBCustomAudience)) return false;
        DBCustomAudience that = (DBCustomAudience) o;
        return mOwner.equals(that.mOwner)
                && mBuyer.equals(that.mBuyer)
                && mName.equals(that.mName)
                && mExpirationTime.equals(that.mExpirationTime)
                && mActivationTime.equals(that.mActivationTime)
                && mCreationTime.equals(that.mCreationTime)
                && mLastAdsAndBiddingDataUpdatedTime.equals(that.mLastAdsAndBiddingDataUpdatedTime)
                && Objects.equals(mUserBiddingSignals, that.mUserBiddingSignals)
                && Objects.equals(mTrustedBiddingData, that.mTrustedBiddingData)
                && mBiddingLogicUri.equals(that.mBiddingLogicUri)
                && Objects.equals(mAds, that.mAds)
                && mAuctionServerRequestFlags == that.mAuctionServerRequestFlags
                && Double.compare(mPriority, that.mPriority) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mOwner,
                mBuyer,
                mName,
                mExpirationTime,
                mActivationTime,
                mCreationTime,
                mLastAdsAndBiddingDataUpdatedTime,
                mUserBiddingSignals,
                mTrustedBiddingData,
                mBiddingLogicUri,
                mAds,
                mDebuggable,
                mAuctionServerRequestFlags,
                mPriority);
    }

    /**
     * @return a new builder instance created from this object's cloned data
     * @hide
     */
    @NonNull
    public DBCustomAudience.Builder cloneToBuilder() {
        return new DBCustomAudience.Builder()
                .setOwner(this.mOwner)
                .setBuyer(this.mBuyer)
                .setName(this.mName)
                .setExpirationTime(this.mExpirationTime)
                .setActivationTime(this.mActivationTime)
                .setCreationTime(this.mCreationTime)
                .setLastAdsAndBiddingDataUpdatedTime(this.mLastAdsAndBiddingDataUpdatedTime)
                .setUserBiddingSignals(this.mUserBiddingSignals)
                .setTrustedBiddingData(this.mTrustedBiddingData)
                .setBiddingLogicUri(this.mBiddingLogicUri)
                .setAds(this.mAds)
                .setDebuggable(this.mDebuggable)
                .setAuctionServerRequestFlags(this.mAuctionServerRequestFlags)
                .setPriority(this.mPriority);
    }

    @Override
    public String toString() {
        return "DBCustomAudience{"
                + "mOwner='"
                + mOwner
                + '\''
                + ", mBuyer="
                + mBuyer
                + ", mName='"
                + mName
                + '\''
                + ", mExpirationTime="
                + mExpirationTime
                + ", mActivationTime="
                + mActivationTime
                + ", mCreationTime="
                + mCreationTime
                + ", mLastAdsAndBiddingDataUpdatedTime="
                + mLastAdsAndBiddingDataUpdatedTime
                + ", mUserBiddingSignals="
                + mUserBiddingSignals
                + ", mTrustedBiddingData="
                + mTrustedBiddingData
                + ", mBiddingLogicUri="
                + mBiddingLogicUri
                + ", mAds="
                + mAds
                + ", mDebuggable="
                + mDebuggable
                + ", mAuctionServerRequestFlags="
                + mAuctionServerRequestFlags
                + ", mPriority="
                + mPriority
                + '}';
    }

    /** Builder to construct a {@link DBCustomAudience}. */
    public static final class Builder {
        private String mOwner;
        private AdTechIdentifier mBuyer;
        private String mName;
        private Instant mExpirationTime;
        private Instant mActivationTime;
        private Instant mCreationTime;
        private Instant mLastAdsAndBiddingDataUpdatedTime;
        private AdSelectionSignals mUserBiddingSignals;
        private DBTrustedBiddingData mTrustedBiddingData;
        private Uri mBiddingLogicUri;
        private List<DBAdData> mAds;
        private boolean mDebuggable;
        @CustomAudience.AuctionServerRequestFlag private int mAuctionServerRequestFlags;
        private double mPriority;

        public Builder() {}

        public Builder(@NonNull DBCustomAudience customAudience) {
            Objects.requireNonNull(customAudience, "Custom audience must not be null.");

            mOwner = customAudience.getOwner();
            mBuyer = customAudience.getBuyer();
            mName = customAudience.getName();
            mExpirationTime = customAudience.getExpirationTime();
            mActivationTime = customAudience.getActivationTime();
            mCreationTime = customAudience.getCreationTime();
            mLastAdsAndBiddingDataUpdatedTime =
                    customAudience.getLastAdsAndBiddingDataUpdatedTime();
            mUserBiddingSignals = customAudience.getUserBiddingSignals();
            mTrustedBiddingData = customAudience.getTrustedBiddingData();
            mBiddingLogicUri = customAudience.getBiddingLogicUri();
            mAds = customAudience.getAds();
            mDebuggable = customAudience.isDebuggable();
            mAuctionServerRequestFlags = customAudience.getAuctionServerRequestFlags();
            mPriority = customAudience.getPriority();
        }

        /** See {@link #getOwner()} for detail. */
        public Builder setOwner(String owner) {
            mOwner = owner;
            return this;
        }

        /** See {@link #getBuyer()} for detail. */
        public Builder setBuyer(AdTechIdentifier buyer) {
            mBuyer = buyer;
            return this;
        }

        /** See {@link #getName()} for detail. */
        public Builder setName(String name) {
            mName = name;
            return this;
        }

        /** See {@link #getExpirationTime()} for detail. */
        public Builder setExpirationTime(Instant expirationTime) {
            mExpirationTime = expirationTime;
            return this;
        }

        /** See {@link #getActivationTime()} for detail. */
        public Builder setActivationTime(Instant activationTime) {
            mActivationTime = activationTime;
            return this;
        }

        /** See {@link #getCreationTime()} for detail. */
        public Builder setCreationTime(Instant creationTime) {
            mCreationTime = creationTime;
            return this;
        }

        /** See {@link #getLastAdsAndBiddingDataUpdatedTime()} for detail. */
        public Builder setLastAdsAndBiddingDataUpdatedTime(
                Instant lastAdsAndBiddingDataUpdatedTime) {
            mLastAdsAndBiddingDataUpdatedTime = lastAdsAndBiddingDataUpdatedTime;
            return this;
        }

        /** See {@link #getUserBiddingSignals()} for detail. */
        public Builder setUserBiddingSignals(AdSelectionSignals userBiddingSignals) {
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /** See {@link #getTrustedBiddingData()} for detail. */
        public Builder setTrustedBiddingData(DBTrustedBiddingData trustedBiddingData) {
            mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        /** See {@link #getBiddingLogicUri()} for detail. */
        public Builder setBiddingLogicUri(Uri biddingLogicUri) {
            mBiddingLogicUri = biddingLogicUri;
            return this;
        }

        /** See {@link #getAds()} for detail. */
        public Builder setAds(List<DBAdData> ads) {
            mAds = ads;
            return this;
        }

        /** See {@link #isDebuggable()} for detail. */
        public Builder setDebuggable(boolean debuggable) {
            mDebuggable = debuggable;
            return this;
        }

        /** Sets the bitfield of auction server request flags. */
        @NonNull
        public Builder setAuctionServerRequestFlags(
                @CustomAudience.AuctionServerRequestFlag int auctionServerRequestFlags) {
            mAuctionServerRequestFlags = auctionServerRequestFlags;
            return this;
        }

        /** See {@link #getPriority()} for detail. */
        public Builder setPriority(double priority) {
            mPriority = priority;
            return this;
        }

        /**
         * Build the {@link DBCustomAudience}.
         *
         * @return the built {@link DBCustomAudience}.
         */
        public DBCustomAudience build() {
            return new DBCustomAudience(
                    mOwner,
                    mBuyer,
                    mName,
                    mExpirationTime,
                    mActivationTime,
                    mCreationTime,
                    mLastAdsAndBiddingDataUpdatedTime,
                    mUserBiddingSignals,
                    mTrustedBiddingData,
                    mBiddingLogicUri,
                    mAds,
                    mDebuggable,
                    mAuctionServerRequestFlags,
                    mPriority);
        }
    }

    /**
     * Room DB type converters.
     *
     * <p>Register custom type converters here.
     *
     * <p>{@link TypeConverter} registered here only apply to data access with {@link
     * DBCustomAudience}
     */
    @ProvidedTypeConverter
    public static class Converters {

        private final AdDataConversionStrategy mAdDataConversionStrategy;

        public Converters(
                boolean frequencyCapFilteringEnabled,
                boolean appInstallFilteringEnabled,
                boolean adRenderIdEnabled) {
            mAdDataConversionStrategy =
                    AdDataConversionStrategyFactory.getAdDataConversionStrategy(
                            frequencyCapFilteringEnabled,
                            appInstallFilteringEnabled,
                            adRenderIdEnabled);
        }

        /** Serialize {@link List<DBAdData>} to Json. */
        @TypeConverter
        @Nullable
        public String toJson(@Nullable List<DBAdData> adDataList) {
            if (adDataList == null) {
                return null;
            }
            int toJsonTraceCookie = Tracing.beginAsyncSection(Tracing.DB_CUSTOM_AUDIENCE_TO_JSON);
            try {
                JSONArray jsonArray = new JSONArray();
                for (DBAdData adData : adDataList) {
                    jsonArray.put(mAdDataConversionStrategy.toJson(adData));
                }
                return jsonArray.toString();
            } catch (JSONException jsonException) {
                throw new RuntimeException("Error serialize List<AdData>.", jsonException);
            } finally {
                Tracing.endAsyncSection(Tracing.DB_CUSTOM_AUDIENCE_TO_JSON, toJsonTraceCookie);
            }
        }

        /** Deserialize {@link List<DBAdData>} from Json. */
        @TypeConverter
        @Nullable
        public List<DBAdData> fromJson(String json) {
            if (json == null) {
                return null;
            }

            int fromJsonTraceCookie =
                    Tracing.beginAsyncSection(Tracing.DB_CUSTOM_AUDIENCE_FROM_JSON);
            try {
                JSONArray array = new JSONArray(json);
                List<DBAdData> result = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = array.getJSONObject(i);
                    result.add(mAdDataConversionStrategy.fromJson(jsonObject).build());
                }
                return result;
            } catch (JSONException jsonException) {
                throw new RuntimeException("Error deserialize List<AdData>.", jsonException);
            } finally {
                Tracing.endAsyncSection(Tracing.DB_CUSTOM_AUDIENCE_FROM_JSON, fromJsonTraceCookie);
            }
        }
    }
}
