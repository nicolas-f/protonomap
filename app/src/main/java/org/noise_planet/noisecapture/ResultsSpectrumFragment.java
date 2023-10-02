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

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarDataSet;
import com.github.mikephil.charting.utils.ViewPortHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;


/**
 * Spectrum content on results activity
 */
public class ResultsSpectrumFragment extends Fragment {

    private View view;
    BarChart sChart;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if(view == null) {
            // Inflate the layout for this fragment
            view = inflater.inflate(R.layout.fragment_result_spectrum, container, false);
            sChart = view.findViewById(R.id.spectrumChart);
            initSpectrumChart(sChart);
            Legend ls = sChart.getLegend();
            ls.setEnabled(false); // Hide legend
        }
        return view;
    }

    public void setDataS(List<Float> splHistogram, String[] ltob) {

        ArrayList<String> xVals = new ArrayList<String>();
        Collections.addAll(xVals, ltob);


        ArrayList<BarEntry> yVals1 = new ArrayList<BarEntry>();

        for (int i = 0; i < splHistogram.size(); i++) {
            yVals1.add(new BarEntry(splHistogram.get(i), i));
        }

        BarDataSet set1 = new BarDataSet(yVals1, "DataSet");
        set1.setValueTextColor(Color.WHITE);

        set1.setColors(
                new int[]{Color.rgb(0, 128, 255), Color.rgb(0, 128, 255), Color.rgb(0, 128, 255),
                        Color.rgb(102, 178, 255), Color.rgb(102, 178, 255),
                        Color.rgb(102, 178, 255)});

        ArrayList<IBarDataSet> dataSets = new ArrayList<IBarDataSet>();
        dataSets.add(set1);

        BarData data = new BarData(xVals, dataSets);
        data.setValueTextSize(10f);
        data.setValueFormatter(new FreqValueFormatter(sChart));
        sChart.setData(data);
        sChart.invalidate();
    }


    public void initSpectrumChart(BarChart sChart){
        sChart.setPinchZoom(false);
        sChart.setDoubleTapToZoomEnabled(false);
        sChart.setDrawBarShadow(false);
        sChart.setDescription("");
        sChart.setPinchZoom(false);
        sChart.setDrawGridBackground(false);
        sChart.setHighlightPerTapEnabled(true);
        sChart.setHighlightPerDragEnabled(false);
        sChart.setDrawHighlightArrow(true);
        sChart.setDrawValueAboveBar(true);
        // XAxis parameters: hide all
        XAxis xls = sChart.getXAxis();
        xls.setPosition(XAxis.XAxisPosition.BOTTOM);
        xls.setDrawAxisLine(true);
        xls.setDrawGridLines(false);
        xls.setLabelRotationAngle(-90);
        xls.setDrawLabels(true);
        xls.setTextColor(Color.WHITE);
        xls.setLabelsToSkip(0);
        // YAxis parameters (left): main axis for dB values representation
        YAxis yls = sChart.getAxisLeft();
        yls.setDrawAxisLine(true);
        yls.setDrawGridLines(true);
        yls.setAxisMaxValue(120.f);
        yls.setAxisMinValue(20);
        yls.setTextColor(Color.WHITE);
        yls.setGridColor(Color.WHITE);
        // YAxis parameters (right): no axis, hide all
        YAxis yrs = sChart.getAxisRight();
        yrs.setEnabled(false);
        //return true;
    }



    private static final class FreqValueFormatter implements ValueFormatter {
        private BarChart sChart;

        public FreqValueFormatter(BarChart sChart) {
            this.sChart = sChart;
        }

        @Override
        public String getFormattedValue(float value, Entry entry, int dataSetIndex, ViewPortHandler viewPortHandler) {
            if(!sChart.valuesToHighlight()) {
                return "";
            }
            Highlight[] highlights = sChart.getHighlighted();
            for (final Highlight highlight : highlights) {
                int xIndex = highlight.getXIndex();
                if (xIndex == entry.getXIndex()) {
                    return String.format(Locale.getDefault(), "%.1f dB", value);
                }
            }
            return "";
        }
    }

}
