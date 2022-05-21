package com.example.lezh1k.sensordatacollector;

import android.location.Location;

public class MyLocation {
    public static final int GPS = 1;
    public static final int KALMAN = 2;
    public static final int FILTERED_GEO = 3;

    public double lat;
    public double lng;
    public long time;
    public int type;

    public MyLocation(Location location, int type) {
        this.lat = location.getLatitude();
        this.lng = location.getLongitude();
        this.time = location.getTime();
        this.type = type;
    }
}
