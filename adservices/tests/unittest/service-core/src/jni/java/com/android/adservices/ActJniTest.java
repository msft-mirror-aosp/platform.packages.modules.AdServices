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

package com.android.adservices;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.Transcript;

public class ActJniTest {

    private SchemeParameters mSchemeParameters;
    private ServerPublicParameters mServerPublicParameters;
    private static final String GOLDEN_TRANSCRIPT_PATH = "act/golden_transcript_1";

    @Before
    public void setup() throws IOException {
        Context sContext = ApplicationProvider.getApplicationContext();
        InputStream inputStream = sContext.getAssets().open(GOLDEN_TRANSCRIPT_PATH);
        Transcript transcript = Transcript.parseDelimitedFrom(inputStream);

        mSchemeParameters = transcript.getSchemeParameters();
        mServerPublicParameters = transcript.getServerParameters().getPublicParameters();
    }

    @Test
    public void testGenerateClientParams_generatesDifferentClientParams() throws IOException {
        ClientParameters clientParameters =
                ActJni.generateClientParameters(mSchemeParameters, mServerPublicParameters);
        ClientParameters otherClientParameters =
                ActJni.generateClientParameters(mSchemeParameters, mServerPublicParameters);

        Assert.assertNotEquals(
                clientParameters
                        .getPublicParameters()
                        .getClientPublicParametersV0()
                        .getDyVrfPublicKey()
                        .getCommitPrfKey(),
                otherClientParameters
                        .getPublicParameters()
                        .getClientPublicParametersV0()
                        .getDyVrfPublicKey()
                        .getCommitPrfKey());
    }

    @Test
    public void testGenerateClientParams_withInvalidParameters_throwIllegalArgumentException() {
        SchemeParameters invalidSchemeParameters = SchemeParameters.newBuilder().build();
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        ActJni.generateClientParameters(
                                invalidSchemeParameters, mServerPublicParameters));
    }
}
