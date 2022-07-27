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

import android.adservices.customaudience.CustomAudience;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.customaudience.CustomAudienceUpdatableData;
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
 * POJO represents a Custom Audience Database Entity.
 * TODO: Align on the class naming strategy. (b/228095626)
 */
@Entity(
        tableName = DBCustomAudience.TABLE_NAME,
        primaryKeys = {"owner", "buyer", "name"}
)
@TypeConverters({DBCustomAudience.Converters.class})
public class DBCustomAudience {
    public static final String TABLE_NAME = "custom_audience";

    @ColumnInfo(name = "owner", index = true)
    @NonNull
    private final String mOwner;

    @ColumnInfo(name = "buyer", index = true)
    @NonNull
    private final String mBuyer;

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
    private final String mUserBiddingSignals;

    @Embedded(prefix = "trusted_bidding_data_")
    @Nullable
    private final DBTrustedBiddingData mTrustedBiddingData;

    @ColumnInfo(name = "bidding_logic_url")
    @NonNull
    private final Uri mBiddingLogicUrl;

    @ColumnInfo(name = "ads")
    @Nullable
    private final List<DBAdData> mAds;

    public DBCustomAudience(
            @NonNull String owner,
            @NonNull String buyer,
            @NonNull String name,
            @NonNull Instant expirationTime,
            @NonNull Instant activationTime,
            @NonNull Instant creationTime,
            @NonNull Instant lastAdsAndBiddingDataUpdatedTime,
            @Nullable String userBiddingSignals,
            @Nullable DBTrustedBiddingData trustedBiddingData,
            @NonNull Uri biddingLogicUrl,
            @Nullable List<DBAdData> ads) {
        Preconditions.checkStringNotEmpty(owner, "Owner must be provided");
        Preconditions.checkStringNotEmpty(buyer, "Buyer must be provided.");
        Preconditions.checkStringNotEmpty(name, "Name must be provided");
        Objects.requireNonNull(expirationTime, "Expiration time must be provided.");
        Objects.requireNonNull(creationTime, "Creation time must be provided.");
        Objects.requireNonNull(lastAdsAndBiddingDataUpdatedTime,
                "Last ads and bidding data updated time must be provided.");
        Objects.requireNonNull(biddingLogicUrl, "Bidding logic url must be provided.");

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mExpirationTime = expirationTime;
        mActivationTime = activationTime;
        mCreationTime = creationTime;
        mLastAdsAndBiddingDataUpdatedTime = lastAdsAndBiddingDataUpdatedTime;
        mUserBiddingSignals = userBiddingSignals;
        mTrustedBiddingData = trustedBiddingData;
        mBiddingLogicUrl = biddingLogicUrl;
        mAds = ads;
    }

