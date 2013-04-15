package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.sound.sampled.AudioFormat;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.fft.ISpectrumProvider.ICallback;

public final class SpectrumPanel extends JPanel {

	private volatile int currentMarkerX = -1;

	private volatile int width;
	private volatile int height;
	private volatile int x1Origin;
	private volatile int y1Origin;
	
    private volatile int x2Origin;
    private volatile int y2Origin;
    
	private volatile double scaleX1;
	private volatile double scaleY1;

	private volatile boolean applyMinValue;  
	private volatile double minValue;
	
	private volatile boolean useLogScale=true;
	
	private final Object REFRESH_THREAD_LOCK = new Object();

	private final RefreshThread refreshThread = new RefreshThread();
	
	protected final class RefreshThread extends Thread 
	{
		private volatile boolean terminateRefreshThread;
		private final CountDownLatch latch = new CountDownLatch(1);

		public RefreshThread()
		{
			setDaemon(true);
			start();
		}

		public void terminate() 
		{
			terminateRefreshThread = true;
			
			synchronized( REFRESH_THREAD_LOCK ) {
				REFRESH_THREAD_LOCK.notifyAll();
			}
			
			while(true) 
			{
				try {
					latch.await();
					break;
				} catch (InterruptedException e) {
				}
			}
		}
		
		public void run() 
		{
			try 
			{
outer:				
				while( ! terminateRefreshThread ) 
				{
					synchronized(REFRESH_THREAD_LOCK) 
					{
						while ( spectrumProvider == null || spectrumProvider.isStatic() ) 
						{
							try {
								REFRESH_THREAD_LOCK.wait();
							} 
							catch (InterruptedException e) 
							{
								Thread.currentThread().interrupt();
							}
							continue outer;
						}
					}

					refresh();

					try 
					{
						Thread.sleep( 100 );
					} 
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
				}
			} finally {
				latch.countDown();
				System.out.println("Refresh thread terminated");
			}
		}
	}; 
	
	private volatile Spectrum spectrum;

	private final ICallback repaintCallback = new ICallback() {

		@Override
		public void calculationFinished(ISpectrumProvider provider, Spectrum spectrum)
		{
			SpectrumPanel.this.spectrum = spectrum;
			
			SwingUtilities.invokeLater( new Runnable()  {

				@Override
				public void run()
				{
					repaint();
				}
			});
		}
		@Override
		public void calculationFailed(ISpectrumProvider provider) { }
	};    

	private boolean applyWindowFunction=true;

	private volatile int bands;

	private volatile ISpectrumProvider spectrumProvider;

	private final KeyAdapter keyListener = new KeyAdapter() 
	{
		public void keyTyped(java.awt.event.KeyEvent e) 
		{
			if ( e.getKeyChar()== 'c') {
				applyMinValue = false;
				System.out.println("Cleared min. value");
			} 
			else if ( e.getKeyChar()== 'm') {
				Spectrum spectrum = getSpectrum();
				if ( spectrum != null ) {
					applyMinValue = true;
					minValue = spectrum.getMaxValue();
					System.out.println("Applying min. value: "+minValue);
				}
			} 
			else if ( e.getKeyChar()== 'w') {
				applyWindowFunction = ! applyWindowFunction;
				System.out.println("Apply window function: "+applyWindowFunction);
				refresh();
			} else if ( e.getKeyChar() == '+' && bands <= 65535 ) {
				bands = bands << 1;
				System.out.println("FFT bands: "+bands);
				refresh();
			} 
			else if ( e.getKeyChar() == '-' && bands >= 2 ) 
			{
				bands = bands >> 1;
				System.out.println("FFT bands: "+bands);
				refresh();
			} else {
				System.out.println("IGNORED KEYPRESS: '"+e.getKeyChar()+"'");
			}
		}
	};

	private final MouseAdapter mouseListener = new MouseAdapter() 
	{
		@Override
		public void mouseMoved( MouseEvent e)
		{
			double maxX = x1Origin + bands*scaleX1;

			if ( e.getX() >= x1Origin && e.getX() < maxX ) 
			{
				Graphics graphics = getGraphics();
				graphics.setXORMode(Color.WHITE);                    
				if ( currentMarkerX != -1 ) 
				{
					graphics.drawLine( currentMarkerX , y2Origin , currentMarkerX , 0 );
				}
				currentMarkerX = e.getX();
				graphics.drawLine( currentMarkerX , y2Origin , currentMarkerX , 0 );
				graphics.setPaintMode();    

				plotMarkerFrequency(graphics);
			}
		}
	};

	public void dispose() 
	{
		refreshThread.terminate();
		spectrumProvider.close();
	}

	public void attachKeyListener(Component c) 
	{
		c.addKeyListener( this.keyListener );
	}

