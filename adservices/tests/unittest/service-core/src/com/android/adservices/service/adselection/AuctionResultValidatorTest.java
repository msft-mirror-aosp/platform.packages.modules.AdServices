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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.AuctionResultValidator.NEGATIVE_BID;
import static com.android.adservices.service.adselection.AuctionResultValidator.NEGATIVE_SCORE;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.common.ValidatorTestUtil;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;

import org.junit.Test;

import java.util.Collection;

public class AuctionResultValidatorTest {

    private static final AdTechIdentifier WINNER_BUYER = CommonFixture.VALID_BUYER_1;
    private static final DBAdData VALID_AD =
            DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(WINNER_BUYER).get(0);
    private static final Uri VALID_AD_RENDER_URI = VALID_AD.getRenderUri();
    private static final float VALID_BID = 20.0F;
    private static final float VALID_SCORE = 30.0F;
    private static final String CUSTOM_AUDIENCE_NAME = "test-name";

    private static AuctionResult.Builder getValidAuctionResultBuilder() {
        return AuctionResult.newBuilder()
                .setAdRenderUrl(VALID_AD_RENDER_URI.toString())
                .setCustomAudienceName(CUSTOM_AUDIENCE_NAME)
                .setCustomAudienceOwner(CommonFixture.VALID_BUYER_1.toString())
                .setIsChaff(false)
                .setScore(VALID_SCORE)
                .setBid(VALID_BID);
    }

    @Test
    public void testValidate_noError() {
        AuctionResultValidator validator = new AuctionResultValidator();
        validator.validate(getValidAuctionResultBuilder().build());
    }

    @Test
    public void testValidate_negativeBid() {
        AuctionResultValidator validator = new AuctionResultValidator();
        float negativeBid = -1.2F;
        Collection<String> violations =
                validator.getValidationViolations(
                        getValidAuctionResultBuilder().setBid(negativeBid).build());
        ValidatorTestUtil.assertViolationContainsOnly(
                violations, String.format(String.format(NEGATIVE_BID, negativeBid)));
    }

    @Test
    public void testValidate_negativeScore() {
        AuctionResultValidator validator = new AuctionResultValidator();
        float negativeScore = -1.2F;
        Collection<String> violations =
                validator.getValidationViolations(
                        getValidAuctionResultBuilder().setScore(negativeScore).build());
        ValidatorTestUtil.assertViolationContainsOnly(
                violations, String.format(String.format(NEGATIVE_SCORE, negativeScore)));
    }
}
