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

package android.adservices.customaudience;

import android.adservices.common.AdData;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.Parcel;
import android.os.Parcelable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Represents the information necessary for a custom audience to participate in ad selection.
 *
 * <p>A custom audience is an abstract grouping of users with similar demonstrated interests. This
 * class is a collection of some data stored on a device that is necessary to serve advertisements
 * targeting a single custom audience.
 */
public final class CustomAudience implements Parcelable {

    @NonNull private final String mOwner;
    @NonNull
    private final String mBuyer;
    @NonNull
    private final String mName;
    @Nullable
    private final Instant mActivationTime;
    @Nullable
    private final Instant mExpirationTime;
    @NonNull
    private final Uri mDailyUpdateUrl;
    @Nullable
    private final String mUserBiddingSignals;
    @Nullable
    private final TrustedBiddingData mTrustedBiddingData;
    @NonNull
    private final Uri mBiddingLogicUrl;
    @NonNull
    private final List<AdData> mAds;

    @NonNull
    public static final Creator<CustomAudience> CREATOR = new Creator<CustomAudience>() {
        @Override
        public CustomAudience createFromParcel(@NonNull Parcel in) {
            Objects.requireNonNull(in);

            return new CustomAudience(in);
        }

        @Override
        public CustomAudience[] newArray(int size) {
            return new CustomAudience[size];
        }
    };

    private CustomAudience(@NonNull CustomAudience.Builder builder) {
        mOwner = builder.mOwner;
        mBuyer = builder.mBuyer;
        mName = builder.mName;
        mActivationTime = builder.mActivationTime;
        mExpirationTime = builder.mExpirationTime;
        mDailyUpdateUrl = builder.mDailyUpdateUrl;
        mUserBiddingSignals = builder.mUserBiddingSignals;
        mTrustedBiddingData = builder.mTrustedBiddingData;
        mBiddingLogicUrl = builder.mBiddingLogicUrl;
        mAds = builder.mAds;
    }

    private CustomAudience(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mOwner = in.readString();
        mBuyer = in.readString();
        mName = in.readString();
        mActivationTime = in.readBoolean() ? Instant.ofEpochMilli(in.readLong()) : null;
        mExpirationTime = in.readBoolean() ? Instant.ofEpochMilli(in.readLong()) : null;
        mDailyUpdateUrl = Uri.CREATOR.createFromParcel(in);
        mUserBiddingSignals = in.readBoolean() ? in.readString() : null;
        mTrustedBiddingData = in.readBoolean()
                ? TrustedBiddingData.CREATOR.createFromParcel(in) : null;
        mBiddingLogicUrl = Uri.CREATOR.createFromParcel(in);
        mAds = in.createTypedArrayList(AdData.CREATOR);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeString(mOwner);
        dest.writeString(mBuyer);
        dest.writeString(mName);
        writeNullable(dest, mActivationTime,
                () -> dest.writeLong(mActivationTime.toEpochMilli()));
        writeNullable(dest, mExpirationTime,
                () -> dest.writeLong(mExpirationTime.toEpochMilli()));
        mDailyUpdateUrl.writeToParcel(dest, flags);
        writeNullable(dest, mUserBiddingSignals, () -> dest.writeString(mUserBiddingSignals));
        writeNullable(dest, mTrustedBiddingData,
                () -> mTrustedBiddingData.writeToParcel(dest, flags));
        mBiddingLogicUrl.writeToParcel(dest, flags);
        dest.writeTypedList(mAds);
    }

