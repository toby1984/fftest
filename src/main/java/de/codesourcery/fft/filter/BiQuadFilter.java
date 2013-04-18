package de.codesourcery.fft.filter;

/**
 * BiQuad IIR filter.
 * 
 * Code taken from http://www.earlevel.com/main/2012/11/26/biquad-c-source-code/ and
 * JavaScript source on http://www.earlevel.com/main/2010/12/20/biquad-calculator/.
 * 
 * (C) Nigel Redmon / earlevel.com
 */
public final class BiQuadFilter extends Filter 
{
    public static enum BiQuadType 
    {
        LOWPASS,
        HIGHPASS,
        BANDPASS,
        NOTCH,
        PEAK,
        LOWSHELF,
        HIGHSHELF
    };

    private final double a0;
    private final double a1;
    private final double a2;        

    private final double b1;
    private final double b2;        

    private double z1=0;
    private double z2=0;

    public BiQuadFilter(double a0, double a1, double a2, double b1, double b2)
    {
        this.a0 = a0;
        this.a1 = a1;
        this.a2 = a2;
        this.b1 = b1;
        this.b2 = b2;
    }

    public static void main(String[] args)
    {
        System.out.println( BiQuadFilter.create( BiQuadType.LOWPASS , 10000 , 44100 , 0.7071 , 6 ) );
    }

    @Override
    public double[] filter(double[] x, double windowDurationInSeconds)
    {
        final int len = x.length;
        for ( int n = 0 ; n < len ; n++ ) 
        {
            final double in = x[n];
            double out = in * a0 + z1;
            x[n] = out;
            z1 = in * a1 + z2 - b1 * out;
            z2 = in * a2 - b2 * out;
        }
        return x;
    }

    public static final BiQuadFilter create(BiQuadType type , double Fc, double Fs , double Q , double peakGain) 
    {
        double a0 = 0;
        double a1 = 0;
        double a2 = 0;
        double b1 = 0;
        double b2 = 0;
        double norm=0;

        double V = Math.pow(10, Math.abs(peakGain) / 20);
        double K = Math.tan(Math.PI * Fc / Fs);
        switch (type) {
            case LOWPASS:
                norm = 1 / (1 + K / Q + K * K);
                a0 = K * K * norm;
                a1 = 2 * a0;
                a2 = a0;
                b1 = 2 * (K * K - 1) * norm;
                b2 = (1 - K / Q + K * K) * norm;
                break;
            
            case HIGHPASS:
                norm = 1 / (1 + K / Q + K * K);
                a0 = 1 * norm;
                a1 = -2 * a0;
                a2 = a0;
                b1 = 2 * (K * K - 1) * norm;
                b2 = (1 - K / Q + K * K) * norm;
                break;
            
            case BANDPASS:
                norm = 1 / (1 + K / Q + K * K);
                a0 = K / Q * norm;
                a1 = 0;
                a2 = -a0;
                b1 = 2 * (K * K - 1) * norm;
                b2 = (1 - K / Q + K * K) * norm;
                break;
            
            case NOTCH:
                norm = 1 / (1 + K / Q + K * K);
                a0 = (1 + K * K) * norm;
                a1 = 2 * (K * K - 1) * norm;
                a2 = a0;
                b1 = a1;
                b2 = (1 - K / Q + K * K) * norm;
                break;
            
            case PEAK:
                if (peakGain >= 0) {
                    norm = 1 / (1 + 1/Q * K + K * K);
                    a0 = (1 + V/Q * K + K * K) * norm;
                    a1 = 2 * (K * K - 1) * norm;
                    a2 = (1 - V/Q * K + K * K) * norm;
                    b1 = a1;
                    b2 = (1 - 1/Q * K + K * K) * norm;
                }
                else {  
                    norm = 1 / (1 + V/Q * K + K * K);
                    a0 = (1 + 1/Q * K + K * K) * norm;
                    a1 = 2 * (K * K - 1) * norm;
                    a2 = (1 - 1/Q * K + K * K) * norm;
                    b1 = a1;
                    b2 = (1 - V/Q * K + K * K) * norm;
                }
                break;
            case LOWSHELF:
                if (peakGain >= 0) {
                    norm = 1 / (1 + Math.sqrt(2) * K + K * K);
                    a0 = (1 + Math.sqrt(2*V) * K + V * K * K) * norm;
                    a1 = 2 * (V * K * K - 1) * norm;
                    a2 = (1 - Math.sqrt(2*V) * K + V * K * K) * norm;
                    b1 = 2 * (K * K - 1) * norm;
                    b2 = (1 - Math.sqrt(2) * K + K * K) * norm;
                }
                else {  
                    norm = 1 / (1 + Math.sqrt(2*V) * K + V * K * K);
                    a0 = (1 + Math.sqrt(2) * K + K * K) * norm;
                    a1 = 2 * (K * K - 1) * norm;
                    a2 = (1 - Math.sqrt(2) * K + K * K) * norm;
                    b1 = 2 * (V * K * K - 1) * norm;
                    b2 = (1 - Math.sqrt(2*V) * K + V * K * K) * norm;
                }
                break;
            case HIGHSHELF:
                if (peakGain >= 0) {
                    norm = 1 / (1 + Math.sqrt(2) * K + K * K);
                    a0 = (V + Math.sqrt(2*V) * K + K * K) * norm;
                    a1 = 2 * (K * K - V) * norm;
                    a2 = (V - Math.sqrt(2*V) * K + K * K) * norm;
                    b1 = 2 * (K * K - 1) * norm;
                    b2 = (1 - Math.sqrt(2) * K + K * K) * norm;
                }
                else {  
                    norm = 1 / (V + Math.sqrt(2*V) * K + K * K);
                    a0 = (1 + Math.sqrt(2) * K + K * K) * norm;
                    a1 = 2 * (K * K - 1) * norm;
                    a2 = (1 - Math.sqrt(2) * K + K * K) * norm;
                    b1 = 2 * (K * K - V) * norm;
                    b2 = (V - Math.sqrt(2*V) * K + K * K) * norm;
                }
                break;
        }
        return new BiQuadFilter( a0 , a1 , a2 , b1 , b2 );
    }

    @Override
    public String toString()
    {
        return "BiQuadFilter [ \na0=" + a0 + ", \na1=" + a1 + ", \na2=" + a2 + ", \nb1=" + b1 + ", \nb2=" + b2 + "]";
    }    
}