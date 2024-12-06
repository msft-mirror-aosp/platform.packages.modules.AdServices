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

package com.android.adservices.service.shell;

import static android.adservices.customaudience.CustomAudienceFixture.CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.ConsentedDebugConfigurationDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.BuyerInputGenerator;
import com.android.adservices.service.adselection.debug.ConsentedDebugConfigurationGenerator;
import com.android.adservices.service.customaudience.BackgroundFetchRunner;
import com.android.adservices.service.devapi.DevSessionController;
import com.android.adservices.service.devapi.DevSessionDataStore;
import com.android.adservices.service.shell.adselection.AdSelectionShellCommandFactory;
import com.android.adservices.service.shell.adservicesapi.AdServicesApiShellCommandFactory;
import com.android.adservices.service.shell.customaudience.CustomAudienceShellCommandFactory;
import com.android.adservices.service.shell.signals.SignalsShellCommandFactory;
import com.android.adservices.service.signals.PeriodicEncodingJobRunner;
import com.android.adservices.service.signals.SignalsProviderAndArgumentFactory;
import com.android.adservices.service.stats.pas.EncodingExecutionLogHelper;
import com.android.adservices.service.stats.pas.EncodingJobRunStatsLogger;

import com.google.common.collect.ImmutableList;

import java.time.Clock;
import java.util.Objects;

/**
 * This class allows to inject all dependencies required to create all Shell Command Factories. This
 * should be used for end-to-end test cases.
 */
public class TestShellCommandFactorySupplier extends ShellCommandFactorySupplier {

    private final boolean mIsCustomAudienceCliEnabled;
    private final boolean mIsConsentedDebugCliEnabled;
    private final boolean mIsSignalsCliEnabled;
    private final CustomAudienceDao mCustomAudienceDao;
    private final BackgroundFetchRunner mBackgroundFetchRunner;
    private final ConsentedDebugConfigurationDao mConsentedDebugConfigurationDao;
    private final SignalsProviderAndArgumentFactory mSignalsProviderAndArgumentFactory;
    private final BuyerInputGenerator mBuyerInputGenerator;
    private final AuctionServerDataCompressor mAuctionServerDataCompressor;
    private final PeriodicEncodingJobRunner mEncodingJobRunner;
    private final EncoderLogicHandler mEncoderLogicHandler;
    private final EncodingExecutionLogHelper mEncodingExecutionLogHelper;
    private final EncodingJobRunStatsLogger mEncodingJobRunStatsLogger;
    private final EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private final ConsentedDebugConfigurationGenerator mConsentedDebugConfigurationGenerator;
    private final AdSelectionEntryDao mAdSelectionEntryDao;
    private final DevSessionController mDevSessionController;
    private final DevSessionDataStore mDevSessionDataStore;