    /**
     * Parse parcelable {@link CustomAudience} to storage model {@link DBCustomAudience}.
     *
     * @param parcelable the service model.
     * @param currentTime the timestamp when calling the method.
     * @param defaultExpireIn the default expiration from activation.
     * @return storage model
     */
    @NonNull
    public static DBCustomAudience fromServiceObject(
            @NonNull CustomAudience parcelable,
            @NonNull Instant currentTime,
            @NonNull Duration defaultExpireIn) {
        Objects.requireNonNull(parcelable);
        Objects.requireNonNull(currentTime);
        Objects.requireNonNull(defaultExpireIn);

        // Setting default value to be currentTime.
        // Make it easier at query for activated CAs.
        Instant activationTime = Optional.ofNullable(parcelable.getActivationTime()).orElse(
                currentTime);
        if (activationTime.isBefore(currentTime)) {
            activationTime = currentTime;
        }

        Instant expirationTime =
                Optional.ofNullable(parcelable.getExpirationTime())
                        .orElse(activationTime.plus(defaultExpireIn));

        Instant lastAdsAndBiddingDataUpdatedTime = parcelable.getAds().isEmpty()
                || parcelable.getTrustedBiddingData() == null
                || parcelable.getUserBiddingSignals() == null
                ? Instant.EPOCH : currentTime;

        return new DBCustomAudience.Builder()
                .setName(parcelable.getName())
                .setBuyer(parcelable.getBuyer())
                .setOwner(parcelable.getOwner())
                .setActivationTime(activationTime)
                .setCreationTime(currentTime)
                .setLastAdsAndBiddingDataUpdatedTime(lastAdsAndBiddingDataUpdatedTime)
                .setExpirationTime(expirationTime)
                .setBiddingLogicUrl(parcelable.getBiddingLogicUrl())
                .setTrustedBiddingData(
                        DBTrustedBiddingData.fromServiceObject(parcelable.getTrustedBiddingData()))
                .setAds(
                        parcelable.getAds().isEmpty()
                                ? null
                                : parcelable.getAds().stream()
                                        .map(DBAdData::fromServiceObject)
                                        .collect(Collectors.toList()))
                .setUserBiddingSignals(parcelable.getUserBiddingSignals())
                .build();
    }

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
            customAudienceBuilder.setUserBiddingSignals(
                    updatableData.getUserBiddingSignals().getStringForm());
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
     * information. This is expected to be the domain’s name used in biddingLogicUrl and
     * dailyUpdateUrl.
     * <p>Max length: 200 bytes
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }

    /**
     * Identifies the CustomAudience within the set of ones created for this combination of owner
     * and buyer.
     * <p>Max length: 200 bytes
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * Defines until when the CA end to be effective, this can be used to remove a user from this CA
     * only after a defined period of time.
     * <p>Default to be 60 days after activation(Pending product confirm).
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
     * <p>Should be within 1 year since creation.
     */
    @NonNull
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * Returns the time the CA was created.
     */
    @NonNull
    public Instant getCreationTime() {
        return mCreationTime;
    }

    /**
     * Returns the time the CA ads and bidding data was last updated.
     */
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
    public String getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    /**
     * An ad-tech can define what data needs to be fetched from a trusted server
     * (trusted_bidding_keys) and where it should be fetched from (trusted_bidding_url).
     */
    @Nullable
    public DBTrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /**
     * Returns the URL to fetch bidding logic js.
     */
    @NonNull
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * Returns Ads metadata that used to render an ad.
     */
    @Nullable
    public List<DBAdData> getAds() {
        return mAds;
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
                && mBiddingLogicUrl.equals(that.mBiddingLogicUrl)
                && Objects.equals(mAds, that.mAds);
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
                mBiddingLogicUrl,
                mAds);
    }

    @Override
    public String toString() {
        return "DBCustomAudience{"
                + "mOwner='" + mOwner + '\''
                + ", mBuyer='" + mBuyer + '\''
                + ", mName='" + mName + '\''
                + ", mExpirationTime=" + mExpirationTime
                + ", mActivationTime=" + mActivationTime
                + ", mCreationTime=" + mCreationTime
                + ", mLastAdsAndBiddingDataUpdatedTime=" + mLastAdsAndBiddingDataUpdatedTime
                + ", mUserBiddingSignals='" + mUserBiddingSignals + '\''
                + ", mTrustedBiddingData=" + mTrustedBiddingData
                + ", mBiddingLogicUrl=" + mBiddingLogicUrl
                + ", mAds='" + mAds + '\''
                + '}';
    }

    /**
     * Builder to construct a {@link DBCustomAudience}.
     */
    public static final class Builder {
        private String mOwner;
        private String mBuyer;
        private String mName;
        private Instant mExpirationTime;
        private Instant mActivationTime;
        private Instant mCreationTime;
        private Instant mLastAdsAndBiddingDataUpdatedTime;
        private String mUserBiddingSignals;
        private DBTrustedBiddingData mTrustedBiddingData;
        private Uri mBiddingLogicUrl;
        private List<DBAdData> mAds;

        public Builder() {
        }

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
            mBiddingLogicUrl = customAudience.getBiddingLogicUrl();
            mAds = customAudience.getAds();
        }

        /**
         * See {@link #getOwner()} for detail.
         */
        public Builder setOwner(String owner) {
            mOwner = owner;
            return this;
        }

        /**
         * See {@link #getBuyer()} for detail.
         */
        public Builder setBuyer(String buyer) {
            mBuyer = buyer;
            return this;
        }

        /**
         * See {@link #getName()} for detail.
         */
        public Builder setName(String name) {
            mName = name;
            return this;
        }

        /**
         * See {@link #getExpirationTime()} for detail.
         */
        public Builder setExpirationTime(Instant expirationTime) {
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * See {@link #getActivationTime()} for detail.
         */
        public Builder setActivationTime(Instant activationTime) {
            mActivationTime = activationTime;
            return this;
        }

        /**
         * See {@link #getCreationTime()} for detail.
         */
        public Builder setCreationTime(Instant creationTime) {
            mCreationTime = creationTime;
            return this;
        }

        /**
         * See {@link #getLastAdsAndBiddingDataUpdatedTime()} for detail.
         */
        public Builder setLastAdsAndBiddingDataUpdatedTime(
                Instant lastAdsAndBiddingDataUpdatedTime) {
            mLastAdsAndBiddingDataUpdatedTime = lastAdsAndBiddingDataUpdatedTime;
            return this;
        }

        /**
         * See {@link #getUserBiddingSignals()} for detail.
         */
        public Builder setUserBiddingSignals(String userBiddingSignals) {
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * See {@link #getTrustedBiddingData()} for detail.
         */
        public Builder setTrustedBiddingData(DBTrustedBiddingData trustedBiddingData) {
            mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        /** See {@link #getBiddingLogicUrl()} for detail. */
        public Builder setBiddingLogicUrl(Uri biddingLogicUrl) {
            mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /**
         * See {@link #getAds()} for detail.
         */
        public Builder setAds(List<DBAdData> ads) {
            mAds = ads;
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
                    mBiddingLogicUrl,
                    mAds);
        }
    }

    /**
     * Room DB type converters.
     * <p>Register custom type converters here.
     * <p>{@link TypeConverter} registered here only apply to data access with {@link
     * DBCustomAudience}
     */
    public static class Converters {

        private static final String RENDER_URL_FIELD_NAME = "renderUrl";
        private static final String METADATA_FIELD_NAME = "metadata";

        private Converters() {
        }

        /**
         * Serialize {@link List<DBAdData>} to Json.
         */
        @TypeConverter
        @Nullable
        public static String toJson(@Nullable List<DBAdData> adDataList) {
            if (adDataList == null) {
                return null;
            }

            try {
                JSONArray jsonArray = new JSONArray();
                for (DBAdData adData : adDataList) {
                    jsonArray.put(toJson(adData));
                }
                return jsonArray.toString();
            } catch (JSONException jsonException) {
                throw new RuntimeException("Error serialize List<AdData>.", jsonException);
            }
        }

        /**
         * Deserialize {@link List<DBAdData>} from Json.
         */
        @TypeConverter
        @Nullable
        public static List<DBAdData> fromJson(String json) {
            if (json == null) {
                return null;
            }

            try {
                JSONArray array = new JSONArray(json);
                List<DBAdData> result = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject jsonObject = array.getJSONObject(i);
                    result.add(fromJson(jsonObject));
                }
                return result;
            } catch (JSONException jsonException) {
                throw new RuntimeException("Error deserialize List<AdData>.", jsonException);
            }
        }

        /**
         * Serialize {@link DBAdData} to {@link JSONObject}.
         */
        private static JSONObject toJson(DBAdData adData) throws JSONException {
            return new org.json.JSONObject()
                    .put(RENDER_URL_FIELD_NAME, serializeUrl(adData.getRenderUri()))
                    .put(METADATA_FIELD_NAME, adData.getMetadata());
        }

        /**
         * Deserialize {@link DBAdData} from {@link JSONObject}.
         */
        private static DBAdData fromJson(JSONObject json) throws JSONException {
            String renderUrlString = json.getString(RENDER_URL_FIELD_NAME);
            String metadata = json.getString(METADATA_FIELD_NAME);
            Uri renderUrl = deserializeUrl(renderUrlString);
            return new DBAdData(renderUrl, metadata);
        }

        /**
         * Deserialize {@link Uri} from String.
         */
        @Nullable
        private static Uri deserializeUrl(@Nullable String uri) {
            return Optional.ofNullable(uri)
                    .map(Uri::parse)
                    .orElse(null);
        }

        /**
         * Serialize {@link Uri} to String.
         */
        @Nullable
        private static String serializeUrl(@Nullable Uri uri) {
            return Optional.ofNullable(uri)
                    .map(Uri::toString)
                    .orElse(null);
        }
    }
}
