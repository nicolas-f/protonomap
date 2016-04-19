package org.orbisgis.sos;

import java.util.Arrays;

import org.jtransforms.fft.FloatFFT_1D;

/**
 * Signal processing core
 */
public class FFTSignalProcessing {

    public final int samplingRate;
    private short[] sampleBuffer;
    double[] standardFrequencies;
    private final int windowSize;
    private FloatFFT_1D floatFFT_1D;
    private static final double RMS_REFERENCE_90DB = 2500;
    private static final double DB_FS_REFERENCE = - (20 * Math.log10((RMS_REFERENCE_90DB * Math.sqrt(2)) / (double)Short.MAX_VALUE)) + 90;

    public FFTSignalProcessing(int samplingRate, double[] standardFrequencies, int windowSize) {
        this.windowSize = windowSize;
        this.standardFrequencies = standardFrequencies;
        this.samplingRate = samplingRate;
        this.sampleBuffer = new short[windowSize];
        this.floatFFT_1D = new FloatFFT_1D(windowSize);
    }

    /**
     * @return Time in seconds of sample buffer
     */
    public double getSampleDuration() {
        return samplingRate / (double)windowSize;
    }

    /**
     * @return Computed frequencies
     */
    public double[] getStandardFrequencies() {
        return Arrays.copyOf(standardFrequencies, standardFrequencies.length);
    }

    /**
     * @return Internal sample buffer where length depends on minimal frequency.
     */
    public short[] getSampleBuffer() {
        return sampleBuffer;
    }

    /**
     * Add an audio sample to the buffer.
     * 1000 Hz sinusoidal 2500 RMS for 16-bits audio samples yields a 90dB SPL value.
     * @param sample audio sample
     */
    public void addSample(short[] sample) {
        // Move previous samples backward
        System.arraycopy(sampleBuffer, sample.length, sampleBuffer, 0 , sampleBuffer.length - sample.length);
        System.arraycopy(sample, 0 , sampleBuffer, sampleBuffer.length - sample.length ,sample.length);
    }

    public double computeGlobalLeq() {
        double sampleSum = 0;
        for (short sample : sampleBuffer) {
            sampleSum += sample * sample;
        }
        final double rmsValue = Math.sqrt(sampleSum / sampleBuffer.length);
        return todBspl(rmsValue * Math.sqrt(2));
    }

    public static double todBspl(double peakLevel) {
        return (20 * Math.log10(peakLevel / Short.MAX_VALUE) + DB_FS_REFERENCE);
    }

    /**
     * Calculation of the equivalent sound pressure level per third octave bands
     * @see "http://stackoverflow.com/questions/18684948/how-to-measure-sound-volume-in-db-scale-android"
     * @return List of double array of equivalent sound pressure level per third octave bands
     */
    public ProcessingResult processSample(boolean hanningWindow, boolean aWeighting) {
        float[] signal = new float[sampleBuffer.length];
        for(int i=0; i < signal.length; i++) {
            signal[i] = sampleBuffer[i];
        }
        if(hanningWindow) {
            // Apply Hanning window
            AcousticIndicators.hanningWindow(signal);
        }
        floatFFT_1D.realForward(signal);
        final double freqByCell = samplingRate / windowSize;
        float[] fftResult = new float[signal.length / 2];
        //a[offa+2*k] = Re[k], 0<=k<n/2
        for(int k = 0; k < fftResult.length; k++) {
            final float re = signal[k * 2];
            final float im = signal[k * 2 + 1];
            final double peak = Math.sqrt(re * re + im * im) / fftResult.length;
            fftResult[k] = (float)(peak);
        }
        // Compute A weighted third octave bands
        float[] splLevels = thirdOctaveProcessing(fftResult, aWeighting);
        double globalSpl = 0;
        for(float splLevel : splLevels) {
            globalSpl += Math.pow(10, splLevel / 10);
        }
        // Limit spectrum output by specified frequencies and convert to dBspl
        float[] spectrumSplLevels = new float[(int)(standardFrequencies[standardFrequencies.length - 1] /  freqByCell)];
        for(int i = 0; i < spectrumSplLevels.length; i++) {
            spectrumSplLevels[i] = (float) todBspl(fftResult[i]);
        }
        return new ProcessingResult(spectrumSplLevels, splLevels, (float)(10 * Math.log10(globalSpl)));
    }

    public float[] thirdOctaveProcessing(float[] fftResult, boolean aWeighting) {
        final double freqByCell = samplingRate / windowSize;
        float[] splLevels = new float[standardFrequencies.length];
        int thirdOctaveId = 0;
        int refFreq = Arrays.binarySearch(standardFrequencies, 1000);
        for(double fNominal : standardFrequencies) {
            // Compute lower and upper value of third-octave
            // NF-EN 61260
            // base 10
            double fCenter = Math.pow(10, (Arrays.binarySearch(standardFrequencies, fNominal) - refFreq)/10.) * 1000;
            final double fLower = fCenter * Math.pow(10, -1. / 20.);
            final double fUpper = fCenter * Math.pow(10, 1. / 20.);
            double sumVal = 0;
            int cellLower = (int)(Math.ceil(fLower / freqByCell));
            int cellUpper = Math.min(fftResult.length - 1, (int) (Math.floor(fUpper / freqByCell)));
            for(int idCell = cellLower; idCell <= cellUpper; idCell++) {
                sumVal += fftResult[idCell];
            }
            sumVal = todBspl(sumVal);
            if(aWeighting) {
                // Apply A weighting
                int freqIndex = Arrays.binarySearch(
                        ThirdOctaveFrequencies.STANDARD_FREQUENCIES, fNominal);
                sumVal = (float) (sumVal + ThirdOctaveFrequencies.A_WEIGHTING[freqIndex]);
            }
            splLevels[thirdOctaveId] = (float) sumVal;
            thirdOctaveId++;
        }
        return splLevels;
    }

    /**
     * FFT processing result
     * TODO provide warning information about approximate value about 30 dB range from -18 dB to +12dB around 90 dB
     */
    public static final class ProcessingResult {
        float[] fftResult;
        float[] dBaLevels;
        float globaldBaValue;

        public ProcessingResult(float[] fftResult, float[] dBaLevels, float globaldBaValue) {
            this.fftResult = fftResult;
            this.dBaLevels = dBaLevels;
            this.globaldBaValue = globaldBaValue;
        }

        public float[] getFftResult() {
            return fftResult;
        }

        public float[] getdBaLevels() {
            return dBaLevels;
        }

        public float getGlobaldBaValue() {
            return globaldBaValue;
        }
    }
}