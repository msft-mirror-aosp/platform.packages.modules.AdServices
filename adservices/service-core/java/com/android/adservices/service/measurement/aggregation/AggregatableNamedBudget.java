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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.Nullable;

import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.FilterMap;
import com.android.adservices.service.measurement.util.Filter;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

/** Named Budget containing the budget name and filters info. */
public class AggregatableNamedBudget {
    @Nullable private final String mName;
    @Nullable private final List<FilterMap> mFilterSet;
    @Nullable private final List<FilterMap> mNotFilterSet;

    public AggregatableNamedBudget(JSONObject budgetObj, Flags flags) throws JSONException {
        mName =
                !budgetObj.isNull(NamedBudgetContract.NAME)
                        ? budgetObj.getString(NamedBudgetContract.NAME)
                        : null;
        Filter filter = new Filter(flags);

        mFilterSet =
                !budgetObj.isNull(Filter.FilterContract.FILTERS)
                        ? filter.deserializeFilterSet(
                                budgetObj.getJSONArray(Filter.FilterContract.FILTERS))
                        : null;

        mNotFilterSet =
                !budgetObj.isNull(Filter.FilterContract.NOT_FILTERS)
                        ? filter.deserializeFilterSet(
                                budgetObj.getJSONArray(Filter.FilterContract.NOT_FILTERS))
                        : null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregatableNamedBudget)) {
            return false;
        }
        AggregatableNamedBudget budgetObj = (AggregatableNamedBudget) obj;
        return Objects.equals(mName, budgetObj.mName)
                && Objects.equals(mFilterSet, budgetObj.mFilterSet)
                && Objects.equals(mNotFilterSet, budgetObj.mNotFilterSet);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mFilterSet, mNotFilterSet);
    }

    /** Budget name to match with source. */
    @Nullable
    public String getName() {
        return mName;
    }

    /** Returns NamedBudget filters. */
    @Nullable
    public List<FilterMap> getFilterSet() {
        return mFilterSet;
    }

    /** Returns NamedBudget not_filters, the reverse of filter. */
    @Nullable
    public List<FilterMap> getNotFilterSet() {
        return mNotFilterSet;
    }

    /** NamedBudget field keys. */
    public interface NamedBudgetContract {
        String NAMED_BUDGETS = "named_budgets";
        String NAME = "name";
    }
}
