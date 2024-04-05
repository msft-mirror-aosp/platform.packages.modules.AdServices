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

package com.android.adservices.service.stats;

public class SignatureVerificationLoggerNoOp implements SignatureVerificationLogger {

    @Override
    public void startSignatureVerification() {}

    @Override
    public void endSignatureVerification() {}

    @Override
    public void startKeyFetchForSignatureVerification() {}

    @Override
    public void endKeyFetchForSignatureVerification() {}

    @Override
    public void startSerializationForSignatureVerification() {}

    @Override
    public void endSerializationForSignatureVerification() {}

    @Override
    public void setNumOfKeysFetched(int numOfKeysFetched) {}

    @Override
    public void setFailedSignatureBuyerEnrollmentId(String enrollmentId) {}

    @Override
    public void setFailedSignatureSellerEnrollmentId(String enrollmentId) {}

    @Override
    public void setFailedSignatureCallerPackageName(String callerPackageName) {}

    @Override
    public void setFailureDetailUnknownError() {}

    @Override
    public void setFailureDetailNoEnrollmentDataForBuyer() {}

    @Override
    public void setFailureDetailNoKeysFetchedForBuyer() {}

    @Override
    public void setFailureDetailWrongSignatureFormat() {}

    @Override
    public void addFailureDetailCountOfKeysWithWrongFormat() {}

    @Override
    public void addFailureDetailCountOfKeysFailedToVerifySignature() {}

    @Override
    public void close(int signingVerificationStatus) {}
}
