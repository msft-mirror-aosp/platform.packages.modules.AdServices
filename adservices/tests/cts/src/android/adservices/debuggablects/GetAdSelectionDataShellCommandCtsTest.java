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

import static com.android.adservices.service.CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_AD_SELECTION_CLI_ENABLED;
import static com.android.adservices.service.DebugFlagsConstants.KEY_CONSENT_NOTIFICATION_DEBUG_MODE;
import static com.android.adservices.service.FlagsConstants.KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_PROTECTED_SIGNALS_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.utils.CustomAudienceTestFixture;
import android.util.Base64;

import com.android.adservices.common.AdServicesShellCommandHelper;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.shared.testing.annotations.EnableDebugFlag;
import com.android.adservices.shared.testing.annotations.RequiresSdkLevelAtLeastS;
import com.android.adservices.shared.testing.annotations.SetFlagEnabled;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

@SetFlagEnabled(KEY_DISABLE_FLEDGE_ENROLLMENT_CHECK)
@EnableDebugFlag(KEY_ADSERVICES_SHELL_COMMAND_ENABLED)
@EnableDebugFlag(KEY_AD_SELECTION_CLI_ENABLED)
@SetFlagEnabled(KEY_FLEDGE_AUCTION_SERVER_AD_RENDER_ID_ENABLED)
@SetFlagEnabled(KEY_PROTECTED_SIGNALS_ENABLED)
@EnableDebugFlag(KEY_CONSENT_NOTIFICATION_DEBUG_MODE)
@RequiresSdkLevelAtLeastS(reason = "Ad Selection is enabled for S+")
public class GetAdSelectionDataShellCommandCtsTest extends ForegroundDebuggableCtsTest {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");

    private static final String SHELL_COMMAND_PREFIX = "ad-selection get-ad-selection-data";
    public static final String OUTPUT_PROTO_FIELD = "output_proto";

    private CustomAudienceTestFixture mCustomAudienceTestFixture;
    private final AdServicesShellCommandHelper mAdServicesShellCommandHelper =
            new AdServicesShellCommandHelper();

    @Before
    public void setUp() throws Exception {
        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        AdservicesTestHelper.killAdservicesProcess(sContext);

        mCustomAudienceTestFixture = new CustomAudienceTestFixture(sContext);
        flags.setPpapiAppAllowList(FlagsConstants.ALLOWLIST_ALL);
    }

    @After
    public void tearDown() throws Exception {
        mCustomAudienceTestFixture.leaveJoinedCustomAudiences();
    }

    @Test
    public void testRun_withCustomAudiencesAndSignals_happyPath()
            throws ExecutionException, InterruptedException, TimeoutException, JSONException {
        mCustomAudienceTestFixture.joinCustomAudience(
                mCustomAudienceTestFixture.createCustomAudienceWithAdRenderId(
                        "shoes",
                        BUYER,
                        List.of(1D),
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME));

        String rawOutput =
                mAdServicesShellCommandHelper.runCommand(
                        "%s --buyer %s", SHELL_COMMAND_PREFIX, BUYER);

        assertThat(rawOutput).isNotEmpty(); // Errors may go to stderr and not appear here.
        assertThat(
                        isValidProtobuf(
                                Base64.decode(
                                        new JSONObject(rawOutput).getString(OUTPUT_PROTO_FIELD),
                                        Base64.DEFAULT)))
                .isTrue();
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
}
