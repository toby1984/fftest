package de.codesourcery.fft;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.NoSuchElementException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public final class AudioFile 
{
    private final InputStreamProvider inputProvider;
    private final AudioFormat format;
    private final long totalFrameCount;

    public static final class AudioDataIterator 
    {
        private int offset;
        private final byte[] data;
        private final int strideInBytes;
        private final int sampleSizeInBytes;
        private final boolean bigEndian;

        public AudioDataIterator(AudioFormat format, byte[] data,int channel) 
        {
            this.data = data;
            this.sampleSizeInBytes = (int) Math.ceil( format.getSampleSizeInBits() / 8.0f);
            this.strideInBytes = sampleSizeInBytes * format.getChannels();
            this.bigEndian = format.isBigEndian();
            this.offset = channel*sampleSizeInBytes; 
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

            if ( bigEndian ) 
            {
                for ( int i = 0 ; i < sampleSizeInBytes ; i++ ) {
                    result = result << 8;
                    result |= data[offset+i];
                }
            } 
            else 
            {
                for ( int i = 0 ; i < sampleSizeInBytes ; i++ ) {
                    result = result >> 8;
                result |= data[offset+i];
                }
            }
            offset += strideInBytes;
            return result;
        }
    }        

    public interface InputStreamProvider {

        public InputStream createStream() throws IOException;
    }

    public static AudioFile fromClassPath(final String path) throws IOException, UnsupportedAudioFileException 
    {
        final String stripped = path.startsWith("classpath:") ? path.substring( "classpath:".length() ) : path;

        return new AudioFile( new InputStreamProvider() 
        {

            @Override
            public InputStream createStream() throws IOException
            {
                InputStream result = getClass().getResourceAsStream( stripped );
                if ( result == null ) {
                    throw new FileNotFoundException("Failed to load audio from classpath:"+stripped);
                }
                return new BufferedInputStream(result);
            }
        });
    }

    public static AudioFile fromFile(final File path) throws IOException, UnsupportedAudioFileException 
    {
        return new AudioFile( new InputStreamProvider() {

            @Override
            public InputStream createStream() throws IOException
            {
                return new FileInputStream( path );
            }
        });
    }    

    public AudioFile(InputStreamProvider provider) throws IOException, UnsupportedAudioFileException
    {
        final InputStream in = provider.createStream(); 
        AudioInputStream audioInputStream = null;
        try {
            audioInputStream =  AudioSystem.getAudioInputStream(in);
        } 
        finally 
        {
            if ( audioInputStream != null ) {
                try {
                    audioInputStream.close();
                } catch(IOException e) {};                
            }
            try {
                in.close();
            } catch(IOException e) {};
        }

        this.inputProvider = provider;
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
        return new AudioDataIterator(format,data,channel);
    }

    public long getDurationInMillis() 
    {
        return (long) Math.ceil( getTotalFrameCount() / format.getFrameRate() * 1000.0f / format.getChannels() );
    }

    public AudioFormat getFormat()
    {
        return format;
    }

    @Override
    public String toString()
    {
        String result = rightPad( "Input: ")+inputProvider;
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
            throw new IllegalStateException("Input audio "+inputProvider+" has unspecified frame size");
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

        final InputStream in = inputProvider.createStream();
        AudioInputStream audioInputStream = null;
        try {
            audioInputStream =  AudioSystem.getAudioInputStream(in);

            final int bytesPerFrame =  audioInputStream.getFormat().getFrameSize();
            if (bytesPerFrame == AudioSystem.NOT_SPECIFIED) // some audio formats may have unspecified frame size
            {
                throw new IllegalStateException("Input audio has unspecified frame size");
            } 

            final byte[] audioBytes = new byte[bytesPerFrame];

            // advance to desired frame
            for ( int i = 0 ; i < offset ; i++ ) {
                int read = audioInputStream.read(audioBytes);
                if ( read == -1 || read != bytesPerFrame ) {
                    throw new IOException("Internal error, failed to read frame #"+i+" from "+inputProvider);
                }
            }

            for ( int i = 0 ; i < numOfFrames ; i++ ) 
            {
                final int read = audioInputStream.read(buffer,i*bytesPerFrame , bytesPerFrame );
                if ( read == -1 || read != bytesPerFrame ) {
                    throw new IOException("Internal error, failed to read frame #"+(i+offset)+" from "+inputProvider);
                }
            }
        } 
        finally 
        {
            try {
                in.close();
            } catch(IOException e) {}

            if ( audioInputStream != null ) {
                try {
                    audioInputStream.close();
                } catch(IOException e) {}        
            }
        }        
    }        
}