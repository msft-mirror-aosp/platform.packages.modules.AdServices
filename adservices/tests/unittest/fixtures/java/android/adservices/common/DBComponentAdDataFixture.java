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

package android.adservices.common;

import com.android.adservices.data.customaudience.DBComponentAdData;

import java.util.ArrayList;
import java.util.List;

public class DBComponentAdDataFixture {
    /** Builds a DBComponentAdData with a given componentAdData and custom audience info */
    public static DBComponentAdData getDBComponentAdData(
            ComponentAdData componentAdData, String owner, AdTechIdentifier buyer, String name) {
        return DBComponentAdData.create(
                owner,
                buyer,
                name,
                componentAdData.getRenderUri(),
                componentAdData.getAdRenderId());
    }

    /** Returns a valid list of component ads for a specified buyer. */
    public static List<DBComponentAdData> getValidComponentAdsByBuyer(
            List<ComponentAdData> componentAdDataList,
            String owner,
            AdTechIdentifier buyer,
            String name) {

        List<DBComponentAdData> result = new ArrayList<>();

        for (ComponentAdData componentAdData : componentAdDataList) {
            result.add(getDBComponentAdData(componentAdData, owner, buyer, name));
        }

        return result;
    }
}
