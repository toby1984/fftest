package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

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
    
    private final ICallback repaintCallback = new ICallback() {

        @Override
        public void calculationFinished(ISpectrumProvider provider, Spectrum spectrum)
        {
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

    private final AudioFileSpectrumProvider spectrumProvider;

    private final KeyAdapter keyListener = new KeyAdapter() 
    {
        public void keyTyped(java.awt.event.KeyEvent e) 
        {
            if ( e.getKeyChar()== 'w') {
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

    public void attachKeyListener(Component c) 
    {
        c.addKeyListener( this.keyListener );
    }

    public SpectrumPanel(AudioFile file,int windowSize)
    {
        this.spectrumProvider = new AudioFileSpectrumProvider( file );
        this.bands = windowSize/2;
        addMouseMotionListener( mouseListener );
        refresh();
    }

    public void setFile(AudioFile file) 
    {
        spectrumProvider.setAudioFile( file );
        refresh();
    }
    
    public void refresh() 
    {
        spectrumProvider.calcSpectrum( repaintCallback , this.bands*2 , this.applyWindowFunction );        
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
        final Spectrum[] result = {null};

        final ICallback callback = new ICallback() 
        {

            @Override
            public void calculationFinished(ISpectrumProvider provider, Spectrum spectrum)
            {
                synchronized( result ) 
                {
                    result[0] = spectrum;
                }
            }

            @Override
            public void calculationFailed(ISpectrumProvider provider) { }
        };
        
        spectrumProvider.calcSpectrum( callback , this.bands*2 , this.applyWindowFunction );
        
        synchronized( result ) {
            return result[0];
        }
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
            final int x = (int) Math.floor( xOrigin + band*scaleX); 

            double value = spectrum[band]+yOffset;
            final double y = value*scaleY;

            final double frequency = getFrequencyForBand( band );

            g.fillRect( x , (int) Math.round( yOrigin - y ) , barWidthInPixels , (int) Math.round( y ) );

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
        return spectrumProvider.getAudioFile().getFormat().getSampleRate()/2.0/bands;
    }
}