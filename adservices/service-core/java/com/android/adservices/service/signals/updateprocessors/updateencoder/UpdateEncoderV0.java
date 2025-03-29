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

package com.android.adservices.service.signals.updateprocessors.updateencoder;

import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.service.signals.updateprocessors.UpdateOutput;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/**
 * V0 implementation of the UpdateEncoder update processor. Uses the following update schema:
 *
 * <pre>
 * {
 *   "update_encoder": {
 *     "action": <strong>[Action]</strong>,
 *     "endpoint": <strong>[Endpoint</strong>
 *   }
 * }
 * </pre>
 */
public class UpdateEncoderV0 extends UpdateEncoder {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String ACTION = "action";
    private static final String ENDPOINT = "endpoint";

    @Override
    public UpdateOutput processUpdates(
            Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current) throws JSONException {
        UpdateOutput toReturn = new UpdateOutput();
        JSONObject updatesObject =
                UpdateProcessorUtils.validateAndCastToJSONObject(UPDATE_ENCODER, updates);

        try {
            if (!updatesObject.has(ACTION)) {
                sLogger.v("No update event type present, skipping updating encoder");
                return toReturn;
            }
            String action = updatesObject.getString(ACTION);

            UpdateEncoderEvent.Builder eventBuilder = UpdateEncoderEvent.builder();
            eventBuilder.setUpdateType(UpdateEncoderEvent.UpdateType.valueOf(action));

            if (updatesObject.has(ENDPOINT)) {
                String uriString = updatesObject.getString(ENDPOINT);
                eventBuilder.setEncoderEndpointUri(Uri.parse(uriString));
            }

            toReturn.setUpdateEncoderEvent(eventBuilder.build());

        } catch (JSONException e) {
            throw new JSONException("No valid update encoder event found");
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Unrecognized update event type: %s", e));
        }

        return toReturn;
    }
}
