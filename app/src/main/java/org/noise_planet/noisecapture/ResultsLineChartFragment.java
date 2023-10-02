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

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import org.orbisgis.sos.AcousticIndicators;
import org.orbisgis.sos.LeqStats;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;


/**
 * NoiseLevel over time on result activity
 */
public class ResultsLineChartFragment extends Fragment {

    private static final int MAX_VALUES_Y_DISPLAY = 30;
    private View view;
    private LineChart timeLevelChart;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(view == null) {
            // Inflate the layout for this fragment
            view = inflater.inflate(R.layout.fragment_result_noisetime, container, false);
            timeLevelChart = view.findViewById(R.id.timeLineChart);
            initLevelChart();
        }
        return view;
    }

    public LineChart getTimeLevelChart() {
        return timeLevelChart;
    }

    public void initLevelChart() {
        Legend l = timeLevelChart.getLegend();
        l.setTextColor(Color.WHITE);
        timeLevelChart.setScaleEnabled(false);
        timeLevelChart.disableScroll();
        timeLevelChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        timeLevelChart.setHighlightPerDragEnabled(false);
        timeLevelChart.setHighlightPerTapEnabled(false);
        timeLevelChart.setDescription(getString(R.string.result_linechart_description));
        timeLevelChart.setDescriptionColor(Color.WHITE);
        timeLevelChart.setDescriptionTextSize(12);
        XAxis xAxis = timeLevelChart.getXAxis();
        xAxis.setTextColor(Color.WHITE);
        xAxis.setSpaceBetweenLabels(" 9:99:99".length());
        YAxis yAxis = timeLevelChart.getAxisLeft();
        yAxis.setTextColor(Color.WHITE);
        yAxis.setAxisMinValue(20);
        yAxis.setAxisMaxValue(120);
        timeLevelChart.getAxisRight().setEnabled(false);
    }

    public void setTimeLevelData(LeqStats.LeqOccurrences leqOccurrences, List<Integer> timeData, List<Float> splData) {
        LineData lineData = new LineData();
        lineData.setDrawValues(true);
        List<Entry> entries = new ArrayList<>(timeData.size());
        List<String> xVals = new ArrayList<>(timeData.size());
        SimpleDateFormat dateFormatHoursLength =
                new SimpleDateFormat("HH'h'mm'm'ss's'", Locale.getDefault());
        dateFormatHoursLength.setTimeZone(TimeZone.getTimeZone("UTC"));
        SimpleDateFormat dateFormatMinutesLength =
                new SimpleDateFormat("mm'm'ss's'", Locale.getDefault());
        SimpleDateFormat dateFormatSecondsLength =
                new SimpleDateFormat("ss's'", Locale.getDefault());
        for(int i=0; i < timeData.size(); i++) {
            float currentLevel = splData.get(i);
            entries.add(new Entry(currentLevel, i));
            int secondsElapsed = timeData.get(i);
            Date date = new Date(secondsElapsed * 1000);
            String labelString;
            if(secondsElapsed < 60) {
                labelString = dateFormatSecondsLength.format(date);
            } else if(secondsElapsed < 3600) {
                labelString = dateFormatMinutesLength.format(date);
            } else {
                labelString = dateFormatHoursLength.format(date);
            }
            xVals.add(labelString);
        }
        LineDataSet laeqDataset = new LineDataSet(entries, getString(R.string.result_laeq));
        laeqDataset.setDrawCircles(false);
        laeqDataset.setDrawValues(true);
        laeqDataset.setColor(Color.WHITE);
        Drawable drawable = ContextCompat.getDrawable(view.getContext(), R.drawable.fade_linegraph);
        laeqDataset.setFillDrawable(drawable);
        laeqDataset.setDrawFilled(true);
        // Statistical lines
        double la10 = leqOccurrences.getLa10();
        LineDataSet la10LineDataSet = new LineDataSet(Arrays.asList(new Entry( (float)la10, 0),
                new Entry((float)la10,laeqDataset.getEntryCount() - 1)),
                getString(R.string.measurement_dba_la10));
        la10LineDataSet.setDrawCircles(false);
        la10LineDataSet.setColor(Color.RED);
        la10LineDataSet.setDrawValues(false);
        double la90 = leqOccurrences.getLa90();
        LineDataSet la90LineDataSet = new LineDataSet(Arrays.asList(new Entry( (float)la90, 0),
                new Entry((float)la90,laeqDataset.getEntryCount() - 1)),
                getString(R.string.measurement_dba_la90));
        la90LineDataSet.setDrawCircles(false);
        la90LineDataSet.setColor(Color.GREEN);
        la90LineDataSet.setDrawValues(false);

        lineData.addDataSet(la90LineDataSet);
        lineData.addDataSet(laeqDataset);
        lineData.addDataSet(la10LineDataSet);

        lineData.setXVals(xVals);
        lineData.setValueTextColor(Color.WHITE);
        timeLevelChart.setData(lineData);
        timeLevelChart.setMaxVisibleValueCount(MAX_VALUES_Y_DISPLAY);
        timeLevelChart.invalidate();
    }
}
