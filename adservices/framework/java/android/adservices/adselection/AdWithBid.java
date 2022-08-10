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

import android.adservices.common.AdData;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Represents an ad and its corresponding bid value after the bid generation step in the ad
 * selection process.
 *
 * <p>The ads and their bids are fed into an ad scoring process which will inform the final ad
 * selection.
 *
 * @hide
 */
public final class AdWithBid implements Parcelable {
    @NonNull
    private final AdData mAdData;
    // TODO(b/224873874): Define units used in bid generation
    private final double mBid;

    @NonNull
    public static final Creator<AdWithBid> CREATOR =
            new Creator<AdWithBid>() {
                @Override
                public AdWithBid createFromParcel(@NonNull Parcel in) {
                    Objects.requireNonNull(in);

                    return new AdWithBid(in);
                }

                @Override
                public AdWithBid[] newArray(int size) {
                    return new AdWithBid[size];
                }
            };

    /**
     * TODO(b/224873874): Define units used in bid generation
     * @param adData An {@link AdData} object defining an ad's render URL and buyer metadata
     * @param bid The amount of money a buyer has bid to show an ad; note that while the bid is
     *            expected to be non-negative, this is only enforced during the ad selection process
     *
     * @throws NullPointerException if adData is null
     */
    public AdWithBid(@NonNull AdData adData, double bid) {
        Objects.requireNonNull(adData);
        mAdData = adData;
        mBid = bid;
    }

    private AdWithBid(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mAdData = AdData.CREATOR.createFromParcel(in);
        mBid = in.readDouble();
    }

    /**
     * @return the ad that was bid on
     */
    @NonNull
    public AdData getAdData() {
        return mAdData;
    }

    /**
     * TODO(b/224873874): Define units used in bid generation
     * The bid is the amount of money an advertiser has bid during the ad selection process to show
     * an ad.  The bid could be any non-negative {@code double}, such as 0.00, 0.17, 1.10, or
     * 1000.00.
     *
     * @return the bid value to be passed to the scoring function when scoring the ad returned by
     * {@link #getAdData()}
     */
    public double getBid() {
        return mBid;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mAdData.writeToParcel(dest, flags);
        dest.writeDouble(mBid);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdWithBid)) return false;
        AdWithBid adWithBid = (AdWithBid) o;
        return Double.compare(adWithBid.mBid, mBid) == 0
                && Objects.equals(mAdData, adWithBid.mAdData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mAdData, mBid);
    }
}
