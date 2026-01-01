package com.skettidev.pipdroid.radio;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.skettidev.pipdroid.R;

public class RadioFragment extends Fragment {

    private ImageButton radioPowerButton;
    private RecyclerView recyclerView;
    private RadioStationAdapter adapter;

    public interface RadioCallback {
        void onStationClicked(RadioStation station);
        void onPowerToggled();
    }

    private RadioCallback callback;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof RadioCallback) {
            callback = (RadioCallback) context;
        } else {
            throw new RuntimeException("MainMenu must implement RadioCallback");
        }
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.radio, container, false);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Step 1: Get references to UI elements
        radioPowerButton = view.findViewById(R.id.radio_power_button);
        recyclerView = view.findViewById(R.id.radio_station_list);

        Log.d("onViewCreated", "Step 1: Got references to UI elements.");

        // Step 2: Initialize the recyclerView field before setting its adapter
        recyclerView = view.findViewById(R.id.radio_station_list);

        Log.d("onViewCreated", "Step 2: Initialized the recyclerView field before setting its adapter.");

        // Step 3: Set the layout manager for the RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        Log.d("onViewCreated", "Step 3: Set the layout manager for the RecyclerView.");

        // Step 4: Set the adapter for the RecyclerView
        adapter = new RadioStationAdapter(RadioStationList.getStations(), station -> callback.onStationClicked(station));
        recyclerView.setAdapter(adapter);

        Log.d("onViewCreated", "Step 4: Set the adapter for the RecyclerView.");

        // Step 5: Set an onClickListener for the radioPowerButton
        radioPowerButton.setOnClickListener(v -> callback.onPowerToggled());

        Log.d("onViewCreated", "Step 5: Set an onClickListener for the radioPowerButton.");
    }

    public void setSelectedStation(RadioStation station) {
        adapter.setSelectedStation(station);
    }

    public void setRadioOn(boolean on) {
        radioPowerButton.setAlpha(on ? 1f : 0.4f);
    }
}
