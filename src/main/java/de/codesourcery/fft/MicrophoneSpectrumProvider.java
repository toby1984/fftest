package de.codesourcery.fft;

import java.util.concurrent.atomic.AtomicLong;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class MicrophoneSpectrumProvider extends AbstractSpectrumProvider {

	private final AudioFormat format;

	private final AudioProvider provider;

	private final AtomicLong bytesRead = new AtomicLong(0);
	private final byte[] tmpBuffer;

	public MicrophoneSpectrumProvider(AudioFormat format, int bufferSizeInSamples) throws LineUnavailableException 
	{
		this.format = format;
		final TargetDataLine line = AudioSystem.getTargetDataLine(format);	
		provider = new AudioProvider(line,format,bufferSizeInSamples,20);
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
	public AudioFormat getAudioFormat() {
		return format;
	}

	@Override
	protected double[] getData() 
	{
		try {
			return internalGetData();
		} catch(Exception e) {
			e.printStackTrace();
			return new double[0];
		}
	}
	
	protected double[] internalGetData() 
	{		
		final int sampleBytes = format.getSampleSizeInBits()/8;
		
		final int bufferSize = provider.getBufferSizeInBytes() / sampleBytes;
		
		final int offset;
		if ( format.getEncoding() == Encoding.PCM_SIGNED ) 
		{
			switch (sampleBytes ) {
				case 1:
					offset = 128;
					break;
				case 2:
					offset = 32768;
					break;
				default:
					throw new RuntimeException("Unhandled sample size: "+sampleBytes);
			}
		} 
		else {
			offset = 0;
		}

		double min = Long.MAX_VALUE;
		double max = Long.MIN_VALUE;
		
		final double[] buffer = new double[ bufferSize ];
		try 
		{
			final int bytesRead = provider.readFrame( tmpBuffer );
			this.bytesRead.addAndGet( bytesRead );

			int bytePtr = 0;
			if ( ! format.isBigEndian() ) 
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
					min = Math.min( min , buffer[wordPtr]);
					max = Math.max( max , buffer[wordPtr]);
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
					min = Math.min( min , buffer[wordPtr]);
					max = Math.max( max , buffer[wordPtr]);					
				}
			}
		} 
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
//		System.out.println("Bytes/sample: "+sampleBytes+" , buffer size: "+ provider.getBufferSizeInBytes()+" bytes , output buffer: "+bufferSize+" / offset: "+offset+" / min: "+min+" / max: "+max);
				
		return buffer;
	}

	@Override
	public boolean isStatic() {
		return false;
	}
}