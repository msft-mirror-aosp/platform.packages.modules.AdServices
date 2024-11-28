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

package android.adservices.debuggablects;

import static com.android.adservices.service.CommonDebugFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.DebugFlagsConstants.KEY_DEVELOPER_SESSION_FEATURE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.utils.CustomAudienceTestFixture;
import android.adservices.utils.DevContextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.SupportedByConditionRule;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@EnableDebugFlag(KEY_DEVELOPER_SESSION_FEATURE_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@RequiresSdkLevelAtLeastS(reason = "Ad Selection is enabled for S+")
public class GetAdSelectionDataShellCommandCtsTest extends FledgeDebuggableScenarioTest {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final String SHELL_COMMAND_PREFIX = "ad-selection get-ad-selection-data";
    private static final String OUTPUT_PROTO_FIELD = "output_proto";

    @Rule(order = 11)
    public final SupportedByConditionRule devOptionsEnabled =
            DevContextUtils.createDevOptionsAvailableRule(mContext, LOGCAT_TAG_FLEDGE);

    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();

    private CustomAudienceTestFixture mCustomAudienceTestFixture;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        startDevSession();
        mCustomAudienceTestFixture = new CustomAudienceTestFixture(mCustomAudienceClient);
        flags.setPpapiAppAllowList(FlagsConstants.ALLOWLIST_ALL);
    }

    @After
    public void tearDown() throws Exception {
        mCustomAudienceTestFixture.leaveJoinedCustomAudiences();
        endDevSession();
    }

    @Test
    public void testRunBuyer_withCustomAudiencesAndSignals_happyPath() throws Exception {
        mCustomAudienceTestFixture.joinCustomAudience(
                mCustomAudienceTestFixture.createCustomAudienceWithAdRenderId(
                        SHOES_CA,
                        BUYER,
                        List.of(1D),
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME));

        String rawOutput =
                mAdServicesShellCommandHelper.runCommand(
                        "%s --buyer %s", SHELL_COMMAND_PREFIX, BUYER);

        expect.withMessage("output command valid").that(isValidCommandOutput(rawOutput)).isTrue();
    }

    @Test
    public void testRunSeller_withCustomAudiencesAndSignals_happyPath() throws Exception {
        // Custom audience must have an ad render ID to be present in the output.
        mCustomAudienceTestFixture.joinCustomAudience(
                mCustomAudienceTestFixture.createCustomAudienceWithAdRenderId(
                        SHOES_CA,
                        BUYER,
                        List.of(1D),
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME));

        String rawOutput = mAdServicesShellCommandHelper.runCommand("%s", SHELL_COMMAND_PREFIX);

        expect.withMessage("output command valid").that(isValidCommandOutput(rawOutput)).isTrue();
    }

    private static boolean isValidCommandOutput(String rawOutput) throws JSONException {
        return !rawOutput.isEmpty()
                && isValidProtobuf(
                        Base64.decode(
                                new JSONObject(rawOutput).getString(OUTPUT_PROTO_FIELD),
                                Base64.DEFAULT));
    }

    private static boolean isValidProtobuf(byte[] data) {
        // Check that the bytes is a valid proto wire message. For the full validation the unit
        // tests should cover this, as the proto descriptors are not available in the CTS package.
        try {
            DescriptorProtos.FileDescriptorSet.parseFrom(data);
            return true;
        } catch (InvalidProtocolBufferException e) {
            return false;
        }
    }

    private void startDevSession() {
        setDevSessionState(true);
    }

    private void endDevSession() {
        setDevSessionState(false);
    }

    private void setDevSessionState(boolean state) {
        Log.v(LOGCAT_TAG_FLEDGE, String.format("Starting setDevSession(%b)", state));
        mAdServicesShellCommandHelper.runCommand(
                "adservices-api dev-session %s --erase-db", state ? "start" : "end");
        Log.v(LOGCAT_TAG_FLEDGE, String.format("Completed setDevSession(%b)", state));
    }
}
