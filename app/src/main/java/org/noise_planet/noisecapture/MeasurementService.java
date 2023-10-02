/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fetch the most precise location from different location services.
 */
public class MeasurementService extends Service {

    private enum LISTENER {GPS, NETWORK, PASSIVE}
    private LocationManager gpsLocationManager;
    private LocationManager passiveLocationManager;
    private LocationManager networkLocationManager;
    private CommonLocationListener gpsLocationListener;
    private CommonLocationListener networkLocationListener;
    private CommonLocationListener passiveLocationListener;
    // New measurement record sent to the database Event object is Storage.Leq
    public static final String PROP_NEW_MEASUREMENT = "PROP_NEW_MEASUREMENT";
    private long minTimeDelay = 1000;
    private static final long MAXIMUM_LOCATION_HISTORY = 50;
    private AudioProcess audioProcess;
    private AtomicBoolean isRecording = new AtomicBoolean(false);  // Is microphone activated
    private AtomicBoolean isPaused = new AtomicBoolean(false);  // Recording is temporary paused
    private AtomicBoolean isStorageActivated = new AtomicBoolean(false); // Is leq are stored into database

    private AtomicBoolean canceled = new AtomicBoolean(false);
    // 1s leq recorded in db
    private AtomicInteger leqAdded = new AtomicInteger(0);
    private MeasurementManager measurementManager;
    private DoProcessing doProcessing = new DoProcessing(this);
    // This measurement identifier in the long term storage
    private int recordId = -1;
    // Keep the measurement only if the count of leq is equal or greater than minimalLeqCount
    private int minimalLeqCount = 0;
    // Seconds to delete when pause is activated
    private int deletedLeqOnPause = 0;
    private double dBGain = 0;
    private int calibrationMethod = 0;
    private PropertyChangeSupport listeners = new PropertyChangeSupport(this);
    private static final Logger LOGGER = LoggerFactory.getLogger(MeasurementService.class);

    private NavigableMap<Long, Location> timeLocation = new TreeMap<Long, Location>();
    private LeqStats leqStats = new LeqStats();
    private LeqStats leqStatsFast = new LeqStats();

