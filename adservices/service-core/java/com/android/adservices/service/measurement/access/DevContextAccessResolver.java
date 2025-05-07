/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service.measurement.access;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.measurement.RegistrationRequest;
import android.adservices.measurement.SourceRegistrationRequest;
import android.adservices.measurement.WebSourceParams;
import android.adservices.measurement.WebSourceRegistrationRequest;
import android.adservices.measurement.WebTriggerParams;
import android.adservices.measurement.WebTriggerRegistrationRequest;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.service.common.WebAddresses;
import com.android.adservices.service.devapi.DevContext;
import java.util.Optional;
import java.util.function.Supplier;

/** Resolves access related to development/testing context. */
public class DevContextAccessResolver implements IAccessResolver {
    private static final String ERROR_MESSAGE = "Developer options or dev session are not enabled.";
    private boolean mIsAllowed;

    public DevContextAccessResolver(
            @NonNull Supplier<DevContext> devContextSupplier,
            @NonNull RegistrationRequest registrationRequest) {
        Optional<DevContext> devContext = maybeGetDevContext(devContextSupplier);
        boolean hasLocalhost = WebAddresses.isLocalhost(registrationRequest.getRegistrationUri());
        setIsAllowed(hasLocalhost, devContext);
    }

    public DevContextAccessResolver(@NonNull Supplier<DevContext> devContextSupplier) {
        // For a dev context that is not required to check a specific calling
        // app, we assume the presence of the dev context is enough to allow access.
        // If the app is not allowed to use dev context, the dev context filter
        // would throw a SecurityException and reject the access.
        Optional<DevContext> devContext = maybeGetDevContext(devContextSupplier);
        mIsAllowed = devContext.isPresent();
    }

    public DevContextAccessResolver(
            @NonNull Supplier<DevContext> devContextSupplier,
            @NonNull WebSourceRegistrationRequest webRegistrationRequest) {
        Optional<DevContext> devContext = maybeGetDevContext(devContextSupplier);
        boolean hasLocalhost = false;
        for (WebSourceParams params : webRegistrationRequest.getSourceParams()) {
            if (WebAddresses.isLocalhost(params.getRegistrationUri())) {
                hasLocalhost = true;
                break;
            }
        }
        setIsAllowed(/* checkDevOptionsEnabled= */ hasLocalhost, devContext);
    }

    public DevContextAccessResolver(
            @NonNull Supplier<DevContext> devContextSupplier,
            @NonNull SourceRegistrationRequest registrationRequest) {
        Optional<DevContext> devContext = maybeGetDevContext(devContextSupplier);
        boolean hasLocalhost = false;
        for (Uri uri : registrationRequest.getRegistrationUris()) {
            if (WebAddresses.isLocalhost(uri)) {
                hasLocalhost = true;
                break;
            }
        }
        setIsAllowed(/* checkDevOptionsEnabled= */ hasLocalhost, devContext);
    }

    public DevContextAccessResolver(
            @NonNull Supplier<DevContext> devContextSupplier,
            @NonNull WebTriggerRegistrationRequest webRegistrationRequest) {
        Optional<DevContext> devContext = maybeGetDevContext(devContextSupplier);
        boolean hasLocalhost = false;
        for (WebTriggerParams params : webRegistrationRequest.getTriggerParams()) {
            if (WebAddresses.isLocalhost(params.getRegistrationUri())) {
                hasLocalhost = true;
                break;
            }
        }
        setIsAllowed(/* checkDevOptionsEnabled= */ hasLocalhost, devContext);
    }

    @Override
    public AccessInfo getAccessInfo(@NonNull Context context) {
        int statusCode =
                mIsAllowed
                        ? AdServicesStatusUtils.STATUS_SUCCESS
                        : AdServicesStatusUtils.STATUS_UNAUTHORIZED;
        return new AccessInfo(mIsAllowed, statusCode);
    }

    @NonNull
    @Override
    public String getErrorMessage() {
        return ERROR_MESSAGE;
    }

    private void setIsAllowed(boolean checkDevOptionsEnabled, Optional<DevContext> devContext) {
        mIsAllowed =
                devContext.isPresent()
                        && (checkDevOptionsEnabled
                                ? devContext.get().getDeviceDevOptionsEnabled()
                                : true);
    }

    private Optional<DevContext> maybeGetDevContext(
            @NonNull Supplier<DevContext> devContextSupplier) {
        try {
            return Optional.of(devContextSupplier.get());
        } catch (SecurityException e) {
            return Optional.empty();
        }
    }
}
