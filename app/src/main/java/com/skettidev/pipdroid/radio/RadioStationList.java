package com.skettidev.pipdroid.radio;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


public class RadioStationList {

    /** Default station when Radio tab opens */
    public static final RadioStationId DEFAULT_STATION =
            RadioStationId.FONV_MOJAVE;

    public static List<RadioStation> getStations() {
        List<RadioStation> stations = new ArrayList<>();

        // Fallout 3
        stations.add(new RadioStation(
                RadioStationId.FO3_GNR,
                FalloutGame.FALLOUT_3,
                "FO3 - Galaxy News Radio",
                "https://stations.fallout.radio/listen/fallout_3_-_galaxy_news_radio/radio.mp3",
                3, // nowPlayingId
                "https://static.wikia.nocookie.net/fallout/images/8/85/Fallout3Soundtrack.png",
                true
        ));

        stations.add(new RadioStation(
                RadioStationId.FO3_ENCLAVE,
                FalloutGame.FALLOUT_3,
                "FO3 - Enclave Radio",
                "https://stations.fallout.radio/listen/fallout_3_-_enclave_radio/radio.mp3",
                4,
                "https://static.wikia.nocookie.net/fallout/images/8/85/Fallout3Soundtrack.png",
                true
        ));

        stations.add(new RadioStation(
                RadioStationId.FO3_AGATHA,
                FalloutGame.FALLOUT_3,
                "FO3 - Agatha's Station",
                "https://stations.fallout.radio/listen/fallout_3_-_agathas_station/radio.mp3",
                11,
                "https://static.wikia.nocookie.net/fallout/images/8/85/Fallout3Soundtrack.png",
                true
        ));

        stations.add(new RadioStation(
                RadioStationId.FO3_VAULT101,
                FalloutGame.FALLOUT_3,
                "FO3 - Vault 101 PA System",
                "https://stations.fallout.radio/listen/fallout_3_-_vault_101_pa_system/radio.mp3",
                12,
                "https://static.wikia.nocookie.net/fallout/images/8/85/Fallout3Soundtrack.png",
                true
        ));

        // New Vegas
        stations.add(new RadioStation(
                RadioStationId.FONV_MOJAVE,
                FalloutGame.NEW_VEGAS,
                "FONV - Mojave Music Radio",
                "https://stations.fallout.radio/listen/fallout_new_vegas_-_mojave_music_radio/radio.mp3",
                7,
                "https://archive.org/services/img/johann-sebastian-bach-concerto-for-2-violins-in-d-minor-allegro-ma-non-troppo/full/pct:500/0/default.jpg",
                true
        ));

        stations.add(new RadioStation(
                RadioStationId.FONV_RNV,
                FalloutGame.NEW_VEGAS,
                "FONV - Radio New Vegas",
                "https://stations.fallout.radio/listen/fallout_new_vegas_-_radio_new_vegas/radio.mp3",
                8,
                "https://archive.org/services/img/johann-sebastian-bach-concerto-for-2-violins-in-d-minor-allegro-ma-non-troppo/full/pct:500/0/default.jpg",
                true
        ));

        // Fallout 4
        stations.add(new RadioStation(
                RadioStationId.FO4_DIAMOND_CITY,
                FalloutGame.FALLOUT_4,
                "FO4 - Diamond City Radio",
                "https://stations.fallout.radio/listen/fallout_4_-_diamond_city_radio/radio.mp3",
                10,
                "https://m.media-amazon.com/images/I/518xEmqH8HL._UXNaN_FMjpg_QL85_.jpg",
                true
        ));

        // Fallout 76
        stations.add(new RadioStation(
                RadioStationId.FO76_PIRATE,
                FalloutGame.FALLOUT_76,
                "FO76 - Pirate Radio",
                "https://stations.fallout.radio/listen/fallout_76_-_pirate_radio/radio.mp3",
                14,
                "https://static.wikia.nocookie.net/fallout/images/a/a7/FO76_Original_Game_Score_cover.jpg",
                true
        ));

        return stations;
    }


    /** Sorted by game, then title */
    public static List<RadioStation> getSortedStations() {
        return getStations().stream()
                .sorted(Comparator
                        .comparing(RadioStation::getGame)
                        .thenComparing(RadioStation::getTitle))
                .collect(Collectors.toList());
    }

    /** Find default station */
    public static RadioStation getDefaultStation() {
        for (RadioStation station : getStations()) {
            if (station.getId() == DEFAULT_STATION) {
                return station;
            }
        }
        return null;
    }
}
