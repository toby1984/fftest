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

public abstract class AbstractSpectrumProvider implements ISpectrumProvider
{
    private final Object LOCK = new Object();

    // @GuardedBy( LOCK )
    private Spectrum spectrum;

    private static final Integer DUMMY = new Integer(0);

    // @GuardedBy( LOCK )
    private final Map<ICallback,Integer> pendingRequests = new IdentityHashMap<>();

    private static final ExecutorService threadPool;

    static 
    {
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
            if ( spectrum == null || spectrum.getFFTSize() != fftSize || spectrum.isWindowFunctionApplied() != applyWindowingFunction )
            {
                if ( ! pendingRequests.containsKey( callback ) )
                {
                    runInBackground(callback, fftSize, applyWindowingFunction);
                } 
            } else {
                tmp = spectrum;
            }
        } // end synchronized
        
        if ( tmp != null ) {
            callback.calculationFinished( this , tmp );
        }
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

    protected abstract Spectrum calculateSpectrum(int fftSize,boolean applyWindowingFunction);
}
