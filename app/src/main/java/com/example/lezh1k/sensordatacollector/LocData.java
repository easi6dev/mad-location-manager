package com.example.lezh1k.sensordatacollector;

import android.annotation.SuppressLint;
import android.location.Location;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.mapbox.mapboxsdk.geometry.LatLng;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class LocData {
    public List<MyLocation> gps;
    public List<MyLocation> kalman;
    public List<MyLocation> gpsFiltered;

    public List<MyLocation> sorted;

    @SuppressLint("NewApi")
    public LocData(List<Location> gps, List<Location> kalman, List<Location> gpsFiltered) {
        this.gps = gps.stream().map(it -> new MyLocation(it, MyLocation.GPS)).collect(Collectors.toList());
        this.kalman = kalman.stream().map(it -> new MyLocation(it, MyLocation.KALMAN)).collect(Collectors.toList());
        this.gpsFiltered = gpsFiltered.stream().map(it -> new MyLocation(it, MyLocation.FILTERED_GEO)).collect(Collectors.toList());
        this.sorted = timeBasedLocList();
    }

    public LocData(List<MyLocation> mgps, List<MyLocation> mkalman, List<MyLocation> mgpsFiltered, List<MyLocation> msorted) {
        this.gps = mgps;
        this.kalman = mkalman;
        this.gpsFiltered = mgpsFiltered;
        this.sorted = msorted;
    }

    private List<MyLocation> timeBasedLocList() {
        List<MyLocation> unsorted = new ArrayList<MyLocation>();
        unsorted.addAll(gps);
        unsorted.addAll(kalman);
        unsorted.addAll(gpsFiltered);

        unsorted.sort((o1, o2) -> (int) (o1.time - o2.time));

        return unsorted;
    }

    public LocData listWithTime(long pivot) {
        return filtered(pivot);
    }

    private LocData filtered(long delay) {
        long p = sorted.get(0).time;

        List<MyLocation> mgps = gps.stream().filter(it -> it.time <= delay + p).collect(Collectors.toList());
        List<MyLocation> mkalman = kalman.stream().filter(it -> it.time <= delay + p).collect(Collectors.toList());
        List<MyLocation> mgpsFiltered = gpsFiltered.stream().filter(it -> it.time <= delay + p).collect(Collectors.toList());
        List<MyLocation> msorted = sorted.stream().filter(it -> it.time <= delay + p).collect(Collectors.toList());

        return new LocData(
                mgps,
                mkalman,
                mgpsFiltered,
                msorted
        );
    }
}
