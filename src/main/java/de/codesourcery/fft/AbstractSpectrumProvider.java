package de.codesourcery.fft;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public abstract class AbstractSpectrumProvider implements ISpectrumProvider
{
	private final Object LOCK = new Object();

	public static final Filter NOP_FILTER = new NOPFilter();

	// @GuardedBy( LOCK )
	private Spectrum spectrum;

	private static final Integer DUMMY = new Integer(0);

	// @GuardedBy( LOCK )
	private final Map<ICallback,Integer> pendingRequests = new IdentityHashMap<>();

	private final ExecutorService threadPool;

	private final Filter lowPassFilter = new LowPassFilter(5000);
	private final Filter highPassFilter = new HighPassFilter(15000);

	private final AudioFormat audioFormat;
	private final boolean signedSamples;

	private final WaveWriter waveWriter;    

	public static abstract class Filter 
	{
		public abstract double[] filter(double[] data,double windowDurationInSeconds);
	}

	public static final class NOPFilter extends Filter {

		@Override
		public double[] filter(double[] data, double windowDurationInSeconds) {
			return data;
		}
	}    

	public final class LowPassFilter extends Filter {

		private final double RClow;

		public LowPassFilter(double cutOffFreq) {
			this.RClow = 0.5;// 1.0d/(2.0*Math.PI*cutOffFreq);
		}

		@Override
		public double[] filter(double[] data, double durationInSeconds) 
		{
			final int len = data.length;
			/*
             function lowpass(real[0..n] x, real dt, real RC)
               var real[0..n] y
               var real α := dt / (RC + dt)
               y[0] := x[0]
               for i from 1 to n
                   y[i] := y[i-1] + α * (x[i] - y[i-1])   
               return y      
			 */
			double[] result2 = new double[len];
			result2[0] = data[0];       
			double alphaLow = durationInSeconds / (RClow+durationInSeconds); 
			for ( int i = 1 ; i < len ; i++) {
				result2[i]= result2[i-1] + alphaLow*( data[i] - result2[i-1]);
			}
			return result2;
		}
	}

	protected final class HighPassFilter extends Filter {

		private final double RChigh;

		public HighPassFilter(double cutOffFreq) {
			this.RChigh = 50; // 1.0d/(2.0*Math.PI*cutOffFreq);
		}

		@Override
		public double[] filter(double[] data, double durationInSeconds) 
		{
			durationInSeconds *= 0.1;
			final int len = data.length;
			/*
     // Return RC high-pass filter output samples, given input samples,
     // time interval dt, and time constant RC
     function highpass(real[0..n] x, real dt, real RC)
       var real[0..n] y
       var real α := RC / (RC + dt)
       y[0] := x[0]
       for i from 1 to n
          y[i] := α * (y[i-1] + x[i] - x[i-1])
       return y      
			 */

			/* - a large α corresponds to a large RC and therefore a low corner frequency of the filter
			 * - a small α corresponds to a small RC and therefore a high corner frequency of the filter.
			 */
			double alphaHigh = RChigh / (RChigh+durationInSeconds); 
			double[] result = new double[len];
			result[0] = data[0];
			for ( int i = 1 ; i < len ; i++) {
				result[i]= alphaHigh*(result[i-1]+data[i] - data[i-1] );
			}
			return result;
		}
	}    

	public AbstractSpectrumProvider(AudioFormat audioFormat,File waveFile) throws FileNotFoundException 
	{
		if ( waveFile != null ) 
		{
			if ( audioFormat.getChannels() != 1 ) 
			{
				// tweak AudioFormat since getData() method always returns joint-stereo (=1 channel)
				AudioFormat jointStereo = new AudioFormat(audioFormat.getEncoding(), audioFormat.getSampleRate(), 
						audioFormat.getSampleSizeInBits(), 
						1, 
						audioFormat.getFrameSize() / audioFormat.getChannels() , 
						audioFormat.getFrameRate(), 
						audioFormat.isBigEndian());
				this.waveWriter = new WaveWriter(waveFile,jointStereo);                
			} else {
				this.waveWriter = new WaveWriter(waveFile,audioFormat);
			}
		} else {
			this.waveWriter = null;
		}

		this.signedSamples = audioFormat.getEncoding() == Encoding.PCM_SIGNED || audioFormat.getEncoding() == Encoding.PCM_FLOAT;        

		final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(10);

		final ThreadFactory threadFactory = new ThreadFactory() {

			@Override
			public Thread newThread(final Runnable r)
			{
				final Thread t = new Thread("worker-thread") {
					@Override
					public void run()
					{
						r.run();
					}
				};
				t.setDaemon( true );
				return t;
			}};
			// ringbuffer implementation is based on the assumption of exactly ONE reader and writer
			// so thread pool must not use more than one thread
			threadPool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, workQueue, threadFactory, new AbortPolicy() );

			this.audioFormat = audioFormat;
	}

	public final AudioFormat getAudioFormat() {
		return audioFormat;
	}

	@Override
	public final void calcSpectrum(final ICallback callback,final int fftSize,final boolean applyWindowingFunction,boolean applyFilters) 
	{
		Spectrum tmp = null;
		synchronized(LOCK) 
		{
			if ( ! isStatic() || spectrum == null || spectrum.getFFTSize() != fftSize || 
					spectrum.isWindowFunctionApplied() != applyWindowingFunction || spectrum.isFiltersApplied() != applyFilters )
			{
				if ( ! pendingRequests.containsKey( callback ) )
				{
					runInBackground(callback, fftSize, applyWindowingFunction,applyFilters);
				} else {
					System.out.println("Calculation still pending");
				}
			} else {
				tmp = spectrum;
			}
		} // end synchronized

		if ( tmp != null ) {
			callback.calculationFinished( this , tmp );
		}
	}
	
	private void runInBackground(final ICallback callback, final int fftSize, final boolean applyWindowingFunction,final boolean applyFilters)
	{
		final Runnable runnable = new Runnable() {

			@Override
			public void run()
			{
				Spectrum result=null;
				try {
					result = calculateSpectrum(fftSize,applyWindowingFunction,applyFilters);
				} 
				finally 
				{
					synchronized(LOCK) 
					{
						if ( result != null ) {
							spectrum = result;
						}
						pendingRequests.remove( callback );
					}

					if ( result != null ) {
						callback.calculationFinished( AbstractSpectrumProvider.this , result );
					} else {
						callback.calculationFailed( AbstractSpectrumProvider.this );
					}
				}
			}
		};

		try 
		{
			// calling method has already aquired LOCK 
			pendingRequests.put( callback , DUMMY );
			threadPool.submit( runnable );
		} 
		catch(Exception e) {
			pendingRequests.remove( callback );
		}
	}	

	public void close() 
	{
		System.out.println("Terminating worker pool");
		threadPool.shutdownNow();

		if ( waveWriter != null ) {
			try {
				waveWriter.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}   		
	}

	protected final void invalidateCache() 
	{
		synchronized(LOCK) 
		{
			this.spectrum = null;
		}
	}

	protected static final class SampleData 
	{
		public final double[] data;
		public final double minSample;
		public final double maxSample;

		public SampleData(double[] data, double minSample, double maxSample) {
			this.data = data;
			this.minSample = minSample;
			this.maxSample = maxSample;
		}
	}

	protected abstract SampleData getData();

	protected final double[] filterData(double[] buffer) {
		final double durationInSeconds = buffer.length / audioFormat.getSampleRate();      
		return lowPassFilter.filter( highPassFilter.filter( buffer , durationInSeconds ) , durationInSeconds );
	}

	protected final synchronized Spectrum calculateSpectrum(final int fftSize,final boolean applyWindowingFunction , boolean applyFilters )
	{
		// aquire sample data
		long startTime = System.currentTimeMillis();

		final SampleData sampleData = getData();
		double[] jointStereo = sampleData.data;

		long dataAquisitionTime = System.currentTimeMillis();

		// apply filters
		if ( applyFilters ) { 
			jointStereo = filterData( jointStereo );
		}
		long filterTime = System.currentTimeMillis();

		// DEBUG: write to WAV file
		if ( waveWriter != null ) 
		{
			try {
				waveWriter.write( jointStereo, 0 , jointStereo.length );
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		long wavWriteTime = System.currentTimeMillis();

		// optionally, apply a windowing function to the sample data        
		if ( applyWindowingFunction ) {
			applyWindowingFunction( jointStereo , fftSize );
		}        
		long windowFuncTime = System.currentTimeMillis();        

		final double[] fftData = new double[ fftSize * 2 ]; // need to allocate twice the FFT size , array needs to hold real and imaginary components
		final double[] spectrum = new double[ fftSize ]; 

		// loop over samples , performing FFT on each window 
		int windowCount = 0;
		final DoubleFFT_1D fft = new DoubleFFT_1D(fftSize);

		final double[] compensation = new double[fftSize]; // Kahan summation compensation for each FFT bin
		final double step = fftSize*0.33;
		for ( int offset = 0 ; offset < jointStereo.length-fftSize ; offset += step ) 
		{
			// copy sample data to an array
			// where element(k) = real part (k) and element(k+1) = imaginary part (k)
			int ptr = 0;
			for ( int i = 0 ; i < fftSize ; i++, ptr+=2 ) {
				fftData[ptr]=jointStereo[offset+i];
				fftData[ptr+1]=0;
			}

			// do the actual FFT
			fft.complexForward( fftData );

			// convert FFT result to spectrum (magnitude)
			ptr = 0;
			for ( int bin = 0 ; bin < fftSize ; bin++ , ptr+=2) 
			{
				final double magnitude;
				if ( bin != 0 ) {
					magnitude = Math.sqrt( fftData[ptr] * fftData[ptr] + fftData[ptr+1]*fftData[ptr+1] );
				} else {
					// special case since FFT(0) and FFT(N/2) do not have a complex component,
					// JTransform stores these as element(0) and element(1)
					magnitude = fftData[ptr];
				}

				double input = magnitude*magnitude;

				// average using Kahan summation to minimize rounding errors
				double y = input - compensation[bin];
				double t = spectrum[bin] + y;
				compensation[bin] = ( t - spectrum[bin] ) - y;
				spectrum[bin] = t;
			}
			windowCount++;
		}
		long fftTime = System.currentTimeMillis(); 

		// find min/max values
		double max = -Double.MAX_VALUE;
		double min = Double.MAX_VALUE;

		for ( int i = 1 ; i < fftSize ; i++ ) 
		{
			final double tmp = spectrum[i] / windowCount;
			spectrum[i] = tmp;
			min = Math.min( min , tmp );
			max = Math.max( max , tmp );            
		}

		long calcAverageTime = System.currentTimeMillis();

		final Spectrum result = new Spectrum( spectrum , getAutoCorrelation( spectrum , fftSize ) , 
				fftSize , getAudioFormat().getSampleRate() , applyWindowingFunction , min , max , applyFilters,
				calcVolume( sampleData.minSample, sampleData.maxSample ) );		
		try 
		{
			return result;
		} 
		finally 
		{
		    long totalTime = System.currentTimeMillis();
		    performanceCounter++;
			if ( (performanceCounter % 10) == 0 ) 
			{
			    totalTime -= startTime;
				calcAverageTime -= fftTime;
				fftTime -= windowFuncTime;
				windowFuncTime -= wavWriteTime;
				wavWriteTime -= filterTime;
				filterTime -= dataAquisitionTime;
				dataAquisitionTime -= startTime;

				System.out.println("Total: "+totalTime+
						" / FFT: "+fftTime+
						" / filters: "+filterTime+
						" / window_func: "+windowFuncTime+
						" / wave_write: "+wavWriteTime+
						" / calc_average': "+calcAverageTime+
						" / sampling: "+dataAquisitionTime);
			}
		}
	}

	private volatile long performanceCounter; 

	public double[] getAutoCorrelation(double[] data,int fftSize) 
	{

		final double[] result = new double[ fftSize*2 * 2 ];
		int ptr = 0;
		for ( int i = 0 ; i < data.length ; i+=1 , ptr+=2) 
		{
			result[ptr] = data[i];
			result[ptr+1] = 0; 
		}

		final DoubleFFT_1D fft = new DoubleFFT_1D( fftSize*2 );
		fft.complexInverse( result , true );

		double[] resultArray = new double[ fftSize ];
		ptr = 0;
		for ( int i = 0 ; i < result.length/2 ; i+= 2 ) 
		{
			double mag = Math.sqrt( result[i]*result[i]+result[i+1]*result[i+1]);
			resultArray[ptr++] = mag*mag;
		}
		return resultArray;
	} 	

	/*
	 * 
	 * (3+0i)*(4+0i) = 12 + 0i +0i + 0
	 */

	 //    public double[] getCepstrum(double[] data,int fftSize) 
	//    {
		//            // signal → FT → abs() → square → log → FT → abs() → square → power cepstrum
		//
	//            final DoubleFFT_1D fft = new DoubleFFT_1D( fftSize );
	//
	//            final double[] result = new double[ fftSize ];
	//
	//            int ptr = 0;
	//            for ( int i = 0 ; i < fftSize ; i++ , ptr++) 
	//            {
	//                result[ptr] = Math.log10( data[i] );
	//            }
	//
	//            fft.realInverse( result , true );
	//            return result;
	//    }     

	//    public double[] getCepstrum(double[] data,int fftSize) 
	//    {
	//        try {
	//            // signal → FT → abs() → square → log → FT → abs() → square → power cepstrum
	//
	//            final DoubleFFT_1D fft = new DoubleFFT_1D( fftSize );
	//
	//            final double[] result = new double[ fftSize*2 ];
	//
	//            int ptr = 0;
	//            for ( int i = 0 ; i < fftSize ; i++ , ptr+=2 ) 
	//            {
	//                result[ptr] = Math.log10( data[i] );
	//                result[ptr+1] = 0;
	//            }
	//
	//            fft.complexInverse( result , false );
	//
	//            double[] result2 = new double[ fftSize/2 ];
	//
	//            ptr = 0;
	//            for ( int i = 0 ; i < result2.length ; i+=2 ) 
	//            {
	//                double mag = Math.sqrt( result[i]*result[i] + result[i+1]*result[i+1] );
	//                result2[ptr++] = mag*mag;
	//            }        
	//            return result2;
	//        } catch(RuntimeException e) {
	//            e.printStackTrace();
	//            throw e;
	//        }
	//    }    
	protected final void applyWindowingFunction(double[] data,int windowSize) 
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

	/**
	 * Returns the volume in percent (0...1).
	 * 
	 * @param bitsPerSample number of bits per sample
	 * @param signed whether the sampled data source yields signed or unsigned values
	 * @return
	 */
	protected final double calcVolume( double minSample,double maxSample )
	{
		final int bitsPerSample = audioFormat.getSampleSizeInBits();
		final double volume;
		if ( signedSamples ) 
		{
			// audio source yields signed values
			final double maxValue = (1 << (bitsPerSample-1))-1;
			final double minValue = -(1 << (bitsPerSample-1));				
			final double total = maxValue - minValue;
			volume = (maxSample - minSample ) / total;

		} else {
			// audio source yields unsigned values
			final double maxValue = (1 << bitsPerSample)-1;
			volume = (maxSample - minSample ) / maxValue;
		}
		return volume;
	}     
}
