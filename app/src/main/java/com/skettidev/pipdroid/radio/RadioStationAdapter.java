package com.skettidev.pipdroid.radio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.skettidev.pipdroid.R;

import java.util.List;

public class RadioStationAdapter extends RecyclerView.Adapter<RadioStationAdapter.ViewHolder> {

    private final List<RadioStation> stations;
    private RadioStation selectedStation;
    private final OnStationClickListener listener;

    /** Listener interface for clicks */
    public interface OnStationClickListener {
        void onStationClick(RadioStation station);
    }

    public RadioStationAdapter(List<RadioStation> stations, OnStationClickListener listener) {
        this.stations = stations;
        this.listener = listener;
    }

    /** Mark a station as selected and refresh the list */
    public void setSelectedStation(RadioStation station) {
        selectedStation = station;
        notifyDataSetChanged(); // redraw to update highlight
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate your modular item layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.radio_station_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RadioStation station = stations.get(position);

        // Set station title
        holder.stationName.setText(station.getTitle());

        // Highlight the selected station
        holder.itemView.setSelected(station.equals(selectedStation));

        // Handle click: notify listener and mark as selected
        holder.itemView.setOnClickListener(v -> {
            listener.onStationClick(station);   // Update nowPlayingText via listener
            setSelectedStation(station);        // Update UI highlight
        });
    }

    @Override
    public int getItemCount() {
        return stations.size();
    }

    /** ViewHolder for station item */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView stationName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            stationName = itemView.findViewById(R.id.station_name);
        }
    }
}
