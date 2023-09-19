package org.orbisgis.sos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SpectrumChannel {
    private int subsamplingRatio;
    private int minimum_samples_length;
    private List<BiquadFilter> subSamplers = new ArrayList<>();
    // Cascaded filters are placed here, each element will take less and less samples as input
    private List<HashMap<Integer, BiquadFilter>> iirFilters = new ArrayList<>();
    private DigitalFilter aWeightingFilter = null;
    private DigitalFilter cWeightingFilter = null;
    private int bandFilterSize = 0;
    private List<Double> nominalFrequency = new ArrayList<>();

    public SpectrumChannel() {

    }

    private double[] toArray(List<Double> array) {
        double[] retvalue = new double[array.size()];
        for(int i=0; i<retvalue.length; i++) {
            retvalue[i] = array.get(i);
        }
        return retvalue;
    }

    /**
     * Load configuration generated by filterdesign.py
     * @param configuration Filters configuration
     * @param useCascade Reduce computation time by subsampling the audio according to filter frequency range
     */
    public void loadConfiguration(ConfigurationSpectrumChannel configuration, boolean useCascade) {
        subSamplers.clear();
        bandFilterSize = 0;
        aWeightingFilter = null;
        cWeightingFilter = null;
        iirFilters.clear();
        subsamplingRatio = 0;
        nominalFrequency.clear();
        if(!configuration.getBandpass().isEmpty()) {
            int maxSubsampling = 0;
            if(useCascade) {
                for (ConfigurationBiquad biquad : configuration.getBandpass()) {
                    maxSubsampling = Math.max(maxSubsampling, biquad.getSubsamplingDepth());
                }
            }
            iirFilters = new ArrayList<>();
            subsamplingRatio = configuration.getAntiAliasing().getSampleRatio();
            minimum_samples_length = (int)Math.pow(subsamplingRatio, maxSubsampling);
            ConfigurationSos filterConf = configuration.getAntiAliasing();
            for(int i = 0; i < maxSubsampling; i++) {
                BiquadFilter filter = new BiquadFilter(toArray(filterConf.getB0()),
                        toArray(filterConf.getB1()),
                        toArray(filterConf.getB2()),
                        toArray(filterConf.getA1()),
                        toArray(filterConf.getA2()));
                subSamplers.add(filter);
            }
            // init cascaded filter storage
            for(int i=0; i <= maxSubsampling; i++) {
                iirFilters.add(new HashMap<Integer, BiquadFilter>());
            }
            // fill cascaded filter instances
            for (int i = 0; i < configuration.getBandpass().size(); i++) {
                bandFilterSize += 1;
                ConfigurationBiquad biquad = configuration.getBandpass().get(i);
                nominalFrequency.add(biquad.getNominalFrequency());
                ConfigurationSos refFilter;
                if(useCascade) {
                    refFilter = biquad.getSubsamplingFilter().getSos();
                } else {
                    refFilter = biquad.getSos();
                }
                BiquadFilter filter = new BiquadFilter(toArray(refFilter.getB0()),
                        toArray(refFilter.getB1()),
                        toArray(refFilter.getB2()),
                        toArray(refFilter.getA1()),
                        toArray(refFilter.getA2()));
                if(useCascade) {
                    iirFilters.get(biquad.getSubsamplingDepth()).put(i, filter);
                } else {
                    iirFilters.get(0).put(i, filter);
                }
            }
            if(configuration.getAWeighting() != null) {
                aWeightingFilter = new DigitalFilter(
                        toArray(configuration.getAWeighting().getFilterNumerator()),
                        toArray(configuration.getAWeighting().getFilterDenominator()));
            }
            if(configuration.getCWeighting() != null) {
                cWeightingFilter = new DigitalFilter(
                        toArray(configuration.getCWeighting().getFilterNumerator()),
                        toArray(configuration.getCWeighting().getFilterDenominator()));
            }
        }
    }

    /**
     * @return Nominal frequency for printing results of columns of {@link #processSamples(float[])}
     */
    public List<Double> getNominalFrequency() {
        return Collections.unmodifiableList(nominalFrequency);
    }

    public double processSamplesWeightA(float[] samples) {
        if(aWeightingFilter != null) {
            return aWeightingFilter.filterLeq(samples);
        } else {
            throw new IllegalStateException("A weighting filter not configured");
        }
    }

    public double processSamplesWeightC(float[] samples) {
        if(cWeightingFilter != null) {
            return cWeightingFilter.filterLeq(samples);
        } else {
            throw new IllegalStateException("C weighting filter not configured");
        }
    }

    public double[] processSamples(float[] samples) {
        if(samples.length % minimum_samples_length != 0) {
            throw new IllegalArgumentException(String.format("Provided samples len should be a" +
                    " factor of %d samples", minimum_samples_length));
        }
        if(iirFilters.isEmpty()) {
            throw new IllegalStateException("Loaded configuration does not contain bandpass" +
                    " filters");
        }
        float[] lastFilterSamples = samples;
        double[] leqs = new double[bandFilterSize];
        ExecutorService executorService = Executors.newCachedThreadPool();
        Map<Integer, Future<Double>> threads = new HashMap<>();
        for (int cascadeIndex=0; cascadeIndex < iirFilters.size(); cascadeIndex++) {
            HashMap<Integer, BiquadFilter> cascadeFilters = iirFilters.get(cascadeIndex);
            for (Map.Entry<Integer, BiquadFilter> filterEntry : cascadeFilters.entrySet()) {
                threads.put(filterEntry.getKey(),executorService.submit(
                        new BandAnalysis(filterEntry.getValue(), lastFilterSamples)));
            }
            for (Map.Entry<Integer, Future<Double>> threadEntry : threads.entrySet()) {
                try {
                    leqs[threadEntry.getKey()] = threadEntry.getValue().get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new IllegalStateException("Interrupted exception");
                }
            }
            // subsampling for next iteration
            if(cascadeIndex < subSamplers.size()) {
                float[] nextFilterSamples = new float[lastFilterSamples.length/ subsamplingRatio];
                subSamplers.get(cascadeIndex).filterSlice(lastFilterSamples, nextFilterSamples,
                        subsamplingRatio);
                lastFilterSamples = nextFilterSamples;
            }
        }


        return leqs;
    }

    private static class BandAnalysis implements Callable<Double> {
        BiquadFilter filter;
        float[] samples;

        public BandAnalysis(BiquadFilter filter, float[] samples) {
            this.filter = filter;
            this.samples = samples;
        }

        @Override
        public Double call() throws Exception {
            return filter.filterThenLeq(samples);
        }
    }
}