	public SpectrumPanel(ISpectrumProvider provider,int windowSize)
	{
		this.bands = windowSize/2;
		addMouseMotionListener( mouseListener );
		setSpectrumProvider(provider);
	}

	public void setSpectrumProvider(ISpectrumProvider provider)
	{
		if ( this.spectrumProvider != null ) {
			this.spectrumProvider.close();
		}
		this.spectrumProvider = provider;
		synchronized( REFRESH_THREAD_LOCK ) 
		{
			REFRESH_THREAD_LOCK.notifyAll();
		}
		refresh();
	}

	public void refresh() 
	{
		final long start = System.currentTimeMillis();
		
		final ICallback callback = new ICallback() {
			
			@Override
			public void calculationFinished(ISpectrumProvider provider,
					Spectrum spectrum) 
			{
				@SuppressWarnings("unused")
                final long delta = System.currentTimeMillis() - start;
//				System.out.println("Calculation finished after "+delta);
				repaintCallback.calculationFinished( provider , spectrum);
			}
			
			@Override
			public void calculationFailed(ISpectrumProvider provider) {
				repaintCallback.calculationFailed(provider);
			}
		};
		
		spectrumProvider.calcSpectrum( callback , this.bands*2 , this.applyWindowFunction );   
	}

	private void resized(Spectrum s) 
	{
	    final int h = getHeight() / 2;
	    final int w = getWidth();
	    
		this.width = Math.round(w * 0.8f);
		this.height = Math.round( h * 0.8f);

		this.x1Origin = (int) Math.round( w * 0.1);
		this.y1Origin = Math.round( h *0.9f);
		
        this.x2Origin = x1Origin;
        this.y2Origin = y1Origin+h;

		this.scaleX1 = width / (double) s.getBands();
		this.scaleY1 = height / Math.abs( s.getMaxValue() - s.getMinValue() );
	}

	protected Spectrum getSpectrum() 
	{
		if ( spectrum == null ) {
			refresh();
			return null;
		}
		return spectrum;
	}

	@Override
	public void paint(Graphics g)
	{
		super.paint(g);

		currentMarkerX =  -1;

		Spectrum s = getSpectrum();
		if ( s != null )
		{   
			resized(s);
			plotPowerSpectrum( g , s );
			if ( s.getAutoCorrelation() != null ) {
			    plotAutoCorrelation( g , s );
			}
			plotMarkerFrequency(g);
		} else {
			System.out.println("No spectrum to paint");
		}
	}
	
	private void plotMarkerFrequency(Graphics g)
	{
		// clear old text
		final int x = 5;
		final int y = 15;

		// render frequency at current marker position
		double maxX = x1Origin + bands * scaleX1;
		if ( currentMarkerX != -1 && currentMarkerX >= x1Origin && currentMarkerX <= maxX ) 
		{
			final int lineHeight = g.getFontMetrics().getHeight();
            g.clearRect( x , y-15 , 150 , lineHeight*5 );

			final int band = (int) ( (currentMarkerX - x1Origin) / scaleX1 );

			System.out.println("Band: "+band);
			
			final String f1 = AudioFile.hertzToString( getFrequencyForBand(band) );
			g.setColor(Color.BLACK);
			g.drawString(  f1 , x, y );
			
			Spectrum s = getSpectrum();
			if ( s != null ) 
			{
			    AudioFormat format = spectrumProvider.getAudioFormat();
			    
			    final List<FrequencyAndSlot> autoCorr = getTopAutoCorrelationFrequencies( s , format.getSampleRate() );
			    System.out.println("autoCorr: "+autoCorr);
			    for ( FrequencyAndSlot a : autoCorr ) {
			        final int corrX = (int) Math.round( x1Origin + ( a.slot * scaleX1 ) );
			        g.setColor( Color.RED );
			        g.drawLine(corrX , y2Origin , corrX , y1Origin );
			        
			    }
			    
			    /*
			     * 
			     * 44100 samples      1 sec
			     * -------------- = ---------------   => y = 1 / ( sampleRate / fftSize )
			     * fftSize samples    y sec-
			     */
			    final double windowDurationInSeconds = s.getFFTSize() / format.getSampleRate() / 4.0;
//			    System.out.println("Window duration: "+windowDurationInSeconds);
			    final double percentage = ( currentMarkerX - x1Origin) / ( maxX - x1Origin );
			    
			    final double currentTime = percentage * windowDurationInSeconds;
			    
			    final String freq= AudioFile.hertzToString( 1.0 / currentTime );
			    
	            final DecimalFormat df = new DecimalFormat( "#####0.0####" );
	            g.setColor(Color.BLACK);
	            
                g.drawString(  df.format( currentTime ) , x, y+lineHeight );   	            
	            g.drawString(  freq  , x, y+lineHeight*2 );			    
			}
		} 
	}
	
