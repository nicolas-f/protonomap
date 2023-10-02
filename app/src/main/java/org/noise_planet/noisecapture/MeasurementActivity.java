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
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import com.google.android.material.tabs.TabLayout;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.webkit.WebView;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.HorizontalBarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;

import org.orbisgis.sos.LeqStats;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MeasurementActivity extends MainActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, MapFragment.MapFragmentAvailableListener {

    //public ImageButton buttonRecord;
    //public ImageButton buttoncancel;

    private AtomicBoolean isComputingMovingLeq = new AtomicBoolean(false);
    // For the Charts
    protected HorizontalBarChart mChart; // VUMETER representation
    private DoProcessing doProcessing;
    private ImageButton buttonRecord;
    private ImageButton buttonPause;
    private ViewPagerExt viewPager;
    private static final int PAGE_SPECTRUM = 0;
    private static final int PAGE_SPECTROGRAM = 1;
    private static final int PAGE_MAP = 2;
    // From this accuracy the location hint color is orange
    private static final float APPROXIMATE_LOCATION_ACCURACY = 10.f;
    private static final double MINIMAL_DISTANCE_RESTORE_MAP = 3.f;
    private static final int MAX_LOCATIONS_RESTORE_MAP = 500;

    // Other resources
    private boolean mIsBound = false;
    private long lastMapLocationRefresh = 0;
    // Map user location refresh rate in milliseconds
    private static final long REFRESH_MAP_LOCATION_RATE = 1000;
    private AtomicBoolean chronometerWaitingToStart = new AtomicBoolean(false);

    public final static double MIN_SHOWN_DBA_VALUE = 20;
    public final static double MAX_SHOWN_DBA_VALUE = 120;

    private static final int DEFAULT_MINIMAL_LEQ = 1;
    private static final int DEFAULT_DELETE_LEQ_ON_PAUSE = 0;

    private boolean hasMaximalMeasurementTime;
    private int maximalMeasurementTime = 0;

    // NoiseCapture will switch from Hann window to Rectangular window if the measurement delay
    // is superior than this value in ms
    private final static long SWITCH_TO_FAST_RECTANGULAR_DELAY = 1500;

    private static final String LOG_SCALE_SETTING = "settings_spectrogram_logscalemode";
    private static final String DELETE_LEQ_ON_PAUSE_SETTING = "settings_delete_leq_on_pause";
    private static final String HAS_MAXIMAL_MEASURE_TIME_SETTING = "settings_recording";
    private static final String MAXIMAL_MEASURE_TIME_SETTING = "settings_recording_duration";
    private static final String SETTINGS_MEASUREMENT_DISPLAY_WINDOW = "settings_measurement_display_window";
    private static final int DEFAULT_MAXIMAL_MEASURE_TIME_SETTING = 10;

    public int getRecordId() {
        return measurementService.getRecordId();
    }


    public void initComponents() {
        Spectrogram spectrogram = getSpectrogram();
        if(spectrogram != null) {
            spectrogram.setTimeStep(measurementService.getAudioProcess().getFFTDelay());
        }
        setData(0);
        updateSpectrumGUI();
    }

    private Spectrogram getSpectrogram() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_SPECTROGRAM).getView();
        if(view != null) {
            return (Spectrogram) view.findViewById(R.id.spectrogram_view);
        } else {
            return null;
        }
    }

    private BarChart getSpectrum() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_SPECTRUM).getView();
        if(view != null) {
            return (BarChart) view.findViewById(R.id.spectrumChart);
        } else {
            return null;
        }
    }

    private WebView getMap() {
        View view = ((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_MAP).getView();
        if(view != null) {
            return (WebView) view.findViewById(R.id.measurement_webmapview);
        } else {
            return null;
        }
    }

    private MapFragment getMapControler() {
        return (MapFragment) (((ViewPagerAdapter)viewPager.getAdapter()).getItem(PAGE_MAP));
    }

    private void setupViewPager(ViewPagerExt viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        adapter.addFragment(new MeasurementSpectrumFragment(), getString(R.string.measurement_tab_spectrum));
        adapter.addFragment(new MeasurementSpectrogramFragment(), getString(R.string.measurement_tab_spectrogram));
        MapFragment mapFragment = new MapFragment();
        mapFragment.setMapFragmentAvailableListener(this);
        adapter.addFragment(mapFragment, getString(R.string.measurement_tab_map));
        // Give full control of swipe to the map instead of the tabs controller.
        viewPager.addIgnoredTab(2);
        viewPager.setAdapter(adapter);
    }

    @Override
    public void onMapFragmentAvailable(MapFragment mapFragment) {
        mapFragment.loadUrl("file:///android_asset/html/map_measurement.html");
    }

    @Override
    public void onPageLoaded(MapFragment mapFragment) {
        // Nothing to do
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if(LOG_SCALE_SETTING.equals(key)) {
            Spectrogram spectrogram = getSpectrogram();
            if(spectrogram != null) {
                spectrogram.setScaleMode(sharedPreferences.getBoolean(key, true) ?
                        Spectrogram.SCALE_MODE.SCALE_LOG : Spectrogram.SCALE_MODE.SCALE_LINEAR);
            }
        } else if(DELETE_LEQ_ON_PAUSE_SETTING.equals(key)) {
            measurementService.setDeletedLeqOnPause(getInteger(sharedPreferences,key, DEFAULT_DELETE_LEQ_ON_PAUSE));
        } else if(HAS_MAXIMAL_MEASURE_TIME_SETTING.equals(key)) {
            hasMaximalMeasurementTime = sharedPreferences.getBoolean(HAS_MAXIMAL_MEASURE_TIME_SETTING,
                    false);
        } else if(MAXIMAL_MEASURE_TIME_SETTING.equals(key)) {
            maximalMeasurementTime = getInteger(sharedPreferences,MAXIMAL_MEASURE_TIME_SETTING, DEFAULT_MAXIMAL_MEASURE_TIME_SETTING);
        } else if("settings_recording_gain".equals(key) && measurementService != null) {
            measurementService.setdBGain(getDouble(sharedPreferences, key, 0), getInteger(sharedPreferences, "settings_calibration_method", 0));
        } else if(SETTINGS_MEASUREMENT_DISPLAY_WINDOW.equals(key) && measurementService != null) {
            measurementService.getAudioProcess().setHannWindowFast(sharedPreferences.getString(SETTINGS_MEASUREMENT_DISPLAY_WINDOW, "RECTANGULAR").equals("HANN"));
            if(BuildConfig.DEBUG) {
                System.out.println("Switch to Rectangular window");
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(checkAndAskPermissions()) {
            // Application have right now all permissions
            doBindService();
        }
        setContentView(R.layout.activity_measurement);
        initDrawer();

        // Check if the dialog box (for caution) must be displayed
        // Depending on the settings
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        Boolean CheckNbRunSettings = sharedPref.getBoolean("settings_caution", true);

        hasMaximalMeasurementTime = sharedPref.getBoolean(HAS_MAXIMAL_MEASURE_TIME_SETTING,
                false);
        maximalMeasurementTime = getInteger(sharedPref, MAXIMAL_MEASURE_TIME_SETTING, DEFAULT_MAXIMAL_MEASURE_TIME_SETTING);
        if (CheckNbRunSettings && CheckNbRun("NbRunMaxCaution", getResources().getInteger(R.integer
                .NbRunMaxCaution))) {
            new AlertDialog.Builder(this).setTitle(R.string.title_caution)
                    .setMessage(R.string.text_caution)
                    .setNeutralButton(R.string.text_OK, null)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }

        // Enabled/disabled buttons
        buttonPause = (ImageButton) findViewById(R.id.pauseBtn);
        buttonPause.setEnabled(false);

        // To start a record (test mode)
        buttonRecord = (ImageButton) findViewById(R.id.recordBtn);
        buttonRecord.setImageResource(R.drawable.button_record_normal);
        buttonRecord.setEnabled(true);

        // Actions on record button
        doProcessing = new DoProcessing(this);
        buttonRecord.setOnClickListener(doProcessing);

        // Action on cancel button (during recording)
        buttonPause.setOnClickListener(onButtonPause);
        buttonPause.setOnTouchListener(new ToggleButtonTouch(this));

        // Init tabs (Spectrum, Spectrogram, Map)

        viewPager = (ViewPagerExt) findViewById(R.id.measurement_viewpager);
        setupViewPager(viewPager);
        TabLayout tabLayout = (TabLayout) findViewById(R.id.measurement_tabs);
        tabLayout.setupWithViewPager(viewPager);
        // Select map by default
        viewPager.setCurrentItem(2);
        // Instantaneous sound level VUMETER
        // Stacked bars are used for represented Min, Current and Max values
        // Horizontal barchart
        LinearLayout graphLayouts = (LinearLayout) findViewById(R.id.graph_components_layout);
        mChart = (HorizontalBarChart) findViewById(R.id.vumeter);
        mChart.setTouchEnabled(false);

        initVueMeter();
        setData(0);
        // Legend: hide all
        Legend lv = mChart.getLegend();
        lv.setEnabled(false); // Hide legend

    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_RECORD_AUDIO_AND_GPS: {
                // If request is cancelled, the result arrays are empty.
                for(int permissionId = 0; permissionId < permissions.length; permissionId++) {
                    if(permissions[permissionId].equals(Manifest.permission.RECORD_AUDIO)) {
                        if (grantResults.length > 0
                                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                            doBindService();
                        } else {
                            // permission denied
                            // Ask again
                            checkAndAskPermissions();
                        }
                    } else if(permissions[permissionId].equals(Manifest.permission.ACCESS_FINE_LOCATION)) {
                        // If accepted, request background location
                        if(grantResults[permissionId] == PackageManager.PERMISSION_GRANTED) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                ActivityCompat.requestPermissions(this,
                                        new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                        PERMISSION_RECORD_AUDIO_AND_GPS);
                            }
                        } else {
                            ActivityCompat.requestPermissions(this,
                                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                                    PERMISSION_RECORD_AUDIO_AND_GPS);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        checkTransferResults();
    }

    private View.OnClickListener onButtonPause = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            // Stop measurement without waiting for the end of processing
            measurementService.setPause(!measurementService.isPaused());
            chronometerWaitingToStart.set(true);
            MeasurementActivity.this.runOnUiThread(new UpdateText(MeasurementActivity.this));
        }
    };

    private static class ToggleButtonTouch implements View.OnTouchListener {
        MeasurementActivity measurement;

        public ToggleButtonTouch(MeasurementActivity measurement) {
            this.measurement = measurement;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(event.getAction() == MotionEvent.ACTION_DOWN) {
                v.setPressed(!measurement.measurementService.isPaused());
                v.performClick();
            }
            return true;
        }
    }



    // Init RNE Pie Chart
    public void initVueMeter(){
        mChart.setDrawBarShadow(false);
        mChart.setDescription("");
        mChart.setPinchZoom(false);
        mChart.setDrawGridBackground(false);
        mChart.setMaxVisibleValueCount(0);
        mChart.setScaleXEnabled(false); // Disable scaling on the X-axis
        // XAxis parameters: hide all
        XAxis xlv = mChart.getXAxis();
        xlv.setPosition(XAxisPosition.BOTTOM);
        xlv.setDrawAxisLine(false);
        xlv.setDrawGridLines(false);
        xlv.setDrawLabels(false);
        // YAxis parameters (left): main axis for dB values representation
        YAxis ylv = mChart.getAxisLeft();
        ylv.setDrawAxisLine(false);
        ylv.setDrawGridLines(true);
        ylv.setAxisMaxValue(110.f);
        ylv.setStartAtZero(true);
        ylv.setTextColor(Color.WHITE);
        ylv.setGridColor(Color.WHITE);
        ylv.setValueFormatter(new dBValueFormatter());
        // YAxis parameters (right): no axis, hide all
        YAxis yrv = mChart.getAxisRight();
        yrv.setEnabled(false);
        //return true;
    }


    // Fix the format of the dB Axis of the vumeter
    public class dBValueFormatter implements YAxisValueFormatter {

        private DecimalFormat mFormat;

        public dBValueFormatter() {
            mFormat = new DecimalFormat("###,###,##0"); // use one decimal
        }

        @Override
        public String getFormattedValue(float value, YAxis yAxis) {
            return mFormat.format(value);
        }
    }

    // Generate artificial 1 data (sound level) for vumeter representation
    private void setData(double val) {

        ArrayList<String> xVals = new ArrayList<String>();
        xVals.add("");

        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        yVals1.add(new BarEntry((float)val, 0));

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        //set1.setBarSpacePercent(35f);
        //set1.setColor(Color.rgb(0, 153, 204));
        int nc=getNEcatColors(val);    // Choose the color category in function of the sound level
        set1.setColor(NE_COLORS[nc]);

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        mChart.setData(data);
        mChart.invalidate(); // refresh
    }

    private void updateSpectrumGUI() {


        ArrayList<String> xVals = new ArrayList<String>();
        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();
        double[] freqLabels = measurementService.getAudioProcess().getRealtimeCenterFrequency();
        double[] freqValues = measurementService.getAudioProcess().getThirdOctaveFrequencySPL();
        for(int idfreq =0; idfreq < freqLabels.length; idfreq++) {
            xVals.add(Spectrogram.formatFrequency((int)freqLabels[idfreq]));
            // Sum values
            // Compute frequency range covered by frequency
            yVals1.add(new BarEntry(new float[] {(float)freqValues[idfreq]}, idfreq));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        set1.setColor(Color.rgb(102, 178, 255));
        set1.setStackLabels(new String[]{
                "SL"
        });

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);

        BarChart sChart = getSpectrum();
        if(sChart != null){
            sChart.setData(data);
            sChart.setPadding(0, 0, 0, 0);
            sChart.setViewPortOffsets(0,0,0,0);
            sChart.invalidate(); // refresh
        }
    }

    private static class WaitEndOfProcessing implements Runnable {
        private MeasurementActivity activity;
        private ProgressDialog processingDialog;

        public WaitEndOfProcessing(MeasurementActivity activity, ProgressDialog processingDialog) {
            this.activity = activity;
            this.processingDialog = processingDialog;
        }

        @Override
        public void run() {
            int lastShownProgress = 0;
            while(activity.measurementService.getAudioProcess().getCurrentState() !=
                    AudioProcess.STATE.CLOSED && !activity.measurementService.isCanceled()) {
                try {
                    Thread.sleep(200);
                    int progress =  (int)(activity.measurementService.getAudioProcess().getRemainingNotProcessTime());
                    if(progress != lastShownProgress) {
                        lastShownProgress = progress;
                        activity.runOnUiThread(new SetDialogMessage(processingDialog, activity.getResources().getString(R.string.measurement_processlastsamples,
                                lastShownProgress)));
                    }
                } catch (InterruptedException ex) {
                    return;
                }
            }

            // If canceled or ended before 1s
            if(!activity.measurementService.isCanceled() && activity.measurementService.getLeqAdded() != 0) {
                processingDialog.dismiss();
                // Goto the Results activity
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Intent ir = new Intent(activity.getApplicationContext(), CommentActivity.class);
                        ir.putExtra(MainActivity.RESULTS_RECORD_ID,
                                activity.measurementService.getRecordId());
                        ir.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(ir);
                        activity.finish();
                    }
                });

            } else {
                // No recordId available, restart measurement activity
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        processingDialog.dismiss();
                        Intent im = new Intent(activity, MeasurementActivity.class);
                        im.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        activity.startActivity(im);
                        activity.finish();
                    }});
            }
        }
    }

    private static class SetDialogMessage implements Runnable {
        private ProgressDialog dialog;
        private String message;

        public SetDialogMessage(ProgressDialog dialog, String message) {
            this.dialog = dialog;
            this.message = message;
        }

        @Override
        public void run() {
            dialog.setMessage(message);
        }
    }

    private void initGuiState() {
        if(measurementService == null) {
            // measurementService is required
            return;
        }
        // Update buttons: cancel enabled; record button to stop;
        // Show start measure hint
        TextView overlayMessage = (TextView) findViewById(R.id.textView_message_overlay);

        initComponents();
        if (measurementService.isStoring()) {
            overlayMessage.setVisibility(View.INVISIBLE);
            buttonPause.setEnabled(true);
            buttonRecord.setImageResource(R.drawable.button_record_pressed);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            // Start chronometer
            chronometerWaitingToStart.set(true);

            MeasurementManager measurementManager = new MeasurementManager(this);

            MapFragment mapFragment = getMapControler();
            if(mapFragment != null) {
                List<MeasurementManager.LeqBatch> locations = measurementManager
                        .getRecordLocations(measurementService.getRecordId(), true, MAX_LOCATIONS_RESTORE_MAP, null,
                                MINIMAL_DISTANCE_RESTORE_MAP);
                mapFragment.cleanMeasurementPoints();
                for(MeasurementManager.LeqBatch location : locations) {
                    Storage.Leq leq = location.getLeq();
                    String htmlColor = MeasurementExport.getColorFromLevel
                            (location.computeGlobalLAeq());
                    mapFragment.addMeasurement(new MapFragment.LatLng(leq.getLatitude(), leq
                            .getLongitude()), htmlColor);
                }
            }
        }
        else
        {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            // Enabled/disabled buttons after measurement
            buttonPause.setEnabled(false);
            buttonRecord.setImageResource(R.drawable.button_record);
            buttonRecord.setEnabled(true);
            // Stop and reset chronometer
            Chronometer chronometer = (Chronometer) findViewById(R.id.chronometer_recording_time);
            chronometer.stop();
            overlayMessage.setText(R.string.no_data_text_description);
            overlayMessage.setVisibility(View.VISIBLE);
        }
    }

    private static class DoProcessing implements CompoundButton.OnClickListener,
            PropertyChangeListener {
        private MeasurementActivity activity;

        public DoProcessing(MeasurementActivity activity) {
            this.activity = activity;
        }

        @Override
        public void propertyChange(PropertyChangeEvent event) {
            if(AudioProcess.PROP_FAST_LEQ.equals(event.getPropertyName())) {
                AudioProcess.AudioMeasureResult measure =
                        (AudioProcess.AudioMeasureResult) event.getNewValue();
                // Realtime audio processing
                Spectrogram spectrogram = activity.getSpectrogram();
                if(spectrogram  != null) {
                    spectrogram.addTimeStep(measure.getResult().getFftResult(),
                            activity.measurementService.getAudioProcess().getFFTFreqArrayStep());
                }
                if(activity.isComputingMovingLeq.compareAndSet(false, true) && activity
                        .measurementService.isRecording()) {
                    activity.runOnUiThread(new UpdateText(activity));
                }
                if(activity.measurementService.getAudioProcess().isHannWindowFast() && activity.measurementService.getAudioProcess().getFastNotProcessedMilliseconds() > SWITCH_TO_FAST_RECTANGULAR_DELAY) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putString(MeasurementActivity.SETTINGS_MEASUREMENT_DISPLAY_WINDOW, "RECTANGULAR");
                            editor.apply();
                        }
                    });
                }
            } else if(AudioProcess.PROP_STATE_CHANGED.equals(event.getPropertyName())) {
                if (AudioProcess.STATE.CLOSED.equals(event.getNewValue())) {
                    activity.runOnUiThread(new UpdateText(activity));
                }
            } else if(AudioProcess.PROP_SLOW_LEQ.equals(event.getPropertyName())) {
                if(activity.hasMaximalMeasurementTime && activity.measurementService.isStoring() &&
                        activity.maximalMeasurementTime <= activity.measurementService.getLeqAdded()) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.buttonRecord.performClick();
                        }
                    });
                }
            }
            else if(MeasurementService.PROP_NEW_MEASUREMENT.equals(event.getPropertyName())) {
                if(BuildConfig.DEBUG) {
                    System.out.println("Measure offset "+activity.measurementService.getAudioProcess().getFastNotProcessedMilliseconds()+" ms");
                }
                MapFragment mapFragment = activity.getMapControler();
                if(mapFragment != null) {
                    final MeasurementService.MeasurementEventObject measurement = (MeasurementService.MeasurementEventObject) event.getNewValue();
                    if(!(Double.compare(measurement.leq.getLatitude(), 0) == 0 && Double.compare(measurement.leq.getLongitude(), 0) == 0)) {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String htmlColor = MeasurementExport.getColorFromLevel
                                        (measurement.measure.getGlobaldBaValue());
                                activity.getMapControler().addMeasurement(new MapFragment.LatLng(measurement.leq.getLatitude(), measurement.leq.getLongitude()), htmlColor);
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void onClick(View v) {
            if(activity.measurementService == null) {
                // Service not ready
                return;
            }
            Resources resources = activity.getResources();
            ImageButton buttonPause= (ImageButton) activity.findViewById(R.id.pauseBtn);
            buttonPause.setEnabled(true);
            ImageButton buttonRecord= (ImageButton) activity.findViewById(R.id.recordBtn);

            if (!activity.measurementService.isStoring()) {
                // Start recording
                buttonRecord.setImageResource(R.drawable.button_record_pressed);
                buttonRecord.setEnabled(false);
                activity.measurementService.startStorage();
                // Force service to stay alive even if this activity is killed (Foreground service)
                activity.startService(new Intent(activity, MeasurementService.class));
            } else {
                // Stop measurement
                activity.measurementService.stopRecording();

                // Show computing progress dialog
                ProgressDialog myDialog = new ProgressDialog(activity);
                if (!activity.measurementService.isCanceled()) {
                    myDialog.setMessage(resources.getString(R.string.measurement_processlastsamples,
                            (int)(activity.measurementService.getAudioProcess()
                                    .getRemainingNotProcessTime())));
                    myDialog.setCancelable(false);
                    myDialog.setButton(DialogInterface.BUTTON_NEGATIVE,
                            resources.getText(R.string.text_CANCEL_data_transfer),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    activity.measurementService.cancel();
                                }
                            });
                    myDialog.show();
                }

                // Launch processing end activity
                new Thread(new WaitEndOfProcessing(activity, myDialog)).start();
            }
            activity.initGuiState();
        }
    }

    private final static class UpdateText implements Runnable {
        MeasurementActivity activity;

        private static void formatdBA(double dbAValue, TextView textView) {
            if(dbAValue > MIN_SHOWN_DBA_VALUE && dbAValue < MAX_SHOWN_DBA_VALUE) {
                textView.setText(String.format(Locale.getDefault(), " %.1f", dbAValue));
            } else {
                textView.setText(R.string.no_valid_dba_value);
            }
        }

        private UpdateText(MeasurementActivity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            try {
                if(activity.measurementService.isRecording()) {
                    int seconds = activity.measurementService.getLeqAdded();
                    if(seconds >= MeasurementActivity.DEFAULT_MINIMAL_LEQ && !activity.buttonRecord.isEnabled()) {
                        activity.buttonRecord.setEnabled(true);
                    }
                    Chronometer chronometer = (Chronometer) activity
                            .findViewById(R.id.chronometer_recording_time);
                    if (activity.chronometerWaitingToStart.getAndSet(false)) {
                        chronometer.setBase(SystemClock.elapsedRealtime() - seconds * 1000);
                        TextView overlayMessage = (TextView) activity.findViewById(R.id.textView_message_overlay);
                        if(activity.measurementService.isPaused()) {
                            chronometer.stop();
                            chronometer.startAnimation(AnimationUtils.loadAnimation(activity, R.anim.pause_anim));
                            overlayMessage.setText(R.string.measurement_pause);
                        } else {
                            chronometer.clearAnimation();
                            chronometer.start();
                            overlayMessage.setText("");
                        }
                    }

                    //Update accuracy hint
                    final TextView accuracyText = (TextView) activity.findViewById(R.id.textView_value_gps_precision);
                    final ImageView accuracyImageHint = (ImageView) activity.findViewById(R.id.imageView_value_gps_precision);
                    Location location = activity.measurementService.getLastLocation();
                    if(location != null) {
                        float lastPrecision = location.getAccuracy();
                        if (lastPrecision < APPROXIMATE_LOCATION_ACCURACY) {
                            accuracyImageHint.setImageResource(R.drawable.gps_fixed);
                            accuracyText.setText(activity.getString(R.string.gps_hint_precision,
                                    (int)lastPrecision));
                        } else {
                            accuracyImageHint.setImageResource(R.drawable.gps_not_fixed);
                            accuracyText.setText(activity.getString(R.string.gps_hint_precision,
                                    (int)lastPrecision));
                        }
                        if (accuracyImageHint.getVisibility() == View.INVISIBLE) {
                            accuracyImageHint.setVisibility(View.VISIBLE);
                        }
                        long now = System.currentTimeMillis();
                        if(now - activity.lastMapLocationRefresh >= REFRESH_MAP_LOCATION_RATE) {
                            activity.getMapControler().updateLocationMarker(new MapFragment.LatLng(location.getLatitude(), location.getLongitude()), location.getAccuracy());
                            activity.lastMapLocationRefresh = now;
                        }
                    } else {
                        accuracyImageHint.setImageResource(R.drawable.gps_off);
                        accuracyText.setText(R.string.no_gps_hint);
                    }
                    // Change the text and the textcolor in the corresponding textview
                    // for the Leqi value
                    LeqStats leqStats =
                            activity.measurementService.getFastLeqStats();
                    // Update current location of user
                    double lastLaeqFast = activity.measurementService.getLAeq();
                    activity.setData(lastLaeqFast);
                    final TextView mTextView = activity.findViewById(R.id.textView_value_SL_i);
                    formatdBA(lastLaeqFast, mTextView);
                    if(activity.measurementService.getLeqAdded() != 0) {
                        // Stats are only available if the recording of previous leq are activated
                        final TextView valueMin = (TextView) activity.findViewById(R.id
                                .textView_value_Min_i);
                        formatdBA(leqStats.getLeqMin(), valueMin);
                        final TextView valueMax = (TextView) activity.findViewById(R.id
                                .textView_value_Max_i);
                        formatdBA(leqStats.getLeqMax(), valueMax);
                        final TextView valueMean = (TextView) activity.findViewById(R.id
                                .textView_value_Mean_i);
                        formatdBA(leqStats.getLeqMean(), valueMean);
                    }
                    int nc = MeasurementActivity.getNEcatColors(lastLaeqFast);    // Choose the color category in
                    // function of the sound level
                    mTextView.setTextColor(activity.NE_COLORS[nc]);

                    // Spectrum data
                    activity.updateSpectrumGUI();
                } else {
                    activity.initGuiState();
                }

                // Debug processing time
            } finally {
                activity.isComputingMovingLeq.set(false);
            }
        }

    }

    private MeasurementService measurementService;

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service.  Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            measurementService = ((MeasurementService.LocalBinder)service).getService();

            measurementService.setMinimalLeqCount(MeasurementActivity.DEFAULT_MINIMAL_LEQ);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(MeasurementActivity.this);
            measurementService.setDeletedLeqOnPause(getInteger(sharedPref,MeasurementActivity.DELETE_LEQ_ON_PAUSE_SETTING,
                            MeasurementActivity.DEFAULT_DELETE_LEQ_ON_PAUSE));
            measurementService.setdBGain(
                    getDouble(sharedPref,"settings_recording_gain", 0), getInteger(sharedPref, "settings_calibration_method", 0));
            // Init gui if recording is ongoing
            measurementService.addPropertyChangeListener(doProcessing);

            if(!measurementService.isRecording()) {
                measurementService.startRecording();
            }
            measurementService.getAudioProcess().setDoFastLeq(true);
            measurementService.getAudioProcess().setDoOneSecondLeq(true);
            measurementService.getAudioProcess().setWeightingA(true);
            measurementService.getAudioProcess().setHannWindowOneSecond(true);
            measurementService.getAudioProcess().setHannWindowFast(sharedPref.getString(SETTINGS_MEASUREMENT_DISPLAY_WINDOW, "RECTANGULAR").equals("HANN"));
            initGuiState();
        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            measurementService.removePropertyChangeListener(doProcessing);
            measurementService = null;
        }
    };

    void doBindService() {
        // Establish a connection with the service.  We use an explicit
        // class name because we want a specific service implementation that
        // we know will be running in our own process (and thus won't be
        // supporting component replacement by other applications).
        if(!bindService(new Intent(this, MeasurementService.class), mConnection,
                Context.BIND_AUTO_CREATE)) {
            Toast.makeText(MeasurementActivity.this, R.string.measurement_service_disconnected,
                    Toast.LENGTH_SHORT).show();
        } else {
            mIsBound = true;
        }
    }

    void doUnbindService() {
        if (mIsBound && measurementService != null) {
            measurementService.removePropertyChangeListener(doProcessing);
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        doBindService();
        if(measurementService != null) {
            initGuiState();
            measurementService.getAudioProcess().setDoFastLeq(true);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(measurementService != null) {
            // Disconnect listener from measurement
            if(measurementService.isStoring()) {
                // Disable 125ms processing as it is only used for display
                measurementService.getAudioProcess().setDoFastLeq(false);
            }
            doUnbindService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doUnbindService();
    }
}

