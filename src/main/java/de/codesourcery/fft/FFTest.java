package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.NoSuchElementException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JFrame;
import javax.swing.JPanel;

import de.codesourcery.fft.FFTest.AudioFile.AudioDataIterator;
import edu.emory.mathcs.jtransforms.fft.DoubleFFT_1D;

public class FFTest
{
    public static final int WINDOW_SIZE_IN_SAMPLES = 512;

    public static void main(String[] args) throws Exception
    {
        AudioFile file = new AudioFile("/home/tgierke/workspace/fftest/src/main/resources/5000hz.wav");
        System.out.println( file );

        final double[] fftResult = calculateFFT(file,true);

        plotFFTResult(file,fftResult,WINDOW_SIZE_IN_SAMPLES);
    }



    private static double[] calculateFFT(AudioFile file,boolean applyWindowingFunction) throws IOException, UnsupportedAudioFileException
    {
        final byte[] data = file.readFrames( 0 , (int) file.getTotalFrameCount() );

        final int totalSampleCount = (int) Math.ceil( file.getTotalFrameCount()* ( file.getSamplesPerFrame() / file.getFormat().getChannels() ));
        System.out.println("Total samples: "+totalSampleCount);

        final int channels = file.getFormat().getChannels();
        final int[] jointStereo = new int[ totalSampleCount ]; // 16-bit samples
        for ( int channel = 0 ; channel < channels ; channel++) 
        {
            final AudioDataIterator it = file.iterator( data , channel ); 
            for ( int j = 0 ; j < totalSampleCount ; j++ ) {
                try 
                {
                    jointStereo[j] += it.next();
                } catch(NoSuchElementException e) {
                    System.err.println("At index "+j+" using channel #"+channel);
                }
            }
        }

        for ( int j = 0 ; j < totalSampleCount ; j++ ) {
            jointStereo[j] /= channels;
        }

        // apply windowing function
        final double[] fftData = new double[ WINDOW_SIZE_IN_SAMPLES * 2 ]; // fftData[0] = Re(0) , fftData[1] = Img(0) , ...
        final double[] fftResult = new double[ WINDOW_SIZE_IN_SAMPLES ]; // we'll only store the Math.sqrt( (j+ki)^2 ) from the fft
        
        if ( applyWindowingFunction ) {
            applyWindowingFunction( fftData , WINDOW_SIZE_IN_SAMPLES );
        }

        // apply FFT
        int windowCount = 0;
        final DoubleFFT_1D fft = new DoubleFFT_1D(WINDOW_SIZE_IN_SAMPLES);
        for ( int offset = 0 ; offset < jointStereo.length-WINDOW_SIZE_IN_SAMPLES ; offset += WINDOW_SIZE_IN_SAMPLES ) 
        {
            int ptr = 0;
            for ( int i = 0 ; i < WINDOW_SIZE_IN_SAMPLES ; i++, ptr+=2 ) {
                fftData[ptr]=jointStereo[offset+i];
                fftData[ptr+1]=0;
            }
            fft.complexForward( fftData );

            ptr = 0;
            for ( int i = 0 ; i < WINDOW_SIZE_IN_SAMPLES ; i++ , ptr+=2) 
            {
                final double magnitude = Math.sqrt( fftData[ptr]* fftData[ptr] + fftData[ptr+1]*fftData[ptr+1] );
                fftResult[i] += (magnitude*magnitude);
            }
            windowCount++;
        }

        for ( int i = 0 ; i < WINDOW_SIZE_IN_SAMPLES ; i++ ) {
//            fftResult[i] = 10*Math.log10( fftResult[i] / windowCount );
            fftResult[i] = fftResult[i] / windowCount;
        }
        return fftResult;
    }
    

    private static void plotFFTResult(final AudioFile file , final double[] fftResult,final int windowSizeInSamples) 
    {
        final JPanel panel = new FFTPanel( file ); 

        final JFrame frame = new JFrame("FFT");
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx=1.0;
        cnstrs.weighty=1.0;
        frame.setSize( new Dimension(600,400 ) );
        frame.setPreferredSize( new Dimension(600,400 ) );
        frame.getContentPane().setLayout( new GridBagLayout() );
        frame.getContentPane().add( panel , cnstrs );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        frame.setVisible( true );
    }

    private static void applyWindowingFunction(double[] data,int N) 
    {
        final int len = data.length;
        int j = 0;
        for ( int ptr = 0 ; ptr < len ; ptr+= 2, j++ ) // data[k] = Re(k) , data[k+1=Img(k)
        {
            final double n = j % N;
            /*
             * Hann window:
              w(n) = 0.5\; \left(1 - \cos \left ( \frac{2 \pi n}{N-1} \right) \right) 
             */              
            double coeff = 0.5f*( 1.0f + (float) Math.cos( 2f*Math.PI*n / ( N-1 ) ) );     
            /*
             * Welch window
             */
//            double coeff = 1-( (n-((N-1)/2 ) ) / ((N+1)/2) );            
            data[ptr] = data[ptr] * coeff;
        }
    }

