package com.example.lezh1k.sensordatacollector.Presenters;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import mad.location.manager.lib.Commons.Utils;
import mad.location.manager.lib.Loggers.GeohashRTFilter;

import com.elvishew.xlog.XLog;
import com.example.lezh1k.sensordatacollector.Interfaces.MapInterface;
import com.example.lezh1k.sensordatacollector.LocData;
import com.example.lezh1k.sensordatacollector.MainActivity;
import com.example.lezh1k.sensordatacollector.MyLocation;
import com.google.gson.Gson;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static android.content.Context.LOCATION_SERVICE;

/**
 * Created by lezh1k on 1/30/18.
 */

public class MapPresenter implements LocationListener {
    private MapInterface mapInterface;
    private Context context;

    private GeohashRTFilter m_geoHashRTFilter;
    private List<Location> m_lstGpsCoordinates = new ArrayList<>();
    private List<Location> m_lstKalmanFilteredCoordinates = new ArrayList<>();

    public MapPresenter(Context context, MapInterface mapInterface, GeohashRTFilter geoHashRTFilter) {
        this.mapInterface = mapInterface;
        this.context = context;
        m_geoHashRTFilter = geoHashRTFilter;
    }

    public void locationChanged(Location loc, CameraPosition currentCameraPosition) {
        CameraPosition.Builder position =
                new CameraPosition.Builder(currentCameraPosition).target(new LatLng(loc));
        mapInterface.moveCamera(position.build());
        getRoute();
        m_lstKalmanFilteredCoordinates.add(loc);
        m_geoHashRTFilter.filter(loc);
    }

    AtomicInteger atInt;
    Thread thread;
    Timer timer;
    ExecutorService executor;
    long delay = 1000L;
    long delaySum = 0L;
    int lastSize = 0;

    public void rewindRoute(LocData locData, CameraPosition currentCameraPosition) {
        if (executor != null) {
            executor.shutdown();
        }

        lastSize = 0;
        delaySum = 0L;
        executor = Executors.newSingleThreadExecutor();

        mapInterface.clearRoute();
        CameraPosition.Builder position =
                new CameraPosition.Builder(currentCameraPosition).target(new LatLng(locData.sorted.get(0).lat, locData.sorted.get(0).lng));
        mapInterface.moveCamera(position.build());

        Runnable runnable = () -> {
            while (true) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                delaySum += delay * 10;
                LocData raw = locData.listWithTime(delaySum);
                lastSize = raw.sorted.size();

                rewindRoute2(raw);

                if (locData.sorted.size() <= lastSize) {
                    return;
                }
            }
        };

        executor.submit(runnable);
    }


    public void rewindRoute2(LocData locData) {
        List<LatLng> gps = locData.gps.stream().map(data -> new LatLng(data.lat, data.lng)).collect(Collectors.toList());
        List<LatLng> kalman = locData.kalman.stream().map(data -> new LatLng(data.lat, data.lng)).collect(Collectors.toList());
        List<LatLng> filteredGeo = locData.gpsFiltered.stream().map(data -> new LatLng(data.lat, data.lng)).collect(Collectors.toList());

        Handler handler = new Handler(Looper.getMainLooper());

        handler.post(() -> {
            mapInterface.showRoute(gps, MainActivity.GPS_ONLY);
            mapInterface.showRoute(kalman, MainActivity.FILTER_KALMAN_ONLY);
            mapInterface.showRoute(filteredGeo, MainActivity.FILTER_KALMAN_WITH_GEO);
        });
    }

    public void getRoute() {
        List<LatLng> routGpsAsIs = new ArrayList<>(m_lstGpsCoordinates.size());
        List<LatLng> routeFilteredKalman = new ArrayList<>(m_lstKalmanFilteredCoordinates.size());
        List<LatLng> routeFilteredWithGeoHash =
                new ArrayList<>(m_geoHashRTFilter.getGeoFilteredTrack().size());

        for (Location loc : new ArrayList<>(m_lstKalmanFilteredCoordinates)) {
            routeFilteredKalman.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
        }

        for (Location loc : new ArrayList<>(m_geoHashRTFilter.getGeoFilteredTrack())) {
            routeFilteredWithGeoHash.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
        }

        for (Location loc : new ArrayList<>(m_lstGpsCoordinates)) {
            routGpsAsIs.add(new LatLng(loc.getLatitude(), loc.getLongitude()));
        }

        mapInterface.showRoute(routeFilteredKalman, MainActivity.FILTER_KALMAN_ONLY);
        mapInterface.showRoute(routeFilteredWithGeoHash, MainActivity.FILTER_KALMAN_WITH_GEO);
        mapInterface.showRoute(routGpsAsIs, MainActivity.GPS_ONLY);
    }
    //////////////////////////////////////////////////////////

    public void start() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //todo something
        } else {
            LocationManager lm = (LocationManager) context.getSystemService(LOCATION_SERVICE);
            lm.removeUpdates(this);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    Utils.GPS_MIN_TIME, Utils.GPS_MIN_DISTANCE, this);
        }
    }

    public void stop() {
        LocationManager lm = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        lm.removeUpdates(this);

        m_lstGpsCoordinates.clear();
        m_lstKalmanFilteredCoordinates.clear();
    }

    public void save() {
        List<Location> gps = m_lstGpsCoordinates;
        List<Location> kalman = m_lstKalmanFilteredCoordinates;
        List<Location> gpsFiltered = m_geoHashRTFilter.getGeoFilteredTrack();

        LocData data = new LocData(gps, kalman, gpsFiltered);

        Gson gson = new Gson();
        String testText = gson.toJson(data);
        XLog.i(testText);
        fileLogging(testText);
    }

    @Override
    public void onLocationChanged(Location loc) {
        if (loc == null) return;
//        if (loc.isFromMockProvider()) return;
        m_lstGpsCoordinates.add(loc);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        /*do nothing*/
    }

    @Override
    public void onProviderEnabled(String provider) {
        /*do nothing*/
    }

    @Override
    public void onProviderDisabled(String provider) {
        /*do nothing*/
    }

    @SuppressLint("NewApi")
    public void fileLogging(final String fileContents) {
        Date date = new Date(System.currentTimeMillis());
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddhhmmss");
        String time = format.format(date);

        String title = "log_test_" + time + ".txt";

        try {
            File path = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), title);
//            File path = new File(context.getExternalFilesDirs(null)[0], title);
            FileOutputStream writer = new FileOutputStream(path);
            writer.write(fileContents.getBytes());
            writer.close();
        } catch (IOException e) {
            Log.e("file", e.toString());
        }
    }

}
