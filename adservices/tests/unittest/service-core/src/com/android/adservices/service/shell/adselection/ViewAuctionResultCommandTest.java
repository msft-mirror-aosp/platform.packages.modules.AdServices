/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.shell.adselection;

import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_AD_SELECTION_ID;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_BIDDING_LOGIC_URI;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_BUYER_CONTEXTUAL_SIGNALS;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_BUYER_DECISION_LOGIC_JS;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_BUYER_WIN_REPORTING_URI;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CREATION_TIMESTAMP;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_ACTIVATION_TIME;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_BUYER;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_EXPIRATION_TIME;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_NAME;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_OWNER;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_CUSTOM_AUDIENCE_SIGNALS_USER_BIDDING_SIGNALS;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_SELLER_CONTEXTUAL_SIGNALS;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_SELLER_WIN_REPORTING_URI;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_WINNING_AD_BID;
import static com.android.adservices.service.shell.adselection.AdSelectionEntryHelper.FIELD_WINNING_AD_RENDER_URI;
import static com.android.adservices.service.shell.adselection.AdSelectionShellCommandArgs.AD_SELECTION_ID;
import static com.android.adservices.service.shell.adselection.ViewAuctionResultCommand.CMD;
import static com.android.adservices.service.shell.adselection.ViewAuctionResultCommand.HELP;
import static com.android.adservices.service.shell.signals.SignalsShellCommandFactory.COMMAND_PREFIX;
import static com.android.adservices.service.stats.ShellCommandStats.COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBAdSelectionFixture;
import com.android.adservices.data.adselection.ReportingDataFixture;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.service.shell.ShellCommandTestCase;
import com.android.adservices.service.stats.ShellCommandStats;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.mockito.Mock;

public class ViewAuctionResultCommandTest extends ShellCommandTestCase<ViewAuctionResultCommand> {

    @ShellCommandStats.Command
    private static final int EXPECTED_COMMAND = COMMAND_AD_SELECTION_VIEW_AUCTION_RESULT;

    @Mock private AdSelectionEntryDao mAdSelectionEntryDao;

    private static final DBAdSelectionEntry AD_SELECTION_ENTRY =
            DBAdSelectionFixture.getValidDbAdSelectionEntryBuilder().build();

    @Test
    public void testRun_missingAdSelectionId_returnsHelp() {
        runAndExpectInvalidArgument(
                new ViewAuctionResultCommand(mAdSelectionEntryDao),
                HELP,
                EXPECTED_COMMAND,
                COMMAND_PREFIX,
                CMD);
    }

