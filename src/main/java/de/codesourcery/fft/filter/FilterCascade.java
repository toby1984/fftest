package de.codesourcery.fft.filter;

public class FilterCascade extends Filter
{
    private final Filter[] filters;
    
    public FilterCascade(Filter... filters) 
    {
        if ( filters != null ) {
            this.filters = filters;
        } else {
            this.filters = new Filter[0];
        }
    }

    @Override
    public double[] filter(double[] data)
    {
        double[] result = data;
        for ( Filter f : filters ) {
            result = f.filter( result );
        }
        return result;
    }
}
