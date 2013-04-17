package de.codesourcery.fft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public final class Spectrum
{
    private final double[] powerSpectrum;
    private final int fftSize;
    private final boolean windowFunctionApplied;
    private final double sampleRate;
    
    private final int bands;
    
    private final double volumeInPercent;
    
    private final double minPower;
    private final double maxPower;
    
    private final double[] autoCorrelation;
    
    private final boolean filtersApplied;
    
    private final List<FrequencyAndSlot> topAutoCorrelated;
    
    public Spectrum(double[] powerSpectrum, 
    		double[] autoCorrelation,
    		int fftSize,
    		double sampleRate,
    		boolean windowFunctionApplied,
    		double minValue,
    		double maxValue,
    		boolean filtersApplied,
    		double volumeInPercent)
    {
        this.powerSpectrum = powerSpectrum;
        if ( ( fftSize >> 1 ) << 1 != fftSize ) {
            throw new IllegalArgumentException("FFT size needs to be 2^x");
        }
        this.sampleRate = sampleRate;
        this.fftSize = fftSize;
        this.filtersApplied = filtersApplied;
        this.autoCorrelation = autoCorrelation;
        this.windowFunctionApplied=windowFunctionApplied;
        this.bands = fftSize/2;
        this.minPower = minValue;
        this.maxPower = maxValue;
        this.volumeInPercent = volumeInPercent;
        this.topAutoCorrelated = calcTopAutoCorrelationFrequencies(1);
    }
    
    public double getVolumeInPercent() {
		return volumeInPercent;
	}
    
    public boolean isFiltersApplied() {
		return filtersApplied;
	}
    
    public List<FrequencyAndSlot> getTopAutoCorrelated() {
    	return topAutoCorrelated;
    }
    
    @Override
	public String toString() {
		return "Spectrum [fftSize="
				+ fftSize + ", windowFunctionApplied=" + windowFunctionApplied
				+ ", bands=" + bands + ", minValue=" + minPower + ", maxValue="
				+ maxPower + "data=" + Arrays.toString(powerSpectrum) + ", ]";
	}
    
    public double[] getAutoCorrelation()
    {
        return autoCorrelation;
    }

	public double getMinValue()
    {
        return minPower;
    }
    
    public double getMaxValue()
    {
        return maxPower;
    }
    
    public int getBands()
    {
        return bands;
    }
    
    public double getSampleRate() {
		return sampleRate;
	}

    public boolean isWindowFunctionApplied()
    {
        return windowFunctionApplied;
    }
    
    public int getFFTSize()
    {
        return fftSize;
    }
    
    public double[] getData()
    {
        return powerSpectrum;
    }
    
	public final class FrequencyAndSlot implements Comparable<FrequencyAndSlot> 
	{
	    public final double correlationFactor;
	    public final int slot;
	    
        public FrequencyAndSlot(double correlationFactor, int slot)
        {
            this.correlationFactor = correlationFactor;
            this.slot = slot;
        }
        @Override
        public int compareTo(FrequencyAndSlot o)
        {
            return Double.compare( this.correlationFactor , o.correlationFactor );
        }
        
        public double getFrequency() 
        {
            final double windowDurationInSeconds = getBands() / sampleRate / 2;
            final double percentage = slot / (double) getBands();
            
            final double currentTime = percentage * windowDurationInSeconds;
            return 1.0 / currentTime;
        }
        
        @Override
        public String toString()
        {
            return "AutoCorr( "+slot+" : "+ getFrequency()+")";
        }
	}
	
	private List<FrequencyAndSlot> calcTopAutoCorrelationFrequencies(int count) 
	{
	    final List<FrequencyAndSlot> candidates = new ArrayList<>();
	    
	    final int peakHeight = (int) (1+(getBands()*0.1));
	    
outer:	    
	    for ( int i = peakHeight ; i < getBands()-peakHeight ; i++ ) 
	    {
	        double val2 = getAutoCorrelation()[i];
	        
	        for ( int j = i-peakHeight ; j < i ; j++ ) {
	            if ( getAutoCorrelation()[j] >= val2 ) {
	                continue outer;
	            }
	        }
	        
            for ( int j = i+1 ; j < i+peakHeight ; j++ ) {
                if ( getAutoCorrelation()[j] >= val2 ) {
                    continue outer;
                }
            }	        
            candidates.add( new FrequencyAndSlot( val2 , i ) );
	    }
	    Collections.sort( candidates );
	    Collections.reverse( candidates );
	    if ( candidates.size() > count ) {
	        return candidates.subList( 0 , count );
	    }
	    return candidates;
	}    
}