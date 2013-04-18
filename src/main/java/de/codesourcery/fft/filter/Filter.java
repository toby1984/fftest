package de.codesourcery.fft.filter;


public abstract class Filter 
{
    public static final NOPFilter NOP_FILTER = new NOPFilter();
    
    public static final class NOPFilter extends Filter {

        @Override
        public double[] filter(double[] data, double windowDurationInSeconds) {
            return data;
        }
    } 
    
    public abstract double[] filter(double[] data,double windowDurationInSeconds);
}