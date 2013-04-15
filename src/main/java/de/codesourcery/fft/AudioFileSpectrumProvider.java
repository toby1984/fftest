package de.codesourcery.fft;

import java.io.File;
import java.io.FileNotFoundException;

import de.codesourcery.fft.AudioFile.AudioDataIterator;

public class AudioFileSpectrumProvider extends AbstractSpectrumProvider
{
    private volatile AudioFile file;
    
    public AudioFileSpectrumProvider(AudioFile file,File waveFile) throws FileNotFoundException 
    {
        super(file.getFormat(),waveFile);
        this.file = file;
    }
    
    public AudioFile getAudioFile()
    {
        return file;
    }
    
    public void setAudioFile(AudioFile file)
    {
        this.file = file;
        invalidateCache();
    }
    
    protected final double[] getData() 
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
//        final int totalSampleCount = 16384;
        
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
        return jointStereo;
    }

	@Override
	public boolean isStatic() {
		return true;
	}
}
