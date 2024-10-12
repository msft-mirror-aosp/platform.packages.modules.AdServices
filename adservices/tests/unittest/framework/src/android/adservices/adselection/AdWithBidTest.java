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

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;
import android.os.Parcel;

import com.android.adservices.common.AdServicesUnitTestCase;

import org.junit.Test;

/** CTS tests for {@link AdWithBid} */
public final class AdWithBidTest extends AdServicesUnitTestCase {
    private static final AdData VALID_AD_DATA =
            AdDataFixture.getValidAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);
    private static final double TEST_BID = 1.0;

    @Test
    public void testBuildValidAdWithBidSuccess() {
        AdWithBid validAdWithBid = new AdWithBid(VALID_AD_DATA, TEST_BID);

        expect.that(validAdWithBid.getAdData()).isEqualTo(VALID_AD_DATA);
        expect.that(validAdWithBid.getBid()).isEqualTo(TEST_BID);
    }

    @Test
    public void testParcelValidAdWithBidSuccess() {
        AdWithBid validAdWithBid = new AdWithBid(VALID_AD_DATA, TEST_BID);

        Parcel p = Parcel.obtain();
        validAdWithBid.writeToParcel(p, 0);
        p.setDataPosition(0);
        AdWithBid fromParcel = AdWithBid.CREATOR.createFromParcel(p);

        expect.that(fromParcel.getAdData()).isEqualTo(VALID_AD_DATA);
        expect.that(fromParcel.getBid()).isEqualTo(TEST_BID);
    }

    @Test
    public void testBuildNullAdDataAdWithBidFails() {
        assertThrows(NullPointerException.class, () -> new AdWithBid(null, TEST_BID));
    }

    @Test
    public void testAdWithBidDescribeContents() {
        AdWithBid obj = new AdWithBid(VALID_AD_DATA, TEST_BID);

        expect.that(obj.describeContents()).isEqualTo(0);
    }
}
