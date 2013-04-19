package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.fft.TuningHelper.Key;

public class TunerPanel extends JPanel {

	private static final double DEG_TO_RAD = Math.PI/180.0d;
	private static final double EPSILON = 1;
	
	private static final int REFRESH_INTERVAL_MILLIS = 200;
	
	private static final double ZERO_ANGLE = -90.0;
	
	private volatile double targetAngle=ZERO_ANGLE;
	private volatile double currentAngle=ZERO_ANGLE;
	
	private final UpdateThread updateThread;
	
	private volatile String label = " -- ";
	
	public TunerPanel() 
	{
		updateThread = new UpdateThread();
		updateThread.start();
		setPreferredSize( new Dimension (80,80 ) );
		setMinimumSize( new Dimension (80,80 ) );
	}
	
	private final Key getGuitarKey(double actualFrequency) {
		
		// find guitar key we're closest to
		Key best = null;
		double bestDelta = 0;
		double translatedActualFrequency=actualFrequency;
		for ( Key k : TuningHelper.GUITAR_KEYS ) 
		{
			// we'll check not only for the fundamental frequency
			// but also for the 1st and 2nd harmonic
			for ( int i = 0 ; i < 3 ; i++ ) 
			{
				double translated = actualFrequency / (double) ( 1 << i );
				double deltaFreq = Math.abs( k.getTargetFrequency() - translated );
				if ( best == null || deltaFreq < bestDelta ) {
					best = k;
					bestDelta = deltaFreq;
					translatedActualFrequency = translated;
				}
			}
			for ( int i = 0 ; i < 3 ; i++ ) 
			{
				double translated = actualFrequency * (double) ( 1 << i );
				double deltaFreq = Math.abs( k.getTargetFrequency() - translated );
				if ( best == null || deltaFreq < bestDelta ) {
					best = k;
					bestDelta = deltaFreq;
					translatedActualFrequency = translated;
				}
			}			
		}
		
		best = best.withActualFrequency( translatedActualFrequency );
		
		if ( best.equals( TuningHelper.E2 ) || best.equals( TuningHelper.E4 ) ) 
		{
			// E2 and E4 share the same harmonics so the above algorithm will always pick E2 because
			// it's compared first ; see whether the measured frequency is closer to the fundamental frequency
			// of E2 or E4
			double delta1 = Math.abs( TuningHelper.E2.getTargetFrequency() - actualFrequency );
			double delta2 = Math.abs( TuningHelper.E4.getTargetFrequency() - actualFrequency );
			if ( delta1 < delta2 ) {
				System.out.println("delta1: "+delta1+" / delta2: "+delta2+" => picking E2");
				best = TuningHelper.E2.withActualFrequency( translatedActualFrequency );
			} else {
				System.out.println("delta1: "+delta1+" / delta2: "+delta2+" => picking E4");
				best = TuningHelper.E4.withActualFrequency( translatedActualFrequency );
			}
		} 
		return best;
	}	
	
	public synchronized void setFrequency(double[] top) 
	{
		
		// sort frequencies ascending
		
		final double[] topFrequencies = new double[top.length];
		System.arraycopy( top , 0 , topFrequencies , 0 , top.length );
	    Arrays.sort( topFrequencies );
	    
	    // check if we find at least one more harmonic for any
	    // of the strongest frequencies
	    
		Double frequency = null;
	    final int len = topFrequencies.length;
	    for ( int i = 0 ; i < len ; i++ ) 
	    {
	    	for ( int harmonic = 1 ; harmonic < 3 ; harmonic++) 
	    	{
	    		double f = topFrequencies[i] * (double) ( 1 << harmonic );
	    		for ( int j = i+1 ; j < len ; j++ ) 
	    		{
	    			double delta = Math.abs( topFrequencies[j] - f );
	    			if ( delta < 5 )  // TODO: arbitrary delta of 5 Hz
	    			{ 
	    				if ( top[0] == topFrequencies[j] ) 
	    				{
	    					frequency = topFrequencies[j];
	    				} else {
	    					frequency = topFrequencies[i];
	    				}
	    			}
	    		}
	    	}
	    }
	    
	    if ( frequency == null ) {
	    	return;
	    }
	    
		System.out.println("Strongest frequencies: "+ArrayUtils.toString( top )+" (picked: "+frequency+")" );
		
		final Key key = getGuitarKey( frequency );
		
		this.label = key.getName();
		
		final Key next = key.nextKey();
		double delta = next.getTargetFrequency() - TuningHelper.getKey( key.getKeyIndex() ).getTargetFrequency();
		// 100 cent = 1 half-tone
		final double maxDeviation = delta*0.5; // 1/2 half-tone max. resolution
		
		System.out.println("freq = "+key.getActualFrequency()+" => "+key+" ( min: "+(key.getTargetFrequency() - maxDeviation)+" / max: "+(key.getTargetFrequency()+maxDeviation));
		
		setValue( key.getActualFrequency() , key.getTargetFrequency() , maxDeviation );
	}
	
