package de.codesourcery.fft;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class MicrophoneSpectrumProvider extends AbstractSpectrumProvider {

	private final MicrophoneAudioProvider provider;

	private final AtomicLong bytesRead = new AtomicLong(0);
	private final byte[] tmpBuffer;
	
	public MicrophoneSpectrumProvider(AudioFormat format, int bufferSizeInSamples,File waveFile,File micInRawFile) throws LineUnavailableException, FileNotFoundException 
	{
	    super(format,waveFile);
		final TargetDataLine line = AudioSystem.getTargetDataLine(format);	
		provider = new MicrophoneAudioProvider(line,format,bufferSizeInSamples,100, micInRawFile );
		tmpBuffer = new byte[ provider.getBufferSizeInBytes() ];
	}
	
	public void start() {
		provider.startCapturing();
	}

	public void close() 
	{
		super.close();
		provider.close();
		System.out.println("Bytes read: "+bytesRead);
		System.out.println("Bytes lost: "+provider.getLostBytesCount());
	}

	@Override
	protected SampleData getData() 
	{
		final int sampleBytes = getAudioFormat().getSampleSizeInBits()/8;
		
		final int bufferSize = provider.getBufferSizeInBytes() / sampleBytes;
		
		double min = 0;
		double max = 0;
		
		double[] buffer = new double[ bufferSize ];
		try 
		{
			final int bytesRead = provider.readFrame( tmpBuffer );
			this.bytesRead.addAndGet( bytesRead );

			int bytePtr = 0;
			if ( ! getAudioFormat().isBigEndian() ) 
			{
				// little endian
				for ( int wordPtr = 0 ; wordPtr < bufferSize ; wordPtr++ ) 
				{
					int value = 0;
					for ( int i = 0 ; i < sampleBytes ; i++ ) { 
						value = value << 8;
						int byteValue = tmpBuffer[bytePtr+i];
						value |= (byteValue % 0xff);
					}
					bytePtr += sampleBytes;
					buffer[wordPtr]=value;
					if ( value < min ) {
						min = value;
					}
					if ( value > max ) {
						max = value;
					}
				}
			}
			else 
			{
				// big endian
				for ( int wordPtr = 0 ; wordPtr < bufferSize ; wordPtr++ ) 
				{
					int value = 0;
					for ( int i = sampleBytes-1 ; i >= 0 ; i-- ) {
						value = value << 8;
						int byteValue = tmpBuffer[bytePtr+i];
						value |= (byteValue & 0xff);
					}
					bytePtr += sampleBytes;
					buffer[wordPtr]=value;
					if ( value < min ) {
						min = value;
					}
					if ( value > max ) {
						max = value;
					}					
				}
			}
		} 
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
//		System.out.println("Bytes/sample: "+sampleBytes+" , buffer size: "+ provider.getBufferSizeInBytes()+" bytes , output buffer: "+bufferSize+" / offset: "+offset+" / min: "+min+" / max: "+max);
		return new SampleData(buffer ,min , max );
	}

	@Override
	public boolean isStatic() {
		return false;
	}
}