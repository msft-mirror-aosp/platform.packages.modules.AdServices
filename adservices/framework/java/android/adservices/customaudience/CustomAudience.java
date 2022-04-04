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
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.internal.util.Preconditions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Represents the information necessary for a custom audience to participate in ad selection.
 *
 * A custom audience is an abstract grouping of users with similar demonstrated interests.  This
 * class is a collection of some data stored on a device that is necessary to serve advertisements
 * targeting a single custom audience.
 *
 * Hiding for future implementation and review for public exposure.
 * @hide
 */
public final class CustomAudience implements Parcelable {
    // Default to 60-day expiry
    private static final long DEFAULT_EXPIRY_SECONDS = 60 * 60 * 24 * 60;
    private static final long MAX_FUTURE_ACTIVATION_TIME_SECONDS = 60 * 60 * 24 * 365;
    private static final long MAX_FUTURE_EXPIRATION_TIME_SECONDS = 60 * 60 * 24 * 365;

    @NonNull
    private final String mOwner;
    @NonNull
    private final String mBuyer;
    @NonNull
    private final String mName;
    @NonNull
    private final Instant mActivationTime;
    @NonNull
    private final Instant mExpirationTime;
    @NonNull
    private final Uri mDailyUpdateUrl;
    @NonNull
    private final String mUserBiddingSignals;
    @NonNull
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

    private CustomAudience(@NonNull String owner, @NonNull String buyer, @NonNull  String name,
            @NonNull Instant activationTime, @NonNull Instant expirationTime,
            @NonNull Uri dailyUpdateUrl, @NonNull String userBiddingSignals,
            @NonNull TrustedBiddingData trustedBiddingData, @NonNull Uri biddingLogicUrl,
            @NonNull List<AdData> ads) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(name);
        Objects.requireNonNull(activationTime);
        Objects.requireNonNull(expirationTime);
        Objects.requireNonNull(dailyUpdateUrl);
        Objects.requireNonNull(userBiddingSignals);
        Objects.requireNonNull(trustedBiddingData);
        Objects.requireNonNull(biddingLogicUrl);
        Objects.requireNonNull(ads);