	public void setValue(double actual,double desired,double maxDeviation) 
	{
		final double deviation = -(desired-actual);
		
		double percentage = Math.abs(desired-actual) / maxDeviation;
		
		if ( percentage > 1.0 ) {
			percentage = 1.0;
		} else if ( percentage < 0 ) {
			percentage = 0;
		}
		
		if ( deviation >= 0 ) { // actual > target
			targetAngle = ZERO_ANGLE+90*percentage;
		} else {
			targetAngle = ZERO_ANGLE-90*percentage;
		}
		System.out.println("actual: "+actual+" / desired: "+desired+"/ target angle: "+targetAngle+" / deviation: "+deviation+" ("+percentage+")");
	}

	public void terminate() 
	{
		updateThread.terminate();
	}
	
	@Override
	protected void paintComponent(Graphics g) 
	{
		super.paintComponent(g);

		final double minExtend = Math.min( getHeight() , getWidth() );
		
		paintDial( (Graphics2D) g , minExtend );
	}

	private void paintDial(Graphics2D g, double extend)
	{
		final double angleInRad = currentAngle*DEG_TO_RAD;
		double radius = (extend/2.0) * 0.9;
		
		int xOrigin = (int) Math.round( extend / 2.0 );
		int yOrigin = (int) Math.round( extend / 2.0 );
		
	    double x = xOrigin + radius*Math.cos( angleInRad );
	    double y = yOrigin + radius*Math.sin( angleInRad );
	    
	    g.setColor(Color.BLACK);
	    g.drawArc( xOrigin-(int) Math.round(radius) , yOrigin-(int) Math.round(radius) , (int) Math.round(radius*2) , (int) Math.round(radius*2) , 0 , 360 );
	    
	    g.setColor(Color.RED);
	    g.drawLine( xOrigin, yOrigin , (int) Math.round(x) , (int) Math.round( y ) );
	    
	    // paint label
	    final String s = this.label;
	    if ( s != null ) 
	    {
	    	final Rectangle2D bounds = g.getFontMetrics().getStringBounds( s , g );
	    	final int stringX = (int) Math.round( xOrigin - bounds.getWidth() / 2.0 );
	    	final int stringY = (int) Math.round( yOrigin + bounds.getHeight() + 1 );
	    	
	    	g.setColor(Color.BLACK );
	    	g.drawString( s , stringX , stringY );
	    	g.drawRect( stringX-1 , yOrigin+2 , (int) Math.ceil( bounds.getWidth() + 2 ) , (int) Math.ceil( bounds.getHeight() +2 ) );
	    }
	}
	
	protected final class UpdateThread extends Thread 
	{
		private final CountDownLatch latch = new CountDownLatch(1);
		private volatile boolean terminate = false;
		
		public UpdateThread() {
			setDaemon(true);
			setName("TunerPanel-UpdateThread");
		}
		
		@Override
		public void run() 
		{
			try 
			{
				final int ADJUSTMENT_TIME_MILLIS = 500;
				
				while ( ! terminate ) 
				{
					final double target = targetAngle;
					final double delta = target-currentAngle;
					if ( Math.abs(delta) > EPSILON ) 
					{
						final double steps = ADJUSTMENT_TIME_MILLIS / (double) REFRESH_INTERVAL_MILLIS;
						final double angleIncrement = delta / steps;

						currentAngle += angleIncrement;
						try {
							SwingUtilities.invokeAndWait( new Runnable() {

								@Override
								public void run() 
								{
									TunerPanel.this.repaint();
								}
							});
						} 
						catch (Exception e) 
						{
							e.printStackTrace();
						}
					}
					
					try {
						Thread.sleep(REFRESH_INTERVAL_MILLIS);
					} 
					catch(Exception e) { }
				}
			} finally {
				latch.countDown();
			}
		}
		
		public void terminate() {
			this.terminate = true;
			this.interrupt();
			try {
				latch.await();
			} catch (InterruptedException e) {
			}
		}
	};	
}
