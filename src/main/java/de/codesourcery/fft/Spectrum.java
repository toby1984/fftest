package de.codesourcery.fft;


public final class Spectrum
{
    private final double[] data;
    private final int fftSize;
    private final boolean windowFunctionApplied;
    
    private final int bands;
    private final double minValue;
    private final double maxValue;
    
    public Spectrum(double[] data, int fftSize,boolean windowFunctionApplied)
    {
        this.data = data;
        this.fftSize = fftSize;
        this.windowFunctionApplied=windowFunctionApplied;
        this.bands = fftSize/2;
        
        // find min/max values
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;

        for ( int i = 0 ; i < bands ; i++ ) 
        {
            min = Math.min( min ,  data[i] );
            max = Math.max( max , data[i] );
        }

        this.minValue = min; 
        this.maxValue = max;        
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