    TestShellCommandFactorySupplier(
            boolean isCustomAudienceCLiEnabled,
            boolean isConsentedDebugCliEnabled,
            boolean isSignalsCliEnabled,
            BackgroundFetchRunner backgroundFetchRunner,
            CustomAudienceDao customAudienceDao,
            ConsentedDebugConfigurationDao consentedDebugConfigurationDao,
            SignalsProviderAndArgumentFactory signalsProviderAndArgumentFactory,
            BuyerInputGenerator buyerInputGenerator,
            AuctionServerDataCompressor auctionServerDataCompressor,
            PeriodicEncodingJobRunner encodingJobRunner,
            EncoderLogicHandler encoderLogicHandler,
            EncodingExecutionLogHelper encodingExecutionLogHelper,
            EncodingJobRunStatsLogger encodingJobRunStatsLogger,
            EncoderLogicMetadataDao encoderLogicMetadataDao,
            ConsentedDebugConfigurationGenerator consentedDebugConfigurationGenerator,
            AdSelectionEntryDao adSelectionEntryDao,
            DevSessionController devSessionSetter,
            DevSessionDataStore devSessionDataStore) {
        mIsCustomAudienceCliEnabled = isCustomAudienceCLiEnabled;
        mIsConsentedDebugCliEnabled = isConsentedDebugCliEnabled;
        mIsSignalsCliEnabled = isSignalsCliEnabled;
        mCustomAudienceDao =
                Objects.requireNonNull(customAudienceDao, "CustomAudienceDao cannot be null");
        mBackgroundFetchRunner =
                Objects.requireNonNull(
                        backgroundFetchRunner, "BackgroundFetchRunner cannot be null");
        mConsentedDebugConfigurationDao =
                Objects.requireNonNull(
                        consentedDebugConfigurationDao,
                        "ConsentedDebugConfigurationDao cannot be null");
        mSignalsProviderAndArgumentFactory =
                Objects.requireNonNull(
                        signalsProviderAndArgumentFactory, "ProtectedSignalsDao cannot be null");
        mBuyerInputGenerator =
                Objects.requireNonNull(buyerInputGenerator, "BuyerInputGenerator cannot be null");
        mAuctionServerDataCompressor =
                Objects.requireNonNull(
                        auctionServerDataCompressor, "AuctionServerDataCompressor cannot be null");
        mEncodingJobRunner =
                Objects.requireNonNull(encodingJobRunner, "EncodingJobRunner cannot be null");
        mEncoderLogicHandler =
                Objects.requireNonNull(encoderLogicHandler, "EncoderLogicHandler cannot be null");
        mEncodingExecutionLogHelper =
                Objects.requireNonNull(
                        encodingExecutionLogHelper, "EncodingExecutionLogHelper cannot be null");
        mEncodingJobRunStatsLogger =
                Objects.requireNonNull(
                        encodingJobRunStatsLogger, "EncodingJobRunStatsLogger cannot be null");
        mEncoderLogicMetadataDao =
                Objects.requireNonNull(
                        encoderLogicMetadataDao, "EncoderLogicMetadataDao cannot be null");
        mConsentedDebugConfigurationGenerator =
                Objects.requireNonNull(
                        consentedDebugConfigurationGenerator,
                        "ConsentedDebugConfigurationGenerator cannot be null");
        mAdSelectionEntryDao =
                Objects.requireNonNull(adSelectionEntryDao, "AdSelectionEntryDao cannot be null");
        mDevSessionController =
                Objects.requireNonNull(devSessionSetter, "DevSessionController cannot be null");
        mDevSessionDataStore =
                Objects.requireNonNull(devSessionDataStore, "DevSessionDataStore cannot be null");
    }

    @Override
    public ImmutableList<ShellCommandFactory> getAllShellCommandFactories() {
        return ImmutableList.of(
                new CustomAudienceShellCommandFactory(
                        mIsCustomAudienceCliEnabled,
                        mBackgroundFetchRunner,
                        mCustomAudienceDao,
                        Clock.systemUTC(),
                        CUSTOM_AUDIENCE_ACTIVE_FETCH_WINDOW_MS),
                new AdSelectionShellCommandFactory(
                        mIsConsentedDebugCliEnabled,
                        true,
                        mConsentedDebugConfigurationDao,
                        mBuyerInputGenerator,
                        mAuctionServerDataCompressor,
                        mConsentedDebugConfigurationGenerator,
                        mAdSelectionEntryDao,
                        mDevSessionDataStore),
                new SignalsShellCommandFactory(
                        mIsSignalsCliEnabled,
                        mSignalsProviderAndArgumentFactory,
                        mEncodingJobRunner,
                        mEncoderLogicHandler,
                        mEncodingExecutionLogHelper,
                        mEncodingJobRunStatsLogger,
                        mEncoderLogicMetadataDao),
                new AdServicesApiShellCommandFactory(
                        mDevSessionController, /* developerModeFeatureEnabled= */ true));
    }
}