    protected static final class AudioFile 
    {
        private final File file;
        private final AudioFormat format;
        private final long totalFrameCount;

        protected final class AudioDataIterator {

            private int offset;
            private final byte[] data;
            private final int strideInBytes;
            private final int sampleSizeInBytes;
            private final boolean bigEndian;

            public AudioDataIterator(byte[] data,int channel) 
            {
                this.data = data;
                this.sampleSizeInBytes = (int) Math.ceil( format.getSampleSizeInBits() / 8.0f);
                this.strideInBytes = sampleSizeInBytes * format.getChannels();
                this.bigEndian = format.isBigEndian();
                this.offset = channel*sampleSizeInBytes; 
                System.out.println("Iterator on channel #"+channel+" , stride: "+strideInBytes+" , offset: "+offset+" , sampleSizeInBytes: "+sampleSizeInBytes);
            }

            public boolean hasNext() 
            {
                return offset < data.length;
            }

            public int next() 
            {
                if ( ! hasNext() ) {
                    throw new NoSuchElementException();
                }
                int result = 0;

                if ( ! bigEndian ) 
                {
                    for ( int i = 0 ; i < sampleSizeInBytes ; i++ ) {
                        result = result >> 8;
                    result |= data[offset+i];
                    }
                } 
                else 
                {
                    for ( int i = 0 ; i < sampleSizeInBytes ; i++ ) {
                        result = result << 8;
                        result |= data[offset+i];
                    }
                }
                offset += strideInBytes;
                return result;
            }
        }        

        public AudioFile(File file) throws IOException, UnsupportedAudioFileException
        {
            final AudioInputStream audioInputStream =  AudioSystem.getAudioInputStream(file);
            this.file = file;
            this.format = audioInputStream.getFormat();
            this.totalFrameCount = audioInputStream.getFrameLength();
        }

        public static final String hertzToString(double hertz) {

            if ( hertz < 1000 ) 
            {
                DecimalFormat df = new DecimalFormat("#########0.0#");
                return df.format( hertz )+" hz";
            } 
            if ( hertz < 1000000 ) {
                DecimalFormat df = new DecimalFormat("#########0.0#");
                return df.format( hertz/1000d )+" KHz";                
            }
            DecimalFormat df = new DecimalFormat("##########0.0##");
            return df.format( hertz/1000000d )+" MHz";                
        }

        public int getBytesPerSample() {
            return (int) Math.ceil( format.getSampleSizeInBits()/ 8.0f);
        }

        public long getTotalFrameCount() {
            return totalFrameCount;
        }

        public float getSamplesPerFrame() {
            return format.getFrameSize() / (float) getBytesPerSample();
        }

        public AudioDataIterator iterator( byte[] data, int channel) 
        {
            return new AudioDataIterator(data,channel);
        }

        public long getDurationInMillis() 
        {
            return (long) Math.ceil( getTotalFrameCount() / format.getFrameRate() * 1000.0f / format.getChannels() );
        }

        public AudioFile(String string) throws IOException, UnsupportedAudioFileException
        {
            this(new File(string) );
        }

        public AudioFormat getFormat()
        {
            return format;
        }

        @Override
        public String toString()
        {
            String result = rightPad( "File: ")+file.getAbsolutePath();
            result += "\n"+rightPad( "Total frames: ")+getTotalFrameCount();
            result += "\n"+rightPad( "Duration/ms: ")+getDurationInMillis();
            result += "\n"+rightPad( "Encoding: ")+format.getEncoding();
            result += "\n"+rightPad( "Channels: ")+format.getChannels();
            result += "\n"+rightPad( "Endianess: ")+(format.isBigEndian() ? "big-endian" : "little-endian");
            result += "\n"+rightPad( "Frames/s: ")+format.getFrameRate();
            result += "\n"+rightPad( "Bytes/Frame: ")+format.getFrameSize();
            result += "\n"+rightPad( "Sample rate: ")+format.getSampleRate();
            result += "\n"+rightPad( "Bits/sample: ")+format.getSampleSizeInBits(); // getSamplesPerFrame
            result += "\n"+rightPad( "Samples/Frame: ")+getSamplesPerFrame();
            return result;
        }

        private static String rightPad(String s) 
        {
            final int len = 20;
            if ( s.length() >= len ) {
                return s;
            }
            String result = s;
            for ( int i = len - s.length() ; i > 0 ; i-- ) {
                result += " ";
            }
            return result;
        }


