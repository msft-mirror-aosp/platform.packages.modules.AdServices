/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.os.Parcel;
import android.os.Parcelable;

import java.util.Objects;

/**
 * Decision logic provided by the buyer, to be used in the override This would help prevent
 * downloading the payload for reporting or updating bids from server
 *
 * @hide
 */
public final class BuyerDecisionLogic implements Parcelable {

    @NonNull private final AdTechIdentifier mBuyer;

    @NonNull private final String mDecisionLogic;

    public BuyerDecisionLogic(@NonNull AdTechIdentifier buyer, @NonNull String decisionLogic) {
        Objects.requireNonNull(buyer);
        Objects.requireNonNull(decisionLogic);
        mBuyer = buyer;
        mDecisionLogic = decisionLogic;
    }

    private BuyerDecisionLogic(@NonNull Parcel in) {
        Objects.requireNonNull(in);
        mBuyer = AdTechIdentifier.CREATOR.createFromParcel(in);
        mDecisionLogic = in.readString();
    }

    @NonNull
    public static final Creator<BuyerDecisionLogic> CREATOR =
            new Creator<BuyerDecisionLogic>() {
                @Override
                public BuyerDecisionLogic createFromParcel(Parcel in) {
                    Objects.requireNonNull(in);
                    return new BuyerDecisionLogic(in);
                }

                @Override
                public BuyerDecisionLogic[] newArray(int size) {
                    return new BuyerDecisionLogic[0];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        Objects.requireNonNull(dest);

        mBuyer.writeToParcel(dest, flags);
        dest.writeString(mDecisionLogic);
    }

    @Override
    public String toString() {
        return mBuyer + ":" + mDecisionLogic;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mBuyer, mDecisionLogic);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BuyerDecisionLogic)) return false;
        BuyerDecisionLogic buyerDecisionLogic = (BuyerDecisionLogic) o;
        return (mBuyer.equals(buyerDecisionLogic.getBuyer()))
                && mDecisionLogic.equals(buyerDecisionLogic.getDecisionLogic());
    }

    @NonNull
    public AdTechIdentifier getBuyer() {
        return mBuyer;
    }

    @NonNull
    public String getDecisionLogic() {
        return mDecisionLogic;
    }
}
