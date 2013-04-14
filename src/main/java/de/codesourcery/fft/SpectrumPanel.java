package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.concurrent.CountDownLatch;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.fft.ISpectrumProvider.ICallback;

public final class SpectrumPanel extends JPanel {

	private volatile int currentMarkerX = -1;

	private volatile int width;
	private volatile int height;
	private volatile int xOrigin;
	private volatile int yOrigin;
	private volatile double scaleX;
	private volatile double scaleY;

	private volatile boolean applyMinValue;  
	private volatile double minValue;
	
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

	private int bands;

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
				refresh();
			} 
			else if ( e.getKeyChar() == '-' && bands >= 2 ) 
			{
				bands = bands >> 1;
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
			double maxX = xOrigin + bands*scaleX;

			if ( e.getX() >= xOrigin && e.getX() < maxX ) 
			{
				Graphics graphics = getGraphics();
				graphics.setXORMode(Color.WHITE);                    
				if ( currentMarkerX != -1 ) 
				{
					graphics.drawLine( currentMarkerX , yOrigin , currentMarkerX , 0 );
				}
				currentMarkerX = e.getX();
				graphics.drawLine( currentMarkerX , yOrigin , currentMarkerX , 0 );
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
				final long delta = System.currentTimeMillis() - start;
				System.out.println("Calculation finished after "+delta);
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
		this.width = Math.round(getWidth()*0.8f);
		this.height = Math.round(getHeight()*0.8f);

		this.xOrigin = (int) Math.round( getWidth() * 0.1);
		this.yOrigin = Math.round( getHeight() *0.9f);

		this.scaleX = width / (double) s.getBands();
		this.scaleY = height / Math.abs( s.getMaxValue() - s.getMinValue() );
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
			plotChart( g , s );
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
		double maxX = xOrigin + bands * scaleX;
		if ( currentMarkerX != -1 && currentMarkerX >= xOrigin && currentMarkerX <= maxX ) 
		{
			g.clearRect( x , y-15 , 150 , g.getFontMetrics().getHeight() );

			final int band = (int) ( (currentMarkerX-xOrigin) / scaleX );

			final String f1 = AudioFile.hertzToString( getFrequencyForBand(band) );
			g.setColor(Color.BLACK);
			g.drawString(  f1 , x, y );
		} 
	}

	private void plotChart(Graphics g,Spectrum s) 
	{
		g.setColor(Color.BLUE);

		final double yOffset = s.getMinValue() > 0 ? -s.getMinValue() : s.getMinValue();

		int barWidthInPixels = (int) Math.round(scaleX);
		if ( barWidthInPixels < 1 ) {
			barWidthInPixels = 1;
		}

		final double[] spectrum = s.getData();

		for ( int band = 0 ; band < bands ; band++ ) 
		{
			final double frequency = getFrequencyForBand( band );
			final int x = (int) Math.floor( xOrigin + band*scaleX);
			
			if ( ! applyMinValue || spectrum[band] > minValue ) {
				double value = spectrum[band]+yOffset;
				final double y = value*scaleY;
				g.fillRect( x , (int) Math.round( yOrigin - y ) , barWidthInPixels , (int) Math.round( y ) );
			}
			
			if ( bands < 32 ) 
			{
				// draw label
				final String f = AudioFile.hertzToString( frequency );
				final Rectangle2D bounds = g.getFontMetrics().getStringBounds( f , g );

				g.drawString( f , x + barWidthInPixels/2 - (int) Math.round(bounds.getWidth()/2) , yOrigin + g.getFontMetrics().getHeight() );
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