    private static void writeNullable(Parcel parcel, Object o, Runnable howToWrite) {
        boolean isFieldPresents = o != null;
        parcel.writeBoolean(isFieldPresents);
        if (isFieldPresents) {
            howToWrite.run();
        }
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Returns a String representing the custom audience's owner application package name.
     *
     * <p>The value of this field should be the package name of the calling app. Supplying another
     * app's package name will result in failure when calling {@link
     * CustomAudienceManager#joinCustomAudience(JoinCustomAudienceRequest, Executor,
     * OutcomeReceiver)}.
     */
    @NonNull
    public String getOwner() {
        return mOwner;
    }

    /**
     * A buyer is identified by a domain in the form "buyerexample.com".
     *
     * @return a String containing the custom audience's buyer's domain
     */
    @NonNull
    public String getBuyer() {
        return mBuyer;
    }

    /**
     * This name of a custom audience is an opaque string provided by the owner and buyer on
     * creation of the {@link CustomAudience} object.
     *
     * @return the String name of the custom audience
     */
    @NonNull
    public String getName() {
        return mName;
    }

    /**
     * On creation of the {@link CustomAudience} object, an optional activation time may be set in
     * the future, in order to serve a delayed activation. If the field is not set, the {@link
     * CustomAudience} will be activated at the time of joining.
     *
     * <p>For example, a custom audience for lapsed users may not activate until a threshold of
     * inactivity is reached, at which point the custom audience's ads will participate in the ad
     * selection process, potentially redirecting lapsed users to the original owner application.
     *
     * <p>The maximum delay in activation is 60 days from initial creation.
     *
     * <p>If specified, the activation time must be an earlier instant than the expiration time.
     *
     * @return the timestamp, truncated to milliseconds, after which the custom audience is active;
     */
    @Nullable
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * Once the expiration time has passed, a custom audience is no longer eligible for daily
     * ad/bidding data updates or participation in the ad selection process. The custom audience
     * will then be deleted from memory by the next daily update.
     *
     * <p>If no expiration time is provided on creation of the {@link CustomAudience}, expiry will
     * default to 60 days from activation.
     *
     * <p>The maximum expiry is 60 days from initial activation.
     *
     * @return the timestamp, truncated to milliseconds, after which the custom audience should be
     *     removed;
     */
    @Nullable
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * This URL points to a buyer-operated server that hosts updated bidding data and ads metadata
     * to be used in the on-device ad selection process. The URL must use HTTPS.
     *
     * @return the custom audience's daily update URL
     */
    @NonNull
    public Uri getDailyUpdateUrl() {
        return mDailyUpdateUrl;
    }

    /**
     * User bidding signals are optionally provided by buyers to be consumed by buyer-provided
     * JavaScript during ad selection in an isolated execution environment. These signals should be
     * represented as a valid JSON object serialized into a string.
     *
     * <p>If the user bidding signals are not a valid JSON object that can be consumed by the
     * buyer's JS, the custom audience will not be eligible for ad selection.
     *
     * <p>If not specified, the {@link CustomAudience} will not participate in ad selection until
     * user bidding signals are provided via the daily update for the custom audience.
     *
     * @return a JSON String representing the user bidding signals for the custom audience
     */
    @Nullable
    public String getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    /**
     * Trusted bidding data consists of a URL pointing to a trusted server for buyers' bidding data
     * and a list of keys to query the server with. Note that the keys are arbitrary identifiers
     * that will only be used to query the trusted server for a buyer's bidding logic during ad
     * selection.
     *
     * <p>If not specified, the {@link CustomAudience} will not participate in ad selection until
     * trusted bidding data are provided via the daily update for the custom audience.
     *
     * @return a {@link TrustedBiddingData} object containing the custom audience's trusted bidding
     *     data
     */
    @Nullable
    public TrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /**
     * Returns the target URL used to fetch bidding logic when a custom audience participates in the
     * ad selection process. The URL must use HTTPS.
     */
    @NonNull
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * This list of {@link AdData} objects is a full and complete list of the ads that will be
     * served by this {@link CustomAudience} during the ad selection process.
     *
     * <p>If not specified, or if an empty list is provided, the {@link CustomAudience} will not
     * participate in ad selection until a valid list of ads are provided via the daily update for
     * the custom audience.
     *
     * @return a {@link List} of {@link AdData} objects representing ads currently served by the
     *     custom audience
     */
    @NonNull
    public List<AdData> getAds() {
        return mAds;
    }

    /**
     * Checks whether two {@link CustomAudience} objects contain the same information.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomAudience)) return false;
        CustomAudience that = (CustomAudience) o;
        return mOwner.equals(that.mOwner)
                && mBuyer.equals(that.mBuyer)
                && mName.equals(that.mName)
                && Objects.equals(mActivationTime, that.mActivationTime)
                && Objects.equals(mExpirationTime, that.mExpirationTime)
                && mDailyUpdateUrl.equals(that.mDailyUpdateUrl)
                && Objects.equals(mUserBiddingSignals, that.mUserBiddingSignals)
                && Objects.equals(mTrustedBiddingData, that.mTrustedBiddingData)
                && mBiddingLogicUrl.equals(that.mBiddingLogicUrl)
                && mAds.equals(that.mAds);
    }

    /**
     * Returns the hash of the {@link CustomAudience} object's data.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mOwner, mBuyer, mName, mActivationTime, mExpirationTime,
                mDailyUpdateUrl, mUserBiddingSignals, mTrustedBiddingData, mBiddingLogicUrl, mAds);
    }

    /** Builder for {@link CustomAudience} objects. */
    public static final class Builder {
        @NonNull private String mOwner;
        @NonNull
        private String mBuyer;
        @NonNull
        private String mName;
        @Nullable
        private Instant mActivationTime;
        @Nullable
        private Instant mExpirationTime;
        @NonNull
        private Uri mDailyUpdateUrl;
        @Nullable
        private String mUserBiddingSignals;
        @Nullable
        private TrustedBiddingData mTrustedBiddingData;
        @NonNull
        private Uri mBiddingLogicUrl;
        @NonNull
        private List<AdData> mAds;

        // TODO(b/232883403): We may need to add @NonNUll members as args.
        public Builder() {
        }

        /**
         * Sets the owner application package name.
         *
         * <p>The value of this field should be the package name of the calling app. Supplying
         * another app's package name will result in failure when calling {@link
         * CustomAudienceManager#joinCustomAudience(JoinCustomAudienceRequest, Executor,
         * OutcomeReceiver)}.
         *
         * <p>See {@link #getOwner()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);
            mOwner = owner;
            return this;
        }

        /**
         * Sets the buyer domain URL.
         * <p>
         * See {@link #getBuyer()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setBuyer(@NonNull String buyer) {
            Objects.requireNonNull(buyer);
            mBuyer = buyer;
            return this;
        }

        /**
         * Sets the {@link CustomAudience} object's name.
         * <p>
         * See {@link #getName()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);
            mName = name;
            return this;
        }

        /**
         * Sets the time, truncated to milliseconds, after which the {@link CustomAudience} will
         * serve ads.
         *
         * <p>Set to {@code null} in order for this {@link CustomAudience} to be immediately active
         * and participate in ad selection.
         *
         * <p>See {@link #getActivationTime()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setActivationTime(@Nullable Instant activationTime) {
            mActivationTime = activationTime;
            return this;
        }

        /**
         * Sets the time, truncated to milliseconds, after which the {@link CustomAudience} should
         * be removed.
         * <p>
         * See {@link #getExpirationTime()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setExpirationTime(@Nullable Instant expirationTime) {
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * Sets the daily update URL. The URL must use HTTPS.
         * <p>
         * See {@link #getDailyUpdateUrl()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setDailyUpdateUrl(@NonNull Uri dailyUpdateUrl) {
            Objects.requireNonNull(dailyUpdateUrl);
            mDailyUpdateUrl = dailyUpdateUrl;
            return this;
        }

        /**
         * Sets the user bidding signals used in the ad selection process.
         * <p>
         * See {@link #getUserBiddingSignals()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setUserBiddingSignals(@Nullable String userBiddingSignals) {
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * Sets the trusted bidding data to be queried and used in the ad selection process.
         * <p>
         * See {@link #getTrustedBiddingData()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setTrustedBiddingData(
                @Nullable TrustedBiddingData trustedBiddingData) {
            mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        /**
         * Sets the URL to fetch bidding logic from for use in the ad selection process. The URL
         * must use HTTPS.
         *
         * <p>See {@link #getBiddingLogicUrl()} ()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setBiddingLogicUrl(@NonNull Uri biddingLogicUrl) {
            Objects.requireNonNull(biddingLogicUrl);
            mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /**
         * Sets the initial remarketing ads served by the custom audience. Will be assigned with an
         * empty list if not provided.
         *
         * <p>See {@link #getAds()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setAds(@Nullable List<AdData> ads) {
            mAds = ads;
            return this;
        }

        /**
         * Builds an instance of a {@link CustomAudience}.
         *
         * @throws NullPointerException     if any non-null parameter is null
         * @throws IllegalArgumentException if the expiration time occurs before activation time
         * @throws IllegalArgumentException if the expiration time is set before the current time
         */
        @NonNull
        public CustomAudience build() {
            Objects.requireNonNull(mOwner);
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mDailyUpdateUrl);
            Objects.requireNonNull(mBiddingLogicUrl);

            // To pass the API lint, we should not allow null Collection.
            if (mAds == null) {
                mAds = List.of();
            }

            return new CustomAudience(this);
        }
    }
}
