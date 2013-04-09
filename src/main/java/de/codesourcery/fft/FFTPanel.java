package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

import javax.swing.JFrame;
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
    		} else if ( e.getKeyChar() == '+' && windowSize < 32768 ) {
    			windowSize = windowSize << 1;
    			calcSpectrum();
    			repaint();    			
    		} else if ( e.getKeyChar() == '-' && windowSize > 1 ) 
    		{
    			windowSize = windowSize >> 1;
    			calcSpectrum();
    			repaint();
        	}    		
    	}
	};
	
    private final MouseAdapter adapter = new MouseAdapter() 
    {
        @Override
        public void mouseMoved( MouseEvent e)
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
    };
    
    public void attachKeyListener(JFrame c) {
    	c.addKeyListener( this.keyAdapter );
    }

    public FFTPanel(AudioFile file,int windowSize)
    {
    	setFile(file,windowSize);
    }
    
    public void setFile(AudioFile file,int windowSize) {
        this.file = file;
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
        double max = Long.MIN_VALUE;
        double min = Long.MAX_VALUE;

        for ( int i = 0 ; i < bands ; i++ ) 
        {
            if ( spectrum[i] < min ) {
                min = spectrum[i];
            }
            if ( spectrum[i] > max ) {
                max = spectrum[i];
            }            
        }

        this.minValue = min; 
        this.maxValue = max;

        System.out.println("bands: "+this.bands+" / min = "+minValue+" / max = "+maxValue);      
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
        if ( currentMarkerX >= xOrigin ) 
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

        int x = 0;
        for ( int band = 0 ; band < bands ; band++, x+= Math.ceil( scaleX ) ) 
        {
            double value = spectrum[band]+yOffset;
            final double y = value*scaleY;

            final double frequency = getFrequencyForBand( band );
            
            g.drawRect( xOrigin + x , (int) Math.round( yOrigin - y ) , barWidthInPixels , (int) Math.round( y ) );

            if ( bands < 32 ) 
            {
                // draw label
                final String f = AudioFile.hertzToString( frequency );
                final Rectangle2D bounds = g.getFontMetrics().getStringBounds( f , g );

                g.drawString( f , xOrigin + x + barWidthInPixels/2 - (int) Math.round(bounds.getWidth()/2) , yOrigin + g.getFontMetrics().getHeight() );
            }
        }
    }

    private double getFrequencyForBand(int band) 
    {
        double bandWidth = file.getFormat().getSampleRate()/2.0/bands;
        final double freqInHertz = band * bandWidth;
        return freqInHertz;
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

        final int channels = file.getFormat().getChannels();
        final double[] jointStereo = new double[ totalSampleCount ]; // 16-bit samples
        for ( int channel = 0 ; channel < channels ; channel++) 
        {
            final AudioDataIterator it = file.iterator( data , channel ); 
            for ( int j = 0 ; j < totalSampleCount ; j++ ) {
                    jointStereo[j] += it.next();
            }
        }

        for ( int j = 0 ; j < totalSampleCount ; j++ ) {
            jointStereo[j] /= channels;
        }
        
        // apply windowing function        
        if ( applyWindowingFunction ) {
            applyWindowingFunction( jointStereo );
        }        

        final double[] fftData = new double[ windowSize * 2 ]; // need to allocate twice the FFT size since fftData[0] = Re(0) , fftData[1] = Img(0) , ...
        final double[] spectrum = new double[ windowSize ]; 

        // loop over samples , performing an FFT on each window 
        int windowCount = 0;
        final DoubleFFT_1D fft = new DoubleFFT_1D(windowSize);
        
        for ( int offset = 0 ; offset < jointStereo.length-windowSize ; offset += windowSize ) 
        {
        	// convert sample data to a double[] array
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
            for ( int i = 0 ; i < windowSize ; i++ , ptr+=2) 
            {
            	final double magnitude;
            	if ( i != 0 ) {
            		magnitude = Math.sqrt( fftData[ptr] * fftData[ptr] + fftData[ptr+1]*fftData[ptr+1] );
            	} else {
            		// special case since FFT(0) and FFT(N/2) do not have a complex component,
            		// JTransform stores these as element(0) and element(1)
            		magnitude = fftData[ptr];
            	}
//                spectrum[i] += 10*Math.log10( magnitude*magnitude );
                spectrum[i] += (magnitude*magnitude);
            }
            windowCount++;
        }

        for ( int i = 0 ; i < windowSize ; i++ ) {
            spectrum[i] = spectrum[i] / (double) windowCount;
        }
        return spectrum;
    }
    
    private void applyWindowingFunction(double[] data) 
    {
        final int len = data.length;
        for ( int ptr = 0 ; ptr < len ; ptr++ ) // data[k] = Re(k) , data[k+1=Img(k)
        {
            final double n = ptr % windowSize;
            // Hann window
//            double coeff = 0.5f*( 1.0f - (float) Math.cos( (2f*Math.PI*n) / ( windowSize-1 ) ) );
             double coeff = 0.5 - 0.5*Math.cos( (2f*Math.PI*n) / (double) ( windowSize-1 ) );                
            // Welch window
//            double coeff = 1-( (n-((windowSize-1)/2 ) ) / ((windowSize+1)/2) );            
            data[ptr] = data[ptr] * coeff;
        }
    }    
}