    @Test
    public void testRun_withUnknownAdSelectionId_throwsException() {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(false);

        runAndExpectInvalidArgument(
                new ViewAuctionResultCommand(mAdSelectionEntryDao),
                HELP,
                EXPECTED_COMMAND,
                COMMAND_PREFIX,
                CMD,
                AD_SELECTION_ID,
                Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));
    }

    @Test
    public void testRun_withValidAdSelectionId_returnsSuccess() throws JSONException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(ReportingDataFixture.REPORTING_DATA_WITHOUT_COMPUTATION_DATA);

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        JSONObject jsonObject = new JSONObject(result.mOut);
        assertThat(jsonObject.getString(FIELD_AD_SELECTION_ID))
                .isEqualTo(Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));
        assertThat(jsonObject.getString(FIELD_CREATION_TIMESTAMP))
                .isEqualTo(AD_SELECTION_ENTRY.getCreationTimestamp().toString());
        assertThat(jsonObject.getString(FIELD_WINNING_AD_BID))
                .isEqualTo(Double.toString(AD_SELECTION_ENTRY.getWinningAdBid()));
        assertThat(jsonObject.getString(FIELD_WINNING_AD_RENDER_URI))
                .isEqualTo(AD_SELECTION_ENTRY.getWinningAdRenderUri().toString());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_BUYER))
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getBuyer().toString());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_OWNER))
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getOwner());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_NAME))
                .isEqualTo(AD_SELECTION_ENTRY.getCustomAudienceSignals().getName());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_ACTIVATION_TIME))
                .isEqualTo(
                        AD_SELECTION_ENTRY
                                .getCustomAudienceSignals()
                                .getActivationTime()
                                .toString());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_EXPIRATION_TIME))
                .isEqualTo(
                        AD_SELECTION_ENTRY
                                .getCustomAudienceSignals()
                                .getExpirationTime()
                                .toString());
        assertThat(
                        jsonObject
                                .getJSONObject(FIELD_CUSTOM_AUDIENCE_SIGNALS)
                                .getString(FIELD_CUSTOM_AUDIENCE_SIGNALS_USER_BIDDING_SIGNALS))
                .isEqualTo(
                        AD_SELECTION_ENTRY
                                .getCustomAudienceSignals()
                                .getUserBiddingSignals()
                                .toString());
        assertThat(jsonObject.getString(FIELD_SELLER_CONTEXTUAL_SIGNALS))
                .isEqualTo(AD_SELECTION_ENTRY.getSellerContextualSignals());
        assertThat(jsonObject.getString(FIELD_BUYER_CONTEXTUAL_SIGNALS))
                .isEqualTo(AD_SELECTION_ENTRY.getBuyerContextualSignals());
        assertThat(jsonObject.getString(FIELD_BUYER_DECISION_LOGIC_JS))
                .isEqualTo(AD_SELECTION_ENTRY.getBuyerDecisionLogicJs());
        assertThat(jsonObject.getString(FIELD_BIDDING_LOGIC_URI))
                .isEqualTo(AD_SELECTION_ENTRY.getBiddingLogicUri().toString());
        assertThat(jsonObject.getString(FIELD_BUYER_WIN_REPORTING_URI))
                .isEqualTo(ReportingDataFixture.BUYER_REPORTING_URI_1.toString());
        assertThat(jsonObject.getString(FIELD_SELLER_WIN_REPORTING_URI))
                .isEqualTo(ReportingDataFixture.SELLER_REPORTING_URI_1.toString());
    }

    @Test
    public void testRun_withEmptyReportingUris_fieldNotPresent() throws JSONException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(null);

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        JSONObject jsonObject = new JSONObject(result.mOut);
        assertThat(jsonObject.getString(FIELD_SELLER_WIN_REPORTING_URI)).isEqualTo("none");
        assertThat(jsonObject.getString(FIELD_BUYER_WIN_REPORTING_URI)).isEqualTo("none");
    }

    @Test
    public void testRun_withoutSellerReportingUri_fieldNotPresent() throws JSONException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(
                        ReportingData.builder()
                                .setBuyerWinReportingUri(ReportingDataFixture.BUYER_REPORTING_URI_1)
                                .build());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        JSONObject jsonObject = new JSONObject(result.mOut);
        assertThat(jsonObject.getString(FIELD_SELLER_WIN_REPORTING_URI)).isEqualTo("none");
        assertThat(jsonObject.has(FIELD_BUYER_WIN_REPORTING_URI)).isTrue();
    }

    @Test
    public void testRun_withoutBuyerReportingUri_fieldNotPresent() throws JSONException {
        when(mAdSelectionEntryDao.doesAdSelectionIdExist(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(true);
        when(mAdSelectionEntryDao.getAdSelectionEntityById(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(AD_SELECTION_ENTRY);
        when(mAdSelectionEntryDao.getReportingUris(AD_SELECTION_ENTRY.getAdSelectionId()))
                .thenReturn(
                        ReportingData.builder()
                                .setSellerWinReportingUri(
                                        ReportingDataFixture.SELLER_REPORTING_URI_1)
                                .build());

        Result result =
                run(
                        new ViewAuctionResultCommand(mAdSelectionEntryDao),
                        COMMAND_PREFIX,
                        CMD,
                        AD_SELECTION_ID,
                        Long.toString(AD_SELECTION_ENTRY.getAdSelectionId()));

        expectSuccess(result, EXPECTED_COMMAND);
        JSONObject jsonObject = new JSONObject(result.mOut);
        assertThat(jsonObject.has(FIELD_SELLER_WIN_REPORTING_URI)).isTrue();
        assertThat(jsonObject.getString(FIELD_BUYER_WIN_REPORTING_URI)).isEqualTo("none");
    }
}
