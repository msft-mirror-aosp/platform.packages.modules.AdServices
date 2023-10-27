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

import com.google.protobuf.InvalidProtocolBufferException;

import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.SchemeParameters;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;

/** Contains JNI wrappers for the ACT(Anonymous counting tokens). */
public class ActJni {

    static {
        System.loadLibrary("hpke_jni");
    }

    private static native byte[] generateClientParameters(
            byte[] schemeParameters, byte[] serverPublicParameters);

    /**
     * Returns a fresh set of Client parameters corresponding to these SchemeParameters and
     * ServerPublicParameters.
     */
    public static ClientParameters generateClientParameters(
            SchemeParameters schemeParametersProto,
            ServerPublicParameters serverPublicParametersProto)
            throws InvalidProtocolBufferException {
        byte[] schemeParametersInBytes = schemeParametersProto.toByteArray();
        byte[] serverPublicParametersInBytes = serverPublicParametersProto.toByteArray();
        byte[] clientParametersInBytes =
                generateClientParameters(schemeParametersInBytes, serverPublicParametersInBytes);
        return ClientParameters.parseFrom(clientParametersInBytes);
    }
}