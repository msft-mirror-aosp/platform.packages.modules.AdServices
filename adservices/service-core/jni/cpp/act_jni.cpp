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
#include "act_jni.h"
#include <act/act_v0/act_v0.h>
#include <act/act_v0/parameters.h>
#include <vector>
#include <google/protobuf/message_lite.h>

using namespace private_join_and_compute::anonymous_counting_tokens;


const char* IllegalArgumentExceptionClass = "java/lang/IllegalArgumentException";

bool BytesToCppProto(JNIEnv* env, google::protobuf::MessageLite* proto, jbyteArray input) {
    bool parsed_ok = false;
    const int size = env->GetArrayLength(input);
    void* ptr = env->GetPrimitiveArrayCritical(input, nullptr);
    if (ptr) {
        parsed_ok = proto->ParseFromArray(static_cast<char*>(ptr), size);
        env->ReleasePrimitiveArrayCritical(input, ptr, JNI_ABORT);
    }
    return parsed_ok;
}

jbyteArray SerializeProtoToJniByteArray(JNIEnv* env,
                                        const google::protobuf::MessageLite& protobuf) {
    const int size = protobuf.ByteSizeLong();
    jbyteArray ret = env->NewByteArray(size);
    if (ret == nullptr) {
        return nullptr;
    }

    uint8_t* scoped_array = static_cast<uint8_t* >(env->GetPrimitiveArrayCritical(ret, nullptr));
    protobuf.SerializeWithCachedSizesToArray(scoped_array);
    env->ReleasePrimitiveArrayCritical(ret, scoped_array, 0);
    return ret;
}

void ThrowJavaException(JNIEnv* env, const char* exception_class_name,
                        const char* message) {
    env->ThrowNew(env->FindClass(exception_class_name), message);
}

JNIEXPORT jbyteArray JNICALL Java_com_android_adservices_ActJni_generateClientParameters(
    JNIEnv *env,
    jclass,
    jbyteArray scheme_parameter_bytes,
    jbyteArray server_public_parameters_bytes
) {
    SchemeParameters scheme_parameter_proto;
    if(!BytesToCppProto(env, &scheme_parameter_proto, scheme_parameter_bytes)) {
        ThrowJavaException(
                env, IllegalArgumentExceptionClass, "Error while parsing SchemeParameters Proto");
        return nullptr;
    }
    ServerPublicParameters server_public_parameters_proto;
    if(!BytesToCppProto(env, &server_public_parameters_proto, server_public_parameters_bytes)) {
        ThrowJavaException(
                env,
                IllegalArgumentExceptionClass,
                "Error while parsing ServerPublicParameters Proto");
        return nullptr;
    }

    std::unique_ptr<AnonymousCountingTokens> act = AnonymousCountingTokensV0::Create();

    ClientParameters client_parameters;
    auto status_or
        = (act -> GenerateClientParameters(scheme_parameter_proto, server_public_parameters_proto));
    if(status_or.ok()) {
        client_parameters = std::move(status_or).value();
    } else {
        ThrowJavaException(
                    env, IllegalArgumentExceptionClass, status_or.status().ToString().c_str());
        return nullptr;
    }
    jbyteArray client_parameters_in_bytes = SerializeProtoToJniByteArray(env, client_parameters);
    return client_parameters_in_bytes;
}




