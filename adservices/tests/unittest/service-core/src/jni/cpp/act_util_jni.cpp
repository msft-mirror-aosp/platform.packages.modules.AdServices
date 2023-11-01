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
#include "act_util_jni.h"
#include "jni_util.h"
#include <act/act_v0/act_v0.h>
#include <act/act_v0/parameters.h>
#include <act/util.pb.h>
#include <vector>
#include <google/protobuf/message_lite.h>

using namespace private_join_and_compute::anonymous_counting_tokens;


const char* IllegalArgumentExceptionClass = "java/lang/IllegalArgumentException";
const char* IllegalStateExceptionClass = "java/lang/IllegalStateException";



JNIEXPORT jboolean JNICALL Java_com_android_adservices_ActJniUtility_checkClientParameters(
    JNIEnv *env,
    jclass,
    jbyteArray scheme_parameter_bytes,
    jbyteArray client_public_parameters_bytes,
    jbyteArray server_public_parameters_bytes,
    jbyteArray server_private_parameters_bytes) {

    SchemeParameters scheme_parameter_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &scheme_parameter_proto, scheme_parameter_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env, IllegalArgumentExceptionClass, "Error while parsing SchemeParameters Proto");
    }

    ClientPublicParameters client_public_parameters_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &client_public_parameters_proto,
                                                                client_public_parameters_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
            env, IllegalArgumentExceptionClass, "Error parsing ClientPublicParameters Proto");
    }
    ServerPublicParameters server_public_parameters_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &server_public_parameters_proto,
                                                                server_public_parameters_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env,
                IllegalArgumentExceptionClass,
                "Error while parsing ServerPublicParameters Proto");
    }

    ServerPrivateParameters server_private_parameters_proto;
    if(!jni_util::JniUtil::BytesToCppProto(env, &server_private_parameters_proto,
                                                                server_private_parameters_bytes)) {
        jni_util::JniUtil::ThrowJavaException(
                env,
                IllegalArgumentExceptionClass,
                "Error while parsing ServerPrivateParameters Proto");
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();
    auto status = (act -> CheckClientParameters(
                                              scheme_parameter_proto,
                                              client_public_parameters_proto,
                                              server_public_parameters_proto,
                                              server_private_parameters_proto));
    if (status.ok()) {
        return true;
    } else {
        jni_util::JniUtil::ThrowJavaException(env, IllegalStateExceptionClass,
                                                                        status.ToString().c_str());
        return false;
    }
}
