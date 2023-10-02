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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.Legend.LegendPosition;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.XAxis.XAxisPosition;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.formatter.PercentFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.formatter.YAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.utils.ViewPortHandler;
import com.google.android.material.tabs.TabLayout;
import com.nhaarman.supertooltips.ToolTip;
import com.nhaarman.supertooltips.ToolTipRelativeLayout;
import com.nhaarman.supertooltips.ToolTipView;

import org.noise_planet.noisecapture.util.CustomPercentFormatter;
import org.orbisgis.sos.LeqStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

public class Results extends MainActivity {
    private static final Logger LOGGER = LoggerFactory.getLogger(Results.class);
    // 1s measurements will be aggregated if there is more than this number of seconds
    public static final int MAXIMUM_X_VALUES_TIME_NOISE_LEVEL = 300;
    private MeasurementManager measurementManager;
    private Storage.Record record;
    private static final double[][] CLASS_RANGES = new double[][]{{Double.MIN_VALUE, 45}, {45, 55},
            {55, 65}, {65, 75},{75, Double.MAX_VALUE}};

    private ToolTipRelativeLayout toolTip;
    private ToolTipView lastShownTooltip = null;


    // For the Charts
    public PieChart rneChart;
    public PieChart neiChart;
    public ViewPagerExt viewPager;

    // Other resources
    private String[] catNE; // List of noise level category (defined as resources)
    //private LeqStats leqStats = new LeqStats();
    private List<String> tags;

    private ResultsLineChartFragment resultsLineChartFragment;
    private ResultsSpectrumFragment resultsSpectrumFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean showTooltip = sharedPref.getBoolean("settings_tooltip", true);
        setContentView(R.layout.activity_results);
        viewPager = findViewById(R.id.result_viewpager);
        setupViewPager(viewPager);
        TabLayout tabLayout = findViewById(R.id.result_tabs);
        tabLayout.setupWithViewPager(viewPager);

        this.measurementManager = new MeasurementManager(this);
        Intent intent = getIntent();
        if(intent != null && intent.hasExtra(RESULTS_RECORD_ID)) {
            record = measurementManager.getRecord(intent.getIntExtra(RESULTS_RECORD_ID, -1));
        } else {
            // Read the last stored record
            List<Storage.Record> recordList = measurementManager.getRecords();
            if(!recordList.isEmpty()) {
                record = recordList.get(0);
            } else {
                // Message for starting a record
                Toast.makeText(getApplicationContext(),
                        getString(R.string.no_results), Toast.LENGTH_LONG).show();
                initDrawer();
                return;
            }
        }
        tags = measurementManager.getTags(record.getId());
        initDrawer(record.getId());
        toolTip = (ToolTipRelativeLayout) findViewById(R.id.activity_tooltip);

        // RNE PieChart
        rneChart = (PieChart) findViewById(R.id.RNEChart);
        initRNEChart();
        Legend lrne = rneChart.getLegend();
        lrne.setTextColor(Color.WHITE);
        lrne.setTextSize(8f);
        lrne.setPosition(LegendPosition.RIGHT_OF_CHART_CENTER);
        lrne.setEnabled(true);

        // NEI PieChart
        neiChart = (PieChart) findViewById(R.id.NEIChart);
        initNEIChart();

        Legend lnei = neiChart.getLegend();
        lnei.setEnabled(false);

        // Other fields
        TextView minText = (TextView) findViewById(R.id.textView_value_Min_SL);
        minText.setText(R.string.no_valid_dba_value);
        if(showTooltip) {
            ToolTipListener touchListener = new ToolTipListener(this, R.string
                    .result_tooltip_minsl);
            minText.setOnTouchListener(touchListener);
            findViewById(R.id.textView_label_Min_SL).setOnTouchListener(touchListener);
        }

        TextView maxText = (TextView) findViewById(R.id.textView_value_Max_SL);
        maxText.setText(R.string.no_valid_dba_value);
        if(showTooltip) {
            ToolTipListener touchListener = new ToolTipListener(this, R.string
                    .result_tooltip_maxsl);
            maxText.setOnTouchListener(touchListener);
            findViewById(R.id.textView_label_Max_SL).setOnTouchListener(touchListener);
        }

        TextView la10Text = (TextView) findViewById(R.id.textView_value_LA10);
        la10Text.setText(R.string.no_valid_dba_value);
        if(showTooltip) {
            ToolTipListener touchListener = new ToolTipListener(this, R.string.result_tooltip_la10);
            la10Text.setOnTouchListener(touchListener);
            findViewById(R.id.textView_label_la10_SL).setOnTouchListener(touchListener);
        }

