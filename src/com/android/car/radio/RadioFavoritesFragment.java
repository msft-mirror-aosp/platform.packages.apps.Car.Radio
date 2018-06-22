/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.radio;

import android.content.Context;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.car.widget.DayNightStyle;
import androidx.car.widget.PagedListView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.broadcastradio.support.Program;
import com.android.car.radio.storage.RadioStorage;

/**
 * Fragment that shows a list of all the current favorite radio stations
 */
public class RadioFavoritesFragment extends Fragment implements
        RadioController.ProgramInfoChangeListener,
        RadioController.RadioServiceConnectionListener,
        RadioStorage.PresetsChangeListener {

    private RadioController mRadioController;
    private BrowseAdapter mBrowseAdapter = new BrowseAdapter();
    private RadioStorage mRadioStorage;
    private PagedListView mBrowseList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.browse_fragment, container, false);
        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Context context = getContext();

        mRadioController.addRadioServiceConnectionListener(this);
        mBrowseAdapter.setOnItemClickListener(mRadioController::tune);
        mBrowseAdapter.setOnItemFavoriteListener(this::handlePresetItemFavoriteChanged);

        mBrowseList = view.findViewById(R.id.browse_list);
        mBrowseList.setDayNightStyle(DayNightStyle.ALWAYS_LIGHT);
        mBrowseList.setAdapter(mBrowseAdapter);
        RecyclerView recyclerView = mBrowseList.getRecyclerView();
        recyclerView.setVerticalFadingEdgeEnabled(true);
        recyclerView.setFadingEdgeLength(getResources()
                .getDimensionPixelSize(R.dimen.car_padding_4));

        mRadioStorage = RadioStorage.getInstance(context);
        mRadioStorage.addPresetsChangeListener(this);
    }

    @Override
    public void onStart() {
        super.onStart();
        ProgramInfo info = mRadioController.getCurrentProgramInfo();
        mRadioController.addProgramInfoChangeListener(this);
        if (info != null) {
            mBrowseAdapter.setActiveProgram(Program.fromProgramInfo(info));
        }
        mBrowseAdapter.setBrowseList(mRadioStorage.getPresets());
    }

    @Override
    public void onProgramInfoChanged(ProgramInfo info) {
        mBrowseAdapter.setActiveProgram(Program.fromProgramInfo(info));
    }

    @Override
    public void onPresetsRefreshed() {
        mBrowseAdapter.updateFavorites(mRadioStorage.getPresets());
    }

    @Override
    public void onRadioServiceConnected() {
        ProgramInfo info = mRadioController.getCurrentProgramInfo();
        if (info != null) {
            mBrowseAdapter.setActiveProgram(Program.fromProgramInfo(info));
        }
        mBrowseAdapter.setBrowseList(mRadioStorage.getPresets());
    }

    private void handlePresetItemFavoriteChanged(Program program, boolean saveAsFavorite) {
        if (saveAsFavorite) {
            mRadioStorage.storePreset(program);
        } else {
            mRadioStorage.removePreset(program.getSelector());
        }
    }

    static RadioFavoritesFragment newInstance(RadioController radioController) {
        RadioFavoritesFragment fragment = new RadioFavoritesFragment();
        fragment.mRadioController = radioController;
        return fragment;
    }

}
