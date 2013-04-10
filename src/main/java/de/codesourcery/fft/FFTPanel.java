package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.JPanel;

import de.codesourcery.fft.AudioFile.AudioDataIterator;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public final class FFTPanel extends JPanel {

    private volatile int currentMarkerX = -1;

    private volatile int width;
    private volatile int height;
    private volatile int xOrigin;
    private volatile int yOrigin;
    private volatile double scaleX;
    private volatile double scaleY;
    
    private int windowSize;
    private AudioFile file;
    private double[] spectrum;
    private int bands;
    private double minValue;
    private double maxValue;
    private boolean applyWindowFunction=true;

    private final KeyAdapter keyAdapter = new KeyAdapter() 
    {
    	public void keyTyped(java.awt.event.KeyEvent e) 
    	{
    		if ( e.getKeyChar()== 'w') {
    			applyWindowFunction = ! applyWindowFunction;
    			System.out.println("Apply window function: "+applyWindowFunction);
    			calcSpectrum();
    			repaint();
    		} else if ( e.getKeyChar() == '+' && windowSize <= 65535 ) {
    			windowSize = windowSize << 1;
    			calcSpectrum();
    			repaint();    			
    		} 
    		else if ( e.getKeyChar() == '-' && windowSize >= 4 ) 
    		{
    			windowSize = windowSize >> 1;
    			calcSpectrum();
    			repaint();
        	} else {
        	    System.out.println("IGNORED KEYPRESS: '"+e.getKeyChar()+"'");
        	}
    	}
	};
	
    private final MouseAdapter adapter = new MouseAdapter() 
    {
        @Override
        public void mouseMoved( MouseEvent e)
        {
            double maxX = xOrigin + bands*scaleX;
            if ( e.getX() >= xOrigin && e.getX() < maxX ) {
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
    
    public void attachKeyListener(Component c) 
    {
    	c.addKeyListener( this.keyAdapter );
    }

    public FFTPanel(AudioFile file,int windowSize)
    {
    	internalSetFile(file,windowSize);
    }
    
    public void setFile(AudioFile file) {
        internalSetFile(file,windowSize);
        repaint();
    }
    
    private void internalSetFile(AudioFile file,int windowSize) {
        this.file = file;
        System.out.println( file );
        this.windowSize = windowSize;
        addMouseMotionListener( adapter );
        calcSpectrum();    	
    }
    
    private void calcSpectrum() 
    {
    	this.spectrum = calculateFFT( this.file , this.windowSize , applyWindowFunction );
    	
        // only plot N/2 bands because of the DFTs symmetry
        this.bands = windowSize/2;

        // find min/max values
        double max = -Double.MAX_VALUE;
        double min = Double.MAX_VALUE;

        for ( int i = 0 ; i < bands ; i++ ) 
        {
            min = Math.min( min ,  spectrum[i] );
            max = Math.max( max , spectrum[i] );
        }

        this.minValue = min; 
        this.maxValue = max;

        System.out.println("bands: "+this.bands+" ("+AudioFile.hertzToString( getBandwidth() )+" per band ) / min = "+minValue+" / max = "+maxValue);      
    }

    private void resized() 
    {
        this.width = Math.round(getWidth()*0.8f);
        this.height = Math.round(getHeight()*0.8f);

        this.xOrigin = (int) Math.round( getWidth() * 0.1);
        this.yOrigin = Math.round( getHeight() *0.9f);

        this.scaleX = width / (double) bands;
        this.scaleY = height / Math.abs( maxValue - minValue );                  
    }

    @Override
    public void paint(Graphics g)
    {
        super.paint(g);
        
        currentMarkerX =  -1;

        resized();

        plotChart( g );
        plotMarkerFrequency(g);
    }

    private void plotMarkerFrequency(Graphics g)
    {
        // clear old text
        final int x = 5;
        final int y = 15;
        g.clearRect( x , y-15 , 150 , g.getFontMetrics().getHeight() );

        // render frequency at current marker position
        double maxX = xOrigin + bands * scaleX;
        if ( currentMarkerX >= xOrigin && currentMarkerX <= maxX ) 
        {
            final int band = (int) ( (currentMarkerX-xOrigin) / scaleX );

            final String f1 = AudioFile.hertzToString( getFrequencyForBand(band) );
            g.setColor(Color.BLACK);
            g.drawString(  f1 , x, y );
        } 
    }

    private void plotChart(Graphics g) 
    {
        g.setColor(Color.RED);

        final double yOffset = minValue > 0 ? -minValue : minValue;

        int barWidthInPixels = (int) Math.round(scaleX);
        if ( barWidthInPixels < 1 ) {
        	barWidthInPixels = 1;
        }

        for ( int band = 0 ; band < bands ; band++ ) 
        {
            final int x = (int) Math.floor( xOrigin + band*scaleX); 
            
            double value = spectrum[band]+yOffset;
            final double y = value*scaleY;

            final double frequency = getFrequencyForBand( band );
            
            g.drawRect( x , (int) Math.round( yOrigin - y ) , barWidthInPixels , (int) Math.round( y ) );

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
    
    private double getBandwidth() {
        return file.getFormat().getSampleRate()/2.0/bands;
    }
    
    private double[] calculateFFT(AudioFile file,int windowSize,boolean applyWindowingFunction) 
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
            applyWindowingFunction( jointStereo );
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
        return spectrum;
    }
    
    private void applyWindowingFunction(double[] data) 
    {
        final int len = data.length;
        for ( int ptr = 0 ; ptr < len ; ptr++ ) // data[k] = Re(k) , data[k+1=Img(k)
        {
            final double n = ptr % windowSize;
            double coeff = 0.5 - 0.5*Math.cos( (2f*Math.PI*n) / (double) ( windowSize-1 ) ); // Hann window               
//          double coeff = 1-( (n-((windowSize-1)/2 ) ) / ((windowSize+1)/2) ); // Welch window         
            data[ptr] = data[ptr] * coeff;
        }
    }    
}