        TextView la50Text = (TextView) findViewById(R.id.textView_value_LA50);
        la50Text.setText(R.string.no_valid_dba_value);
        if(showTooltip) {
            ToolTipListener touchListener = new ToolTipListener(this, R.string.result_tooltip_la50);
            la50Text.setOnTouchListener(touchListener);
            findViewById(R.id.textView_label_la50_SL).setOnTouchListener(touchListener);
        }

        TextView la90Text = (TextView) findViewById(R.id.textView_value_LA90);
        la90Text.setText(R.string.no_valid_dba_value);
        if(showTooltip) {
            ToolTipListener touchListener = new ToolTipListener(this, R.string.result_tooltip_la90);
            la90Text.setOnTouchListener(touchListener);
            findViewById(R.id.textView_label_la90_SL).setOnTouchListener(touchListener);
        }

        // Action on Map button
        Button buttonMap=(Button)findViewById(R.id.mapBtn);
        buttonMap.setEnabled(true);
        buttonMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Go to map page
                Intent a = new Intent(getApplicationContext(), MapActivity.class);
                a.putExtra(RESULTS_RECORD_ID, record.getId());
                startActivity(a);
                finish();
            }
        });
        View measureButton = findViewById(R.id.measureBtn);
        measureButton.setOnClickListener(new OnGoToMeasurePage(this));
        // Action on export button

        Button exportComment=(Button)findViewById(R.id.uploadBtn);
        exportComment.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runOnUiThread(new SendResults(Results.this, record.getId()));
            }
        });

        exportComment.setEnabled(record.getUploadId().isEmpty());


        AsyncTask.execute(new LoadMeasurements(this));
    }

    private void setupViewPager(ViewPagerExt viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());
        resultsLineChartFragment = new ResultsLineChartFragment();
        resultsSpectrumFragment = new ResultsSpectrumFragment();
        adapter.addFragment(resultsLineChartFragment, getString(R.string.result_tab_linechart));
        adapter.addFragment(resultsSpectrumFragment, getString(R.string.measurement_tab_spectrum));
        viewPager.setAdapter(adapter);
    }

    private static final class OnGoToMeasurePage implements View.OnClickListener {
        private Results activity;

        public OnGoToMeasurePage(Results activity) {
            this.activity = activity;
        }

        @Override
        public void onClick(View v) {
            //Open result page
            Intent ir = new Intent(activity, MeasurementActivity.class);
            activity.startActivity(ir);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Transfer results automatically (with all checking)
        checkTransferResults();
    }

    protected void onTransferRecord() {
        // Nothing to do
        // Change upload state
        Button exportComment=(Button)findViewById(R.id.uploadBtn);
        // Refresh record
        record = measurementManager.getRecord(record.getId());
        exportComment.setEnabled(record.getUploadId().isEmpty());
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(record == null) {
            return false;
        }
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_result, menu);


        // Locate MenuItem with ShareActionProvider
        MenuItem item = menu.findItem(R.id.menu_item_share);

        // Fetch and store ShareActionProvider
        ShareActionProvider mShareActionProvider = (ShareActionProvider) MenuItemCompat.getActionProvider(item);

        Map<String, Storage.TagInfo> tagToIndex = new HashMap<>(Storage.TAGS_INFO.length);
        for(Storage.TagInfo sysTag : Storage.TAGS_INFO) {
            tagToIndex.put(sysTag.name, sysTag);
        }

        StringBuilder hashtags = new StringBuilder();
        String[] localeStringArray = getResources().getStringArray(R.array.tags);
        for(String enTag : tags) {
            Storage.TagInfo tagInfo = tagToIndex.get(enTag);
            if (tagInfo != null && tagInfo.id < localeStringArray.length) {
                if(hashtags.length() > 0 ) {
                    hashtags.append(",");
                }
                hashtags.append(localeStringArray[tagInfo.id].replace(" ",""));
            }
        }
        if(record.getNoisePartyTag() != null && !record.getNoisePartyTag().isEmpty()) {
            if(hashtags.length() > 0 ) {
                hashtags.append(",");
            }
            hashtags.append(record.getNoisePartyTag());
        }
        //@see https://dev.twitter.com/web/tweet-button/web-intent
        // Compute position of this measurement
        double[] coordinates = measurementManager.getRecordCenterPosition(record.getId(), 15);
        String append = "";
        if(coordinates != null) {
            append += "&url=" + Uri.encode("http://noise-planet.org/map_noisecapture/index" +
                    ".html#18/"+String.format(Locale.ROOT, "%.5f",
                    coordinates[0]) +"/" + String.format(Locale.ROOT, "%.5f",coordinates[1])) + "/";
        }
        String url = "https://www.twitter.com/intent/tweet?via=Noise_Planet&hashtags="+hashtags.toString() +
                "&text=" + Uri.encode(getString(R.string.share_message, record.getLeqMean())) +
                append;
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        if (mShareActionProvider == null) {
            mShareActionProvider = new ShareActionProvider(this);
            MenuItemCompat.setActionProvider(item, mShareActionProvider);
        }
        mShareActionProvider.setShareIntent(i);
        // Return true to display menu
        return true;
    }






    // Init RNE Pie Chart
    public void initRNEChart(){
        rneChart.setUsePercentValues(true);
        rneChart.setHoleColor(Color.TRANSPARENT);
        rneChart.setHoleRadius(40f);
        rneChart.setDescription("");
        rneChart.setDrawCenterText(true);
        rneChart.setDrawHoleEnabled(true);
        rneChart.setRotationAngle(0);
        rneChart.setRotationEnabled(true);
        rneChart.setDrawSliceText(false);
        rneChart.setCenterText("RNE");
        rneChart.setCenterTextColor(Color.WHITE);
    }


    // Set computed data in chart
    private void setRNEData(List<Double> classRangeValue) {
        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        catNE= getResources().getStringArray(R.array.catNE_list_array);
        ArrayList<String> xVals = new ArrayList<String>();
        double maxValue = 0;
        int maxClassId = 0;
        for (int idEntry = 0; idEntry < classRangeValue.size(); idEntry++) {
            float value = classRangeValue.get(classRangeValue.size() - 1 - idEntry).floatValue();
            // Fix background color issue if the pie is too thin
            if(value < 0.01) {
                value = 0;
            }
            yVals1.add(new Entry(value, idEntry));
            xVals.add(catNE[idEntry]);
            if (value > maxValue) {
                maxClassId = idEntry;
                maxValue = value;
            }
        }

        PieDataSet dataSet = new PieDataSet(yVals1,Results.this.getString(R.string.caption_SL));
        dataSet.setSliceSpace(3f);
        dataSet.setColors(NE_COLORS);

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new CustomPercentFormatter());
        data.setValueTextSize(8f);
        data.setValueTextColor(Color.BLACK);
        rneChart.setData(data);

        // highlight the maximum value of the RNE
        // Find the maximum of the array, in order to be highlighted
        Highlight h = new Highlight(maxClassId, 0);
        rneChart.highlightValues(new Highlight[] { h });
        rneChart.invalidate();
    }

    public void initNEIChart() {
        // NEI PieChart
        neiChart.setUsePercentValues(true);
        neiChart.setHoleColor(Color.TRANSPARENT);
        neiChart.setHoleRadius(75f);
        neiChart.setDescription("");
        neiChart.setDrawCenterText(true);
        neiChart.setDrawHoleEnabled(true);
        neiChart.setRotationAngle(90);
        neiChart.setRotationEnabled(true);
        neiChart.setDrawSliceText(false);
        neiChart.setTouchEnabled(false);
        neiChart.setCenterTextSize(15);
        neiChart.setCenterTextColor(Color.WHITE);
        //return true;
    }

    private void setNEIData() {

        ArrayList<Entry> yVals1 = new ArrayList<Entry>();

        // IMPORTANT: In a PieChart, no values (Entry) should have the same
        // xIndex (even if from different DataSets), since no values can be
        // drawn above each other.
        yVals1.add(new Entry( record.getLeqMean(), 0));

        ArrayList<String> xVals = new ArrayList<String>();

        xVals.add(catNE[0 % catNE.length]);

        PieDataSet dataSet = new PieDataSet(yVals1, "NEI");
        dataSet.setSliceSpace(3f);
        int nc=getNEcatColors(record.getLeqMean());    // Choose the color category in function of the sound level
        dataSet.setColor(NE_COLORS[nc]);   // Apply color category for the corresponding sound level

        PieData data = new PieData(xVals, dataSet);
        data.setValueFormatter(new PercentFormatter());
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);
        data.setDrawValues(false);

        neiChart.setData(data);
        neiChart.setCenterText(String.format(Locale.getDefault(), "%.1f", record.getLeqMean())
                .concat(" dBA"));
        neiChart.invalidate();
    }


    private static class ToolTipListener implements View.OnTouchListener {
        private Results results;
        private int resId;

        public ToolTipListener(Results results, final int resId) {
            this.results = results;
            this.resId = resId;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if(results.lastShownTooltip != null && results.lastShownTooltip.isShown()) {
                // Hide last shown tooltip
                results.lastShownTooltip.remove();
            }
            if(v != null) {
                ToolTip toolTipMinSL = new ToolTip()
                        .withText(resId)
                        .withColor(Color.DKGRAY)
                        .withAnimationType(ToolTip.AnimationType.NONE)
                        .withShadow();
                results.lastShownTooltip = results.toolTip.showToolTipForView(toolTipMinSL, v);
            }
            return false;
        }
    }



    private static final class ReadRecordsProgression implements MeasurementManager
            .ProgressionCallBack, View.OnClickListener {
        private AppCompatActivity activity;
        private long beginReadRecords = 0;
        AtomicBoolean canceled = new AtomicBoolean(false);
        int recordCount = 0;
        int record = 0;
        int lastProgress = 0;
        boolean handleProgression = false;
        private static final int MINIMAL_RECORD_DISPLAY_PROGRESS = 100;
        View progressView;
        ProgressBar progressBar;
        Button button;

        public ReadRecordsProgression(AppCompatActivity activity) {
            this.activity = activity;
            progressView = activity.findViewById(R.id
                    .result_progress_layout);
            progressBar = (ProgressBar) activity.findViewById(R.id
                    .result_progress_control);
            button = (Button)activity.findViewById(R.id
                    .result_progress_cancel);
            button.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            canceled.set(true);
        }

        @Override
        public void onCreateCursor(int recordCount) {
            this.recordCount = recordCount;
            beginReadRecords = System.currentTimeMillis();
            if(recordCount > MINIMAL_RECORD_DISPLAY_PROGRESS) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setProgress(0);
                        progressView.setVisibility(View.VISIBLE);
                    }
                });
                handleProgression = true;
            }
        }

        @Override
        public boolean onCursorNext() {
            if(handleProgression) {
                record++;
                final int newProgression = (int)((record / (double) recordCount) * 100);
                if(newProgression / 5 != lastProgress / 5) {
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                progressBar.setProgress(newProgression, true);
                            } else {
                                progressBar.setProgress(newProgression);
                            }
                        }
                    });
                    lastProgress = newProgression;
                }
            }
            return !canceled.get();
        }

        @Override
        public void onDeleteCursor() {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressView.setVisibility(View.GONE);
                }
            });
            if(BuildConfig.DEBUG) {
                System.out.println("Fetch measurement time "+(System.currentTimeMillis() - beginReadRecords)+" " +
                        "ms");
            }
        }
    }
    private static class LoadMeasurements implements Runnable {
        private Results activity;

        LoadMeasurements(Results activity) {
            this.activity = activity;
        }

        @Override
        public void run() {

            final OneSecondLeqVisitor leqVisitor = new OneSecondLeqVisitor();

            // Query 1s database without storing all values in memory
            activity.measurementManager.getRecordLocations(activity.record.getId(), leqVisitor);

            final List<Float> splHistogram = new ArrayList<>(leqVisitor.leqStatsByFreq.length);
            // List of third-octave bands
            final String[] ltob = new String[leqVisitor.leqStatsByFreq.length];
            int idFreq = 0;
            for (LeqStats aLeqStatsByFreq : leqVisitor.leqStatsByFreq) {
                ltob[idFreq] = Spectrogram.formatFrequency((int)AudioProcess.realTimeCenterFrequency[idFreq]);
                splHistogram.add((float) aLeqStatsByFreq.getLeqMean());
                idFreq++;
            }

            final LeqStats.LeqOccurrences leqOccurrences = leqVisitor.leqStats.computeLeqOccurrences
                    (CLASS_RANGES);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activity.setRNEData(leqOccurrences.getUserDefinedOccurrences());
                    activity.setNEIData();
                    activity.resultsLineChartFragment.setTimeLevelData(leqOccurrences,
                            leqVisitor.splSecond, leqVisitor.splValues);
                    activity.resultsSpectrumFragment.setDataS(splHistogram, ltob);

                    TextView minText = (TextView) activity.findViewById(R.id.textView_value_Min_SL);
                    minText.setText(String.format(Locale.getDefault(), "%.01f", leqVisitor.leqStats
                            .getLeqMin()));

                    activity.findViewById(R.id.textView_color_Min_SL).setBackgroundColor(activity
                            .NE_COLORS[getNEcatColors(leqVisitor.leqStats.getLeqMin())]);

                    TextView maxText = (TextView) activity.findViewById(R.id.textView_value_Max_SL);
                    maxText.setText(String.format(Locale.getDefault(), "%.01f", leqVisitor.leqStats
                            .getLeqMax()));

                    activity.findViewById(R.id.textView_color_Max_SL)
                            .setBackgroundColor(activity.NE_COLORS[getNEcatColors(leqVisitor.leqStats.getLeqMax())]);

                    TextView la10Text = (TextView) activity.findViewById(R.id.textView_value_LA10);
                    la10Text.setText(String.format(Locale.getDefault(), "%.01f", leqOccurrences.getLa10()));

                    activity.findViewById(R.id.textView_color_LA10)
                            .setBackgroundColor(activity.NE_COLORS[getNEcatColors(leqOccurrences.getLa10())]);

                    TextView la50Text = (TextView) activity.findViewById(R.id.textView_value_LA50);
                    la50Text.setText(String.format(Locale.getDefault(), "%.01f", leqOccurrences.getLa50()));

                    activity.findViewById(R.id.textView_color_LA50)
                            .setBackgroundColor(activity.NE_COLORS[getNEcatColors(leqOccurrences.getLa50())]);

                    TextView la90Text = (TextView) activity.findViewById(R.id.textView_value_LA90);
                    la90Text.setText(String.format(Locale.getDefault(), "%.01f", leqOccurrences.getLa90()));

                    activity.findViewById(R.id.textView_color_LA90)
                            .setBackgroundColor(activity.NE_COLORS[getNEcatColors(leqOccurrences.getLa90())]);

                    if(activity.resultsLineChartFragment.getTimeLevelChart() != null) {
                        activity.resultsLineChartFragment.getTimeLevelChart().animateX(1500);
                    }
                    if(activity.neiChart != null) {
                        activity.neiChart.animateXY(1500, 1500);
                    }
                    if(activity.rneChart != null) {
                        activity.rneChart.animateXY(1500, 1500);
                    }
                }
            });

        }
    }
    private static class OneSecondLeqVisitor implements MeasurementManager.RecordVisitor<MeasurementManager.LeqBatch> {
        private LeqStats leqStats = new LeqStats();
        private LeqStats[] leqStatsByFreq = new LeqStats[AudioProcess.realTimeCenterFrequency.length];
        private List<Float> splHistogram = new ArrayList<>(AudioProcess.realTimeCenterFrequency.length);
        private List<Integer> splSecond = new ArrayList<>(Results.MAXIMUM_X_VALUES_TIME_NOISE_LEVEL);
        private List<Float> splValues = new ArrayList<>(Results.MAXIMUM_X_VALUES_TIME_NOISE_LEVEL);
        private int step;
        private long firstUTC = 0;

        public OneSecondLeqVisitor() {
            for(int idFreq = 0; idFreq < leqStatsByFreq.length; idFreq++) {
                leqStatsByFreq[idFreq] = new LeqStats();
            }
        }

        @Override
        public void onCreateCursor(int recordCount) {
            if(recordCount < Results.MAXIMUM_X_VALUES_TIME_NOISE_LEVEL) {
                step = 1;
            } else {
                step = (int)Math.ceil(recordCount /
                        (double)Results.MAXIMUM_X_VALUES_TIME_NOISE_LEVEL);
            }
        }

        @Override
        public boolean next(MeasurementManager.LeqBatch record) {
            leqStats.addLeq(record.computeGlobalLAeq());
            int idFreq = 0;
            for(Storage.LeqValue leqValue : record.getLeqValues()) {
                leqStatsByFreq[idFreq].addLeq(leqValue.getSpl());
                idFreq++;
            }
            if(firstUTC == 0) {
                firstUTC = record.getLeq().getLeqUtc();
            }
            long elapsedTime = (record.getLeq().getLeqUtc() - firstUTC) / 1000 + 1;
            if(elapsedTime % step == 0) {
                splSecond.add((int)elapsedTime);
                splValues.add((float)record.computeGlobalLAeq());
            }
            return true;
        }
    }
}
