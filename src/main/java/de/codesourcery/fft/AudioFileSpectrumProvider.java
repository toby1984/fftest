package de.codesourcery.fft;

import de.codesourcery.fft.AudioFile.AudioDataIterator;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class AudioFileSpectrumProvider extends AbstractSpectrumProvider
{
    private volatile AudioFile file;
    
    public AudioFileSpectrumProvider(AudioFile file) {
        this.file = file;
    }
    
    public AudioFile getAudioFile()
    {
        return file;
    }
    
    public void setAudioFile(AudioFile file)
    {
        this.file = file;
        invalidateCache();
    }

    @Override
    protected Spectrum calculateSpectrum(int windowSize,boolean applyWindowingFunction )
    {
        // read whole file in one go
        final byte[] data;
        try {
            data = file.readFrames( 0 , (int) file.getTotalFrameCount() );
        } catch (Exception e1) {
            throw new RuntimeException(e1);
        }

        // join data from all channels by summing them up and taking the arithmetic average
        final int totalSampleCount = (int) Math.ceil( file.getTotalFrameCount()* ( file.getSamplesPerFrame() / file.getFormat().getChannels() ));

        final double channels = file.getFormat().getChannels();
        final double[] jointStereo = new double[ totalSampleCount ]; // 16-bit samples
        for ( int channel = 0 ; channel < channels ; channel++) 
        {
            final AudioDataIterator it = file.iterator( data , channel ); 
            for ( int j = 0 ; j < totalSampleCount ; j++ ) 
            {
                    jointStereo[j] += it.next();
            }
        }

        for ( int j = 0 ; j < totalSampleCount ; j++ ) {
            jointStereo[j] /= channels;
        }
        
        // optionally, apply a windowing function to the sample data        
        if ( applyWindowingFunction ) {
            applyWindowingFunction( jointStereo , windowSize );
        }        

        final double[] fftData = new double[ windowSize * 2 ]; // need to allocate twice the FFT size , array needs to hold real and imaginary components
        final double[] spectrum = new double[ windowSize ]; 

        // loop over samples , performing FFT on each window 
        int windowCount = 0;
        final DoubleFFT_1D fft = new DoubleFFT_1D(windowSize);
        
        final double[] compensation = new double[windowSize]; // Kahan summation compensation for each FFT bin
        final double step = windowSize*0.33;
        for ( int offset = 0 ; offset < jointStereo.length-windowSize ; offset += step ) 
        {
            // copy sample data to an array
            // where element(k) = real part (k) and element(k+1) = imaginary part (k)
            int ptr = 0;
            for ( int i = 0 ; i < windowSize ; i++, ptr+=2 ) {
                fftData[ptr]=jointStereo[offset+i];
                fftData[ptr+1]=0;
            }
            
            // do the actual FFT
            fft.complexForward( fftData );

            // convert FFT result to spectrum (magnitude)
            ptr = 0;
            for ( int bin = 0 ; bin < windowSize ; bin++ , ptr+=2) 
            {
                final double magnitude;
                if ( bin != 0 ) {
                    magnitude = Math.sqrt( fftData[ptr] * fftData[ptr] + fftData[ptr+1]*fftData[ptr+1] );
                } else {
                    // special case since FFT(0) and FFT(N/2) do not have a complex component,
                    // JTransform stores these as element(0) and element(1)
                    magnitude = fftData[ptr];
                }
                
                double input = (magnitude*magnitude);
                
                // average using Kahan summation to minimize rounding errors
                double y = input - compensation[bin];
                double t = spectrum[bin] + y;
                compensation[bin] = ( t - spectrum[bin] ) - y;
                spectrum[bin] = t;
            }
            windowCount++;
        }

        for ( int i = 0 ; i < windowSize ; i++ ) 
        {
//            spectrum[i] = 10*Math.log10( spectrum[i] / windowCount );
            spectrum[i] = spectrum[i] / windowCount;
        }
        return new Spectrum( spectrum , windowSize , applyWindowingFunction ); 
    }
    
    private void applyWindowingFunction(double[] data,int windowSize) 
    {
        final int len = data.length;
        for ( int ptr = 0 ; ptr < len ; ptr++ ) // data[k] = Re(k) , data[k+1=Img(k)
        {
            final double n = ptr % windowSize;
            double coeff = 0.5 - 0.5*Math.cos( (2f*Math.PI*n) / ( windowSize-1 ) ); // Hann window               
//          double coeff = 1-( (n-((windowSize-1)/2 ) ) / ((windowSize+1)/2) ); // Welch window         
            data[ptr] = data[ptr] * coeff;
        }
    }     
}
