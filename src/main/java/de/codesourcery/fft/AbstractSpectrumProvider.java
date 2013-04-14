package de.codesourcery.fft;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy;
import java.util.concurrent.TimeUnit;

import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public abstract class AbstractSpectrumProvider implements ISpectrumProvider
{
	private final Object LOCK = new Object();

	// @GuardedBy( LOCK )
	private Spectrum spectrum;

	private static final Integer DUMMY = new Integer(0);

	// @GuardedBy( LOCK )
	private final Map<ICallback,Integer> pendingRequests = new IdentityHashMap<>();

	private final ExecutorService threadPool;

	public AbstractSpectrumProvider() {
		final int threadCount = Runtime.getRuntime().availableProcessors()+1;
		final BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(50);

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
			threadPool = new ThreadPoolExecutor(threadCount, threadCount, 60, TimeUnit.SECONDS, workQueue, threadFactory, new CallerRunsPolicy() );
	}

	@Override
	public final void calcSpectrum(final ICallback callback,final int fftSize,final boolean applyWindowingFunction) 
	{
		Spectrum tmp = null;
		synchronized(LOCK) 
		{
			if ( ! isStatic() || spectrum == null || spectrum.getFFTSize() != fftSize || spectrum.isWindowFunctionApplied() != applyWindowingFunction )
			{
				if ( ! pendingRequests.containsKey( callback ) )
				{
					runInBackground(callback, fftSize, applyWindowingFunction);
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

	public void close() {
		threadPool.shutdownNow();
	}

	private void runInBackground(final ICallback callback, final int fftSize, final boolean applyWindowingFunction)
	{
		pendingRequests.put( callback , DUMMY );
		threadPool.submit( new Runnable() {

			@Override
			public void run()
			{
				Spectrum result=null;
				try {
					result = calculateSpectrum(fftSize,applyWindowingFunction);
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
		} );
	}

	protected final void invalidateCache() 
	{
		synchronized(LOCK) 
		{
			this.spectrum = null;
		}
	}

	protected abstract double[] getData();

	protected final Spectrum calculateSpectrum(int windowSize,boolean applyWindowingFunction )
	{
		long time = -System.currentTimeMillis();
		final double[] jointStereo = getData();
		time += System.currentTimeMillis();
//		System.out.println("Data aquisition: "+time+" ms");

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

		// find min/max values
		double max = -Double.MAX_VALUE;
		double min = Double.MAX_VALUE;

		for ( int i = 0 ; i < windowSize ; i++ ) 
		{
			//          double tmp = 10*Math.log10( spectrum[i] / windowCount );
			double tmp = spectrum[i] / windowCount;

			spectrum[i] = tmp;
			min = Math.min( min , tmp );
			max = Math.max( max , tmp );            
		}
		return new Spectrum( spectrum , windowSize , applyWindowingFunction , min , max ); 
	}

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
}