	protected static final class FrequencyAndSlot implements Comparable<FrequencyAndSlot> 
	{
	    public final double correlationFactor;
	    public final int slot;
	    public final Spectrum spectrum;
	    public final double sampleRate;
	    
        public FrequencyAndSlot(Spectrum spectrum,double sampleRate, double correlationFactor, int slot)
        {
            this.spectrum = spectrum;
            this.sampleRate = sampleRate;
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
            final double windowDurationInSeconds = spectrum.getBands() / sampleRate / 2;
            final double percentage = slot / (double) spectrum.getBands();
            
            final double currentTime = percentage * windowDurationInSeconds;
            return 1.0 / currentTime;
        }
        
        @Override
        public String toString()
        {
            return "AutoCorr( "+slot+" : "+ getFrequency()+")";
        }
	}
	
	private List<FrequencyAndSlot> getTopAutoCorrelationFrequencies(Spectrum s,double sampleRate) 
	{
	    final List<FrequencyAndSlot> candidates = new ArrayList<>();
	    
	    final int peakHeight = (int) (1+(s.getBands()*0.1));
	    
outer:	    
	    for ( int i = peakHeight ; i < s.getBands()-peakHeight ; i++ ) 
	    {
	        double val2 = s.getAutoCorrelation()[i];
	        
	        for ( int j = i-peakHeight ; j < i ; j++ ) {
	            if ( s.getAutoCorrelation()[j] >= val2 ) {
	                continue outer;
	            }
	        }
	        
            for ( int j = i+1 ; j < i+peakHeight ; j++ ) {
                if ( s.getAutoCorrelation()[j] >= val2 ) {
                    continue outer;
                }
            }	        
            candidates.add( new FrequencyAndSlot( s , sampleRate , val2 , i ) );
	    }
	    Collections.sort( candidates );
	    Collections.reverse( candidates );
	    if ( candidates.size() > 7 ) {
	        return candidates.subList( 0 , 7 );
	    }
	    return candidates;
	}
	
    private void plotAutoCorrelation(Graphics g,Spectrum s) 
    {
        g.setColor(Color.GREEN);

        int barWidthInPixels = (int) Math.round(scaleX1);
        if ( barWidthInPixels < 1 ) {
            barWidthInPixels = 1;
        }

        final double[] autoCorr = s.getAutoCorrelation();
        final int bands = autoCorr.length/2;
        
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        
        for ( int i = 0 ; i < bands ; i++ ) {
            min = Math.min(min, autoCorr[i] );
            max = Math.max(max, autoCorr[i] );
        }
        final double scaleY = (height/2.0d) / Math.abs( max - min);
        for ( int band = 0 ; band < bands ; band++ ) 
        {
            final int x = (int) Math.floor( x2Origin + band*scaleX1);
            double y = 2*autoCorr[band]*scaleY;
//            System.out.println("y="+y+" [ "+autoCorr[band]+")");
            g.fillRect( x , (int) Math.round( y2Origin - y ) , barWidthInPixels , (int) Math.round( y ) );
        }
    }	

	private void plotPowerSpectrum(Graphics g,Spectrum s) 
	{
		g.setColor(Color.BLUE);

		final double yOffset = s.getMinValue() > 0 ? -s.getMinValue() : s.getMinValue();

		int barWidthInPixels = (int) Math.round(scaleX1);
		if ( barWidthInPixels < 1 ) {
			barWidthInPixels = 1;
		}

		final double[] spectrum = s.getData();

		final int bands = s.getBands();
		for ( int band = 0 ; band < bands ; band++ ) 
		{
			final double frequency = getFrequencyForBand( band );
			final int x = (int) Math.floor( x1Origin + band*scaleX1);
			
			if ( ! applyMinValue || spectrum[band] > minValue ) 
			{
				double value = spectrum[band]+yOffset;
				double y;
				if ( useLogScale ) 
				{
					value = 4*Math.log10( value*value );
					y = (value*value*value)/2500.0d;
				} else {
					y = value*scaleY1;
				}
				g.fillRect( x , (int) Math.round( y1Origin - y ) , barWidthInPixels , (int) Math.round( y ) );
			}
			
			if ( bands < 32 ) 
			{
				// draw label
				final String f = AudioFile.hertzToString( frequency );
				final Rectangle2D bounds = g.getFontMetrics().getStringBounds( f , g );

				g.drawString( f , x + barWidthInPixels/2 - (int) Math.round(bounds.getWidth()/2) , y1Origin + g.getFontMetrics().getHeight() );
			}
		}
	}

	private double getFrequencyForBand(int band) 
	{
		return band * getBandwidth();
	}

	private double getBandwidth() 
	{
		return spectrumProvider.getAudioFormat().getSampleRate()/2.0/bands;
	}    
}