        mOwner = owner;
        mBuyer = buyer;
        mName = name;
        mActivationTime = activationTime;
        mExpirationTime = expirationTime;
        mDailyUpdateUrl = dailyUpdateUrl;
        mUserBiddingSignals = userBiddingSignals;
        mTrustedBiddingData = trustedBiddingData;
        mBiddingLogicUrl = biddingLogicUrl;
        mAds = ads;
    }

    private CustomAudience(@NonNull Parcel in) {
        Objects.requireNonNull(in);

        mOwner = in.readString();
        mBuyer = in.readString();
        mName = in.readString();
        mActivationTime = Instant.ofEpochSecond(in.readLong());
        mExpirationTime = Instant.ofEpochSecond(in.readLong());
        mDailyUpdateUrl = Uri.CREATOR.createFromParcel(in);
        mUserBiddingSignals = in.readString();
        mTrustedBiddingData = TrustedBiddingData.CREATOR.createFromParcel(in);
        mBiddingLogicUrl = Uri.CREATOR.createFromParcel(in);
        mAds = in.createTypedArrayList(AdData.CREATOR);
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        dest.writeString(mOwner);
        dest.writeString(mBuyer);
        dest.writeString(mName);
        dest.writeLong(mActivationTime.getEpochSecond());
        dest.writeLong(mExpirationTime.getEpochSecond());
        mDailyUpdateUrl.writeToParcel(dest, flags);
        dest.writeString(mUserBiddingSignals);
        mTrustedBiddingData.writeToParcel(dest, flags);
        mBiddingLogicUrl.writeToParcel(dest, flags);
        dest.writeTypedList(mAds);
    }

    /** @hide */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * See {@link #getExpirationTime()} for more information about expiration times.
     *
     * @return the default amount of time (in seconds) a {@link CustomAudience} object will live
     * before being expiring and being removed
     */
    public static long getDefaultExpirationTimeSeconds() {
        return DEFAULT_EXPIRY_SECONDS;
    }

    /**
     * See {@link #getActivationTime()} for more information about activation times.
     *
     * @return the maximum permitted difference in seconds between the {@link CustomAudience}
     * object's creation time and its activation time
     */
    public static long getMaxFutureActivationTimeSeconds() {
        return MAX_FUTURE_ACTIVATION_TIME_SECONDS;
    }

    /**
     * See {@link #getExpirationTime()} for more information about expiration times.
     *
     * @return the maximum permitted difference in seconds between the {@link CustomAudience}
     * object's creation time and its expiration time
     */
    public static long getMaxFutureExpirationTimeSeconds() {
        return MAX_FUTURE_EXPIRATION_TIME_SECONDS;
    }

    /**
     * @return a String representing the custom audience's owner application
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
     * On creation of the {@link CustomAudience} object, the activation time may be set in the
     * future, in order to serve a delayed activation.  For example, a custom audience for lapsed
     * users may not activate until a threshold of inactivity is reached, at which point the custom
     * audience's ads will participate in the ad selection process, potentially redirecting lapsed
     * users to the original owner application.
     *
     * The maximum delay in activation is one year (365 days) from initial creation.
     *
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     * audience is active; restricted to one year (365 days) from initial creation
     */
    @NonNull
    public Instant getActivationTime() {
        return mActivationTime;
    }

    /**
     * Once the expiration time has passed, a custom audience is no longer eligible for daily
     * ad/bidding data updates or participation in the ad selection process.  The custom audience
     * will then be deleted from memory by the next daily update.
     *
     * If no expiration time is provided on creation of the {@link CustomAudience}, expiry will
     * default to 60 days.  The maximum expiry is one year (365 days) from initial creation.
     *
     * @return the custom audience's time, truncated to whole seconds, after which the custom
     * audience should be removed; this is restricted to one year (365 days) from initial creation
     * and defaults to 60 days
     */
    @NonNull
    public Instant getExpirationTime() {
        return mExpirationTime;
    }

    /**
     * This URL points to a buyer-operated server that hosts updated bidding data and ads metadata
     * to be used in the on-device ad selection process.
     *
     * @return the custom audience's daily update URL
     */
    @NonNull
    public Uri getDailyUpdateUrl() {
        return mDailyUpdateUrl;
    }

    /**
     * User bidding signals are represented as a JSON object, opaque to the custom audience and ad
     * selection APIs, that is provided as-is directly to the isolated execution environment during
     * the ad selection process.
     *
     * @return a JSON String representing the opaque user bidding signals for the custom audience
     */
    @NonNull
    public String getUserBiddingSignals() {
        return mUserBiddingSignals;
    }

    /**
     * Trusted bidding data consists of a URL pointing to a trusted server for buyers' bidding data
     * and a list of keys to query the server with.  Note that the keys are opaque to the custom
     * audience and ad selection APIs.
     *
     * @return a {@link TrustedBiddingData} object containing the custom audience's semi-opaque
     * trusted bidding data
     */
    @NonNull
    public TrustedBiddingData getTrustedBiddingData() {
        return mTrustedBiddingData;
    }

    /**
     * @return the target URL used to fetch bidding logic when a custom audience participates in the
     * ad selection process
     */
    @NonNull
    public Uri getBiddingLogicUrl() {
        return mBiddingLogicUrl;
    }

    /**
     * This list of {@link AdData} objects is a full and complete list of the ads served by this
     * {@link CustomAudience} during the ad selection process.
     *
     * @return a {@link List} of {@link AdData} objects representing ads currently served by the
     * custom audience
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
        return mOwner.equals(that.mOwner) && mBuyer.equals(that.mBuyer) && mName.equals(that.mName)
                && mActivationTime.equals(that.mActivationTime)
                && mExpirationTime.equals(that.mExpirationTime)
                && mDailyUpdateUrl.equals(that.mDailyUpdateUrl)
                && mUserBiddingSignals.equals(that.mUserBiddingSignals)
                && mTrustedBiddingData.equals(that.mTrustedBiddingData)
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
        @NonNull
        private String mOwner;
        @NonNull
        private String mBuyer;
        @NonNull
        private String mName;
        @NonNull
        private Instant mActivationTime;
        @NonNull
        private Instant mExpirationTime;
        @NonNull
        private Uri mDailyUpdateUrl;
        @NonNull
        private String mUserBiddingSignals;
        @NonNull
        private TrustedBiddingData mTrustedBiddingData;
        @NonNull
        private Uri mBiddingLogicUrl;
        @NonNull
        private List<AdData> mAds;

        public Builder() { }

        /**
         * Sets the owner application.
         */
        @NonNull
        public CustomAudience.Builder setOwner(@NonNull String owner) {
            Objects.requireNonNull(owner);
            mOwner = owner;
            return this;
        }

        /**
         * Sets the buyer domain URL.
         *
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
         *
         * See {@link #getName()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setName(@NonNull String name) {
            Objects.requireNonNull(name);
            mName = name;
            return this;
        }

        /**
         * Sets the time, truncated to seconds, after which the {@link CustomAudience} will serve
         * ads.
         *
         * See {@link #getActivationTime()} for more information.
         *
         * @throws IllegalArgumentException if the activation time is delayed by more than one year
         * (365 days)
         */
        @NonNull
        public CustomAudience.Builder setActivationTime(@NonNull Instant activationTime) {
            Objects.requireNonNull(activationTime);
            activationTime = activationTime.truncatedTo(ChronoUnit.SECONDS);
            Preconditions.checkArgument(
                    activationTime.isBefore(Instant.now().truncatedTo(ChronoUnit.SECONDS)
                            .plusSeconds(getMaxFutureActivationTimeSeconds())),
                    "Invalid activation time");
            mActivationTime = activationTime;
            return this;
        }

        /**
         * Sets the time, truncated to seconds, after which the {@link CustomAudience} should be
         * removed.
         *
         * See {@link #getExpirationTime()} for more information.
         *
         * @throws IllegalArgumentException if the expiration time is set before the current time or
         * if expiration time is more than one year (365 days) in the future
         */
        @NonNull
        public CustomAudience.Builder setExpirationTime(@NonNull Instant expirationTime) {
            Objects.requireNonNull(expirationTime);
            expirationTime = expirationTime.truncatedTo(ChronoUnit.SECONDS);
            Preconditions.checkArgument(expirationTime.isAfter(Instant.now())
                            && expirationTime.isBefore(Instant.now().truncatedTo(ChronoUnit.SECONDS)
                                    .plusSeconds(getMaxFutureExpirationTimeSeconds())),
                    "Invalid expiration time");
            mExpirationTime = expirationTime;
            return this;
        }

        /**
         * Sets the daily update URL.
         *
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
         *
         * See {@link #getUserBiddingSignals()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setUserBiddingSignals(@NonNull String userBiddingSignals) {
            Objects.requireNonNull(userBiddingSignals);
            mUserBiddingSignals = userBiddingSignals;
            return this;
        }

        /**
         * Sets the trusted bidding data to be queried and used in the ad selection process.
         *
         * See {@link #getTrustedBiddingData()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setTrustedBiddingData(
                @NonNull TrustedBiddingData trustedBiddingData) {
            Objects.requireNonNull(trustedBiddingData);
            mTrustedBiddingData = trustedBiddingData;
            return this;
        }

        /**
         * Sets the URL to fetch bidding logic from for use in the ad selection process.
         */
        @NonNull
        public CustomAudience.Builder setBiddingLogicUrl(@NonNull Uri biddingLogicUrl) {
            Objects.requireNonNull(biddingLogicUrl);
            mBiddingLogicUrl = biddingLogicUrl;
            return this;
        }

        /**
         * Sets the initial remarketing ads served by the custom audience.
         *
         * See {@link #getAds()} for more information.
         */
        @NonNull
        public CustomAudience.Builder setAds(@NonNull List<AdData> ads) {
            Objects.requireNonNull(ads);
            mAds = ads;
            return this;
        }

        /**
         * Builds an instance of a {@link CustomAudience}.
         *
         * @throws NullPointerException if any parameter is null
         * @throws IllegalArgumentException if the expiration time occurs before activation time
         */
        @NonNull
        public CustomAudience build() {
            if (mOwner == null) {
                // TODO(b/221861002): Implement default owner population
                mOwner = "not.implemented.yet";
            }
            Objects.requireNonNull(mBuyer);
            Objects.requireNonNull(mName);
            Objects.requireNonNull(mActivationTime);
            if (mExpirationTime == null) {
                mExpirationTime = Instant.now().plusSeconds(getDefaultExpirationTimeSeconds())
                        .truncatedTo(ChronoUnit.SECONDS);
            }
            Objects.requireNonNull(mDailyUpdateUrl);
            Objects.requireNonNull(mUserBiddingSignals);
            Objects.requireNonNull(mTrustedBiddingData);
            Objects.requireNonNull(mBiddingLogicUrl);
            Objects.requireNonNull(mAds);

            Preconditions.checkArgument(mActivationTime.isBefore(mExpirationTime),
                    "Invalid expiration time");

            return new CustomAudience(mOwner, mBuyer, mName, mActivationTime, mExpirationTime,
                    mDailyUpdateUrl, mUserBiddingSignals, mTrustedBiddingData, mBiddingLogicUrl,
                    mAds);
        }
    }
}
