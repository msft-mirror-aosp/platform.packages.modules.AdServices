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
package com.android.adservices.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.adservices.api.R;

/** Fragment for the topics view of the AdServices Settings App. */
public class AdServicesSettingsTopicsFragment extends Fragment {
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.topics_fragment, container, false);

        TopicsViewModel viewModel = new ViewModelProvider(this).get(TopicsViewModel.class);
        initTopicsList(rootView, viewModel);

        return rootView;
    }

    private void initTopicsList(View rootView, TopicsViewModel viewModel) {
        RecyclerView recyclerView = rootView.findViewById(R.id.topics_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        TopicsViewAdapter adapter = new TopicsViewAdapter(viewModel);
        recyclerView.setAdapter(adapter);
    }
}
