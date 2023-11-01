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

import private_join_and_compute.anonymous_counting_tokens.ClientPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPrivateParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;

public class ActJniUtility {

    static {
        System.loadLibrary("acttest_jni");
    }

    private static native boolean checkClientParameters(
            byte[] schemeParameters,
            byte[] clientPublicParameters,
            byte[] serverPublicParameters,
            byte[] serverPrivateParameters);

    /** A helper method that validates the ClientParameters. */
    public static boolean checkClientParameters(
            SchemeParameters schemeParametersProto,
            ClientPublicParameters clientPublicParametersProto,
            ServerPublicParameters serverPublicParametersProto,
            ServerPrivateParameters serverPrivateParametersProto) {
        byte[] schemeParametersInBytes = schemeParametersProto.toByteArray();
        byte[] clientPublicParametersInBytes = clientPublicParametersProto.toByteArray();
        byte[] serverPublicParametersInBytes = serverPublicParametersProto.toByteArray();
        byte[] serverPrivateParametersInBytes = serverPrivateParametersProto.toByteArray();

        return checkClientParameters(
                schemeParametersInBytes,
                clientPublicParametersInBytes,
                serverPublicParametersInBytes,
                serverPrivateParametersInBytes);
    }
}