        public int calcFrameCountForDuration(int durationInMillis) 
        {
            float durationInSeconds = durationInMillis / 1000.0f;
            return (int) Math.ceil( format.getFrameRate()* durationInSeconds );
        }

        public int getFrameSizeInBytes() 
        {
            final int bytesPerFrame =  format.getFrameSize();
            if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) // some audio formats may have unspecified frame size
            {
                throw new IllegalStateException("Input file "+file.getAbsolutePath()+" has unspecified frame size");
            } 
            return bytesPerFrame;
        }

        public int calcFrameStartOffsetInMilliseconds(int frameNumber) 
        {
            format.getFrameRate(); // frames per second
            return Math.round( (frameNumber / format.getFrameRate() )*1000.0f );
        }

        public byte[] readFrames(int offset,int numOfFrames) throws IOException, UnsupportedAudioFileException {

            if ( offset < 0 ) {
                throw new IllegalArgumentException("Invalid offset "+offset);
            }
            if ( numOfFrames < 1 ) {
                throw new IllegalArgumentException("Invalid frame count "+numOfFrames);
            }

            final byte[] buffer = new byte[ numOfFrames * getFrameSizeInBytes() ];
            readFrames(offset,numOfFrames,buffer);
            return buffer;
        }

        public void readFrames(int offset,int numOfFrames, byte[] buffer) throws IOException, UnsupportedAudioFileException 
        {
            final AudioInputStream audioInputStream =  AudioSystem.getAudioInputStream(file);

            final int bytesPerFrame =  audioInputStream.getFormat().getFrameSize();
            if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) // some audio formats may have unspecified frame size
            {
                throw new IllegalStateException("Input file "+file.getAbsolutePath()+" has unspecified frame size");
            } 

            final byte[] audioBytes = new byte[bytesPerFrame];

            // advance to desired frame
            for ( int i = 0 ; i < offset ; i++ ) {
                int read = audioInputStream.read(audioBytes);
                if ( read == -1 || read != bytesPerFrame ) {
                    throw new IOException("Internal error, failed to read frame #"+i+" from "+file.getAbsolutePath());
                }
            }

            for ( int i = 0 ; i < numOfFrames ; i++ ) 
            {
                final int read = audioInputStream.read(buffer,i*bytesPerFrame , bytesPerFrame );
                if ( read == -1 || read != bytesPerFrame ) {
                    throw new IOException("Internal error, failed to read frame #"+(i+offset)+" from "+file.getAbsolutePath());
                }
            }
        }        
    }
    
    private static final class FFTPanel extends JPanel {

        private volatile int currentMarkerX = -1;

        private volatile int width;
        private volatile int height;
        private volatile int xOrigin;
        private volatile int yOrigin;
        private volatile double scaleX;
        private volatile double scaleY;
        
        private final AudioFile file;

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

        public FFTPanel(AudioFile file,int windowSize)
        {
            this.file = file;
            addMouseMotionListener( adapter );
            calcFFT();
        }
        
        private void calcFFT() 
        {
            // only plot N/2 bands because of the DFTs symmetry
            final int bands = (fftResult.length/2)+1;

            // find min/max values
            double max = Long.MIN_VALUE;
            double min = Long.MAX_VALUE;

            for ( int i = 0 ; i < bands ; i++ ) 
            {
                if ( fftResult[i] < min ) {
                    min = fftResult[i];
                }
                if ( fftResult[i] > max ) {
                    max = fftResult[i];
                }            
            }

            final double minValue = min; 
            final double maxValue = max;

            System.out.println("min = "+minValue);
            System.out.println("max = "+maxValue);            
        }

        private void resized() 
        {
            this.width = Math.round(getWidth()*0.8f);
            this.height = Math.round(getHeight()*0.9f);

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
                final String f2 = AudioFile.hertzToString( getFrequencyForBand(band+1) );
                g.setColor(Color.BLACK);
                g.drawString(  f1+" - "+f2 , x, y );
            } 
        }

        private void plotChart(Graphics g) 
        {
            g.setColor(Color.RED);

            final double yOffset = minValue > 0 ? -minValue : minValue;

            final int barWidthInPixels = (int) Math.round(scaleX);                

            int x = 0;
            System.out.println("Drawing "+bands+" bands");
            for ( int band = 0 ; band < bands ; band++, x+= Math.ceil( scaleX ) ) 
            {
                double value = fftResult[band]+yOffset;
                final double y = value*scaleY;

                g.drawRect( xOrigin + x , (int) Math.round( yOrigin - y ) , barWidthInPixels , (int) Math.round( y ) );

                if ( bands < 16 ) 
                {
                    // draw label
                    final String f = AudioFile.hertzToString( getFrequencyForBand(band) );
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
    }
}