    private NotificationManager mNM;

    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private int NOTIFICATION = R.string.local_service_started;
    private NotificationCompat.Builder notification;
    private Notification notificationInstance;
    private ListenToMicrophoneDeviceSwitch audioRecordingCallback = null;
    private double LAeq = 0; // last acquired fast lAeq

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        MeasurementService getService() {
            return MeasurementService.this;
        }
    }

    /**
     * @param dBGain Gain in dB
     */
    public void setdBGain(double dBGain, int calibrationMethod) {
        this.dBGain = dBGain;
        this.calibrationMethod = calibrationMethod;
        if(audioProcess != null && Double.compare(0, dBGain) != 0) {
            audioProcess.setGain((float)Math.pow(10, dBGain / 20));
        }
    }


    public LeqStats getLeqStats() {
        return leqStats;
    }

    public double getLAeq() {
        return LAeq;
    }

    public void setLAeq(double LAeq) {
        this.LAeq = LAeq;
    }

    public LeqStats getFastLeqStats() {
        return leqStatsFast;
    }
    public int getRecordId() {
        return recordId;
    }

    public void cancel() {
        canceled.set(true);
        isRecording.set(false);
        stopLocalisationServices();
    }

    public boolean isCanceled() {
        return canceled.get();
    }

    public int getLeqAdded() {
        return leqAdded.get();
    }

    /**
     * @return AudioProcess or null if recording has not been started
     */
    public AudioProcess getAudioProcess() {
        return audioProcess;
    }

    @Override
    public void onCreate() {
        mNM = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        this.measurementManager = new MeasurementManager(getApplicationContext());
        // Display a notification about us starting.  We put an icon in the status bar.
        showNotification();
        // Mute NoiseCapture while measuring (do not capture android sounds)
        try {
            AudioManager mgr = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
            mgr.setStreamMute(AudioManager.STREAM_SYSTEM, true);
            // Attach a listener to detect active microphone
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if(audioRecordingCallback == null) {
                    audioRecordingCallback = new ListenToMicrophoneDeviceSwitch(this);
                }
                mgr.registerAudioDeviceCallback(audioRecordingCallback, null);
            }
        } catch (SecurityException ex) {
            // Ignore
        }
    }

    /**
     * Keep the measurement only if the count of leq is equal or greater than minimalLeqCount
     * @param minimalLeqCount Minimal seconds
     */
    public void setMinimalLeqCount(int minimalLeqCount) {
        this.minimalLeqCount = minimalLeqCount;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        // Hide notification
        mNM.cancel(NOTIFICATION);
        // Stop record
        if(isRecording()) {
            cancel();
        }
        try {
            AudioManager mgr = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            mgr.setStreamMute(AudioManager.STREAM_SYSTEM, false);
            // Detach a listener to detect active microphone switch
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if(audioRecordingCallback != null) {
                    mgr.unregisterAudioDeviceCallback(audioRecordingCallback);
                }
            }
        } catch (SecurityException ex) {
            // Ignore
        }
    }

    /***
     * @return Get last precision in meters. Null if no available location
     */
    public Location getLastLocation() {
        return timeLocation.isEmpty() ? null : timeLocation.lastEntry().getValue();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void startRecording() {
        canceled.set(false);
        initLocalisationServices();
        isRecording.set(true);
        this.audioProcess = new AudioProcess(isRecording, canceled);
        if(Double.compare(0, dBGain) != 0) {
            audioProcess.setGain((float) Math.pow(10, dBGain / 20));
        }
        audioProcess.getListeners().addPropertyChangeListener(doProcessing);

        // Start measurement
        new Thread(audioProcess).start();

        // Change notification icon message
        showNotification();

    }

    public boolean isPaused() {
        return isPaused.get();
    }

    public void stopRecording() {
        isRecording.set(false);
    }

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    public static int getNotificationIcon() {
        boolean useWhiteIcon = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP);
        return useWhiteIcon ? R.drawable.ic_measure_notification : R.mipmap.ic_launcher;
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel(String channelId, String channelName) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            NotificationChannel chan = new NotificationChannel(channelId,
                    channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager service = (NotificationManager)getSystemService(Context
                    .NOTIFICATION_SERVICE);
            service.createNotificationChannel(chan);
            return channelId;
        } else {
            return "";
        }
    }

    /**
     * Show a notification while this service is running.
     */
    private void showNotification() {
        // Do not stack notifications
        try {
            mNM.cancelAll();
        } catch (SecurityException ex) {
            // Ignore
        }
        // Text for the ticker
        CharSequence text = isStoring() ? getString(R.string.notification_record_content) :
                getText(R.string.record_message);

        // Set the info for the views that show in the notification panel.
        if(notification == null) {

            // The PendingIntent to launch our activity if the user selects this notification
            PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent(this, MeasurementActivity.class), PendingIntent.FLAG_MUTABLE);

            String channelId = "";
            // If earlier version channel ID is not used
            // https://developer.android.com/reference/android/support/v4/app/NotificationCompat.Builder.html#NotificationCompat.Builder(android.content.Context)
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                channelId = createNotificationChannel("noisecapture", "NoiseCapture measurement");
            }
            notification = new NotificationCompat.Builder(this, channelId ).setSmallIcon
                    (getNotificationIcon())  // the status icon
                    .setContentTitle(getString(R.string.title_service_measurement))  // the label
                    // of the
                    // entry
                    .setContentText(text)  // the contents of the entry
                    .setContentIntent(contentIntent);  // The intent to send when the entry is
            // clicked

        } else {
            notification.setContentText(text);
        }
        // Send the notification.
        notificationInstance = notification.getNotification();
        mNM.notify(NOTIFICATION, notificationInstance);
    }

    private void initLocalisationServices() {
        initPassive();
        initGPS();
        initNetworkLocation();
    }

    private void stopLocalisationServices() {
        stopPassive();
        stopGPS();
        stopNetworkLocation();
    }

    private void restartLocalisationServices() {
        LOGGER.info("Restart localisation services");
        stopLocalisationServices();
        initLocalisationServices();
    }

    private void initPassive() {
        if (passiveLocationListener == null) {
            passiveLocationListener = new CommonLocationListener(this, LISTENER.PASSIVE);
        }
        passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            passiveLocationManager.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,
                    minTimeDelay, 0, passiveLocationListener);
        }
    }

    private void initNetworkLocation() {
        if (networkLocationListener == null) {
            networkLocationListener = new CommonLocationListener(this, LISTENER.NETWORK);
        }
        networkLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && networkLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            networkLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                    minTimeDelay, 0, networkLocationListener);
        }
    }

    private void stopGPS() {
        if (gpsLocationListener == null || gpsLocationManager == null) {
            return;
        }
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            gpsLocationManager.removeUpdates(gpsLocationListener);
        }
    }


    private void stopPassive() {
        if (passiveLocationListener == null || passiveLocationManager == null) {
            return;
        }
        passiveLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            passiveLocationManager.removeUpdates(passiveLocationListener);
        }
    }

    private void stopNetworkLocation() {
        if (networkLocationListener == null || networkLocationManager == null) {
            return;
        }
        networkLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && networkLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            networkLocationManager.removeUpdates(networkLocationListener);
        }
    }

    private void initGPS() {
        if (gpsLocationListener == null) {
            gpsLocationListener = new CommonLocationListener(this, LISTENER.GPS);
        }
        gpsLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && passiveLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            gpsLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, minTimeDelay, 0,
                    gpsLocationListener);
        }
    }

    public void addPropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.addPropertyChangeListener(propertyChangeListener);
    }

    public void removePropertyChangeListener(PropertyChangeListener propertyChangeListener) {
        listeners.removePropertyChangeListener(propertyChangeListener);
    }

    public void setPause(boolean newState) {
        isPaused.set(newState);
        LOGGER.info("Measurement pause = " + String.valueOf(newState));
        audioProcess.setDoFastLeq(!newState);
        audioProcess.setDoOneSecondLeq(!newState);
        if(newState && deletedLeqOnPause > 0 && recordId > -1) {
            // Delete last recorded leq
            int deletedLeq = measurementManager.deleteLastLeqs(recordId,
                    System.currentTimeMillis() -  (deletedLeqOnPause * 1000));
            leqAdded.set(Math.max(0, leqAdded.get() - deletedLeq));
            // Recompute LeqStats altered by the removed leq
            LeqStats newLeqStats = new LeqStats();
            // Query database
            List<Integer> frequencies = new ArrayList<Integer>();
            List<Float[]> leqValues = new ArrayList<Float[]>();
            measurementManager.getRecordLeqs(recordId, frequencies, leqValues, null);
            // parse each leq window time
            for(Float[] leqFreqs : leqValues) {
                double rms = 0;
                for(float leqValue : leqFreqs) {
                    rms += Math.pow(10, leqValue / 10);
                }
                newLeqStats.addLeq(10 * Math.log10(rms));
            }
            leqStats = newLeqStats;
            leqStatsFast = new LeqStats(newLeqStats);
        } else if(newState && recordId > -1) {
            leqStatsFast = new LeqStats();
        }
    }

    /**
     * @param deletedLeqOnPause Number of leq to delete on pause
     */
    public void setDeletedLeqOnPause(int deletedLeqOnPause) {
        this.deletedLeqOnPause = Math.max(0, deletedLeqOnPause);
    }

    /**
     * @return Deleted leq triggered by a pause
     */
    public int getDeletedLeqOnPause() {
        return deletedLeqOnPause;
    }

    public void addLocation(Location location) {
        // Check if the previous location is inside the precision range of the new location
        // Keep the new location only if the new location is 60% chance away from previous location
        // and if the new location precision is at least with better accuracy and at most worst
        // than two times of old location
        // see https://developer.android.com/guide/topics/location/strategies.html
        Location previousLocation = timeLocation.isEmpty() ? null : timeLocation.lastEntry().getValue();
        if(previousLocation == null || (location.getProvider().equals(LocationManager.GPS_PROVIDER) ||
                previousLocation.getProvider().equals(LocationManager.NETWORK_PROVIDER)||
                previousLocation.getProvider().equals(LocationManager.PASSIVE_PROVIDER))) {
            timeLocation.put(System.currentTimeMillis(), location);
            if (timeLocation.size() > MAXIMUM_LOCATION_HISTORY) {
                // Clean old entry
                timeLocation.remove(timeLocation.firstKey());
            }
        }
    }

    /**
     * Fetch the nearest location acquired during the provided utc time.
     * @param utcTime UTC time
     * @return Location or null if not found.
     */
    public Location fetchLocation(Long utcTime) {
        Map.Entry<Long, Location> low = timeLocation.floorEntry(utcTime);
        Map.Entry<Long, Location> high = timeLocation.ceilingEntry(utcTime);
        Location res = null;
        long key = 0;
        if (low != null && high != null) {
            // Got two results, find nearest
            res = Math.abs(utcTime-low.getKey()) < Math.abs(utcTime-high.getKey())
                    ?   low.getValue()
                    : high.getValue();
            key = Math.abs(utcTime-low.getKey()) < Math.abs(utcTime-high.getKey())
                    ?   low.getKey()
                    : high.getKey();
        } else if (low != null || high != null) {
            // Just one range bound, search the good one
            res = low != null ? low.getValue() : high.getValue();
            key = low != null ? low.getKey() : high.getKey();
        }
        return res;
    }


    private static class CommonLocationListener implements LocationListener, GpsStatus.Listener, GpsStatus.NmeaListener {
        private MeasurementService measurementService;
        private LISTENER listenerId;

        public CommonLocationListener(MeasurementService measurementService, LISTENER listenerId) {
            this.measurementService = measurementService;
            this.listenerId = listenerId;
        }

        @Override
        public void onGpsStatusChanged(int event) {

        }

        @Override
        public void onLocationChanged(Location location) {
            measurementService.addLocation(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {
            measurementService.restartLocalisationServices();
        }

        @Override
        public void onProviderDisabled(String provider) {
            measurementService.restartLocalisationServices();
        }


        private int nmeaChecksum(String s) {
            int c = 0;
            for(char ch : s.toCharArray()) {
                c ^= ch;
            }
            return c;
        }

        @Override
        public void onNmeaReceived(long timestamp, String nmea) {

            if(nmea == null || !nmea.startsWith("$")){
                return;
            }
            StringTokenizer stringTokenizer = new StringTokenizer(nmea, ",");
            //TODO read NMEA
            // Used by bluetooth GPS receivers

        }
    }

    private static class DoProcessing implements  PropertyChangeListener {
        private MeasurementService measurementService;

        public DoProcessing(MeasurementService measurementService) {
            this.measurementService = measurementService;
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            // Skip event if we do not record or if the pause is active
            if (AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName
                    ())) {
                if (measurementService.isStoring() && !measurementService.isPaused.get()) {
                    // Delayed audio processing
                    AudioProcess.AudioMeasureResult measure =
                            (AudioProcess.AudioMeasureResult) event.getNewValue();
                    Location location = measurementService.fetchLocation(measure.getBeginRecordTime());
                    Storage.Leq leq;
                    if (location == null) {
                        leq = new Storage.Leq(measurementService.recordId, -1, measure
                                .getBeginRecordTime(), 0, 0, null,
                                null, null, 0.f, 0,
                                (float)measure.getGlobaldBaValue());
                    } else {
                        leq = new Storage.Leq(measurementService.recordId, -1, measure
                                .getBeginRecordTime(), location.getLatitude(), location.getLongitude(),
                                location.hasAltitude() ? location.getAltitude() : null,
                                location.hasSpeed() ? location.getSpeed() : null,
                                location.hasBearing() ? location.getBearing() : null,
                                location.getAccuracy(), location.getTime(),
                                (float)measure.getGlobaldBaValue());
                    }
                    double[] freqValues = measurementService.audioProcess.getDelayedCenterFrequency();
                    final double[] leqs = measure.getLeqs();
                    // Add leqs to stats
                    measurementService.leqStats.addLeq(measure.getGlobaldBaValue());
                    List<Storage.LeqValue> leqValueList = new ArrayList<>(leqs.length);
                    for (int idFreq = 0; idFreq < leqs.length; idFreq++) {
                        leqValueList
                                .add(new Storage.LeqValue(-1, (int) freqValues[idFreq], leqs[idFreq]));
                    }
                    measurementService.measurementManager
                            .addLeqBatch(new MeasurementManager.LeqBatch(leq, leqValueList));
                    measurementService.leqAdded.addAndGet(1);
                    measurementService.listeners.firePropertyChange(PROP_NEW_MEASUREMENT, null, new MeasurementEventObject(measure, leq));
                }
            } else if(AudioProcess.PROP_FAST_LEQ.equals(event.getPropertyName())) {
                AudioProcess.AudioMeasureResult measure =
                        (AudioProcess.AudioMeasureResult) event.getNewValue();
                measurementService.setLAeq(measure.getGlobaldBaValue());
                if (measurementService.isStoring() && !measurementService.isPaused.get()) {
                    measurementService.leqStatsFast.addLeq(measure.getGlobaldBaValue());
                }
            } else if (AudioProcess.PROP_STATE_CHANGED.equals(event.getPropertyName())) {
                if (AudioProcess.STATE.CLOSED.equals(event.getNewValue())) {
                    if(measurementService.recordId > -1) {
                        // Recording and processing of audio has been closed
                        // Cancel the persistent notification.
                        if (measurementService.canceled.get() || measurementService.leqAdded.get()
                                < measurementService.minimalLeqCount) {
                            // Canceled or has not the minimal leq count
                            // Destroy record
                            measurementService.measurementManager
                                    .deleteRecord(measurementService.recordId);
                        } else {
                            // Update record
                            if(measurementService.audioProcess.getMicrophoneInfo() != null) {
                                measurementService.measurementManager.
                                        updateRecordMicrophoneDeviceSettings(measurementService.recordId,
                                                measurementService.audioProcess.getMicrophoneInfo());
                            }
                            measurementService.measurementManager.
                                    updateRecordFinal(measurementService.recordId,
                                            (float) measurementService.leqStats.getLeqMean(),
                                            measurementService.leqAdded.get(),
                                            (float)measurementService.dBGain);
                        }
                    }
                    measurementService.isRecording.set(false);
                    measurementService.stopLocalisationServices();
                    // Stop task
                    measurementService.stopForeground(true);
                    measurementService.stopSelf();
                }
            }
            measurementService.listeners.firePropertyChange(event);
        }
    }

    /**
     * @return True if microphone is enabled
     */
    public boolean isRecording() {
        return isRecording.get();
    }

    /**
     * @return True if storage of records are activated
     */
    public boolean isStoring() {
        return isStorageActivated.get();
    }

    /**
     * Start the storage of leq in database
     */
    public void startStorage() {
        if(!isRecording()) {
            startRecording();
        }
        recordId = measurementManager.addRecord(Storage.Record.CALIBRATION_METHODS.values()[calibrationMethod]);
        leqAdded.set(0);
        isStorageActivated.set(true);
        showNotification();
        // Set is foreground in order to let this service running without stopping
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                startForeground(NOTIFICATION, notificationInstance);
            } catch (SecurityException ex) {
                // Ignore, noisecapture can not be run on background on this smartphone
            }
        }
    }

    public static final class MeasurementEventObject {
        public final Storage.Leq leq;
        public final AudioProcess.AudioMeasureResult measure;

        public MeasurementEventObject(AudioProcess.AudioMeasureResult measure, Storage.Leq leq) {
            this.measure = measure;
            this.leq = leq;
        }
    }

    /**
     * Listen to microphone device switch (the user plug a new microphone)
     */
    @RequiresApi(api = Build.VERSION_CODES.N)
    private static final class ListenToMicrophoneDeviceSwitch extends AudioDeviceCallback {
        MeasurementService measurementService;

        public ListenToMicrophoneDeviceSwitch(MeasurementService measurementService) {
            this.measurementService = measurementService;
        }

        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            if(measurementService.audioProcess != null) {
                measurementService.audioProcess.refreshMicrophoneInfo();
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            if(measurementService.audioProcess != null) {
                measurementService.audioProcess.refreshMicrophoneInfo();
            }
        }
    }
}