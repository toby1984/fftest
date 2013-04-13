package de.codesourcery.fft;

import java.util.Arrays;


public final class Spectrum
{
    private final double[] data;
    private final int fftSize;
    private final boolean windowFunctionApplied;
    
    private final int bands;
    private final double minValue;
    private final double maxValue;
    
    public Spectrum(double[] data, int fftSize,boolean windowFunctionApplied,double minValue,double maxValue)
    {
        this.data = data;
        this.fftSize = fftSize;
        this.windowFunctionApplied=windowFunctionApplied;
        this.bands = fftSize/2;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }
    
    @Override
	public String toString() {
		return "Spectrum [fftSize="
				+ fftSize + ", windowFunctionApplied=" + windowFunctionApplied
				+ ", bands=" + bands + ", minValue=" + minValue + ", maxValue="
				+ maxValue + "data=" + Arrays.toString(data) + ", ]";
	}

	public double getMinValue()
    {
        return minValue;
    }
    
    public double getMaxValue()
    {
        return maxValue;
    }
    
    public int getBands()
    {
        return bands;
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
        return data;
    }
}