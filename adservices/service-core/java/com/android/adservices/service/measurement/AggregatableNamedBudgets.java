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

package com.android.adservices.service.measurement;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Class for keeping track of aggregatable named budgets */
public class AggregatableNamedBudgets {
    private Map<String, BudgetAndContribution> mNameToBudgetAndAggregateContribution =
            new HashMap<>();

    private static class BudgetAndContribution {
        public int mAggregateContribution;
        public int mBudget;

        BudgetAndContribution(int budget) {
            mAggregateContribution = 0;
            mBudget = budget;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAggregateContribution, mBudget);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BudgetAndContribution)) {
                return false;
            }
            BudgetAndContribution budgetAndContribution = (BudgetAndContribution) obj;
            return budgetAndContribution.mAggregateContribution == mAggregateContribution
                    && budgetAndContribution.mBudget == mBudget;
        }
    }

    /**
     * @return the budget
     */
    public Optional<Integer> maybeGetBudget(String name) {
        if (mNameToBudgetAndAggregateContribution.containsKey(name)) {
            return Optional.of(mNameToBudgetAndAggregateContribution.get(name).mBudget);
        }
        return Optional.empty();
    }

    /**
     * @return the aggregate contribution towards the named budget
     */
    public Optional<Integer> maybeGetContribution(String name) {
        if (mNameToBudgetAndAggregateContribution.containsKey(name)) {
            return Optional.of(
                    mNameToBudgetAndAggregateContribution.get(name).mAggregateContribution);
        }
        return Optional.empty();
    }

    /**
     * @return whether the contribution was successfully added to the named budget or not
     */
    public boolean setContribution(String name, int contribution) {
        if (contribution > mNameToBudgetAndAggregateContribution.get(name).mBudget) {
            return false;
        }

        // Set mAggregateContribution to contribution for the named budget.
        mNameToBudgetAndAggregateContribution.get(name).mAggregateContribution = contribution;
        return true;
    }

    /** Set budget for the named budget's aggregate contributions */
    public void createContributionBudget(String name, int maxBudget) {
        BudgetAndContribution budgetAndContribution = new BudgetAndContribution(maxBudget);

        mNameToBudgetAndAggregateContribution.put(name, budgetAndContribution);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mNameToBudgetAndAggregateContribution);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregatableNamedBudgets)) {
            return false;
        }
        AggregatableNamedBudgets aggregatableNamedBudgets = (AggregatableNamedBudgets) obj;
        return Objects.equals(
                mNameToBudgetAndAggregateContribution,
                aggregatableNamedBudgets.mNameToBudgetAndAggregateContribution);
    }
}
