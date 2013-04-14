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
	
	private final Filter lowPassFilter = new NOPFilter(); // new LowPassFilter(5000);
	private final Filter highPassFilter = new HighPassFilter(15000);

	protected abstract class Filter 
	{
		public abstract double[] filter(double[] data,double windowDurationInSeconds);
	}
	
	protected final class NOPFilter extends Filter {

		@Override
		public double[] filter(double[] data, double windowDurationInSeconds) {
			return data;
		}
	}
	
	protected final class LowPassFilter extends Filter {

		private final double RClow;
		
		public LowPassFilter(double cutOffFreq) {
			this.RClow = 0.5;// 1.0d/(2.0*Math.PI*cutOffFreq);
		}
		
		@Override
		public double[] filter(double[] data, double durationInSeconds) {
			final int len = data.length;
			/*
			 function lowpass(real[0..n] x, real dt, real RC)
			   var real[0..n] y
			   var real α := dt / (RC + dt)
			   y[0] := x[0]
			   for i from 1 to n
			       y[i] := y[i-1] + α * (x[i] - y[i-1])   
			   return y		 
			 */
			double[] result2 = new double[len];
			result2[0] = data[0];		
			double alphaLow = durationInSeconds / (RClow+durationInSeconds); 
			for ( int i = 1 ; i < len ; i++) {
				result2[i]= result2[i-1] + alphaLow*( data[i] - result2[i-1]);
			}

			return result2;
		}
	}
	
	protected final class HighPassFilter extends Filter {
		
		private final double RChigh;
		
		public HighPassFilter(double cutOffFreq) {
			this.RChigh = 0.9; // 1.0d/(2.0*Math.PI*cutOffFreq);
		}
		
		@Override
		public double[] filter(double[] data, double durationInSeconds) 
		{
			final int len = data.length;
			/*
	 // Return RC high-pass filter output samples, given input samples,
	 // time interval dt, and time constant RC
	 function highpass(real[0..n] x, real dt, real RC)
	   var real[0..n] y
	   var real α := RC / (RC + dt)
	   y[0] := x[0]
	   for i from 1 to n
	      y[i] := α * (y[i-1] + x[i] - x[i-1])
	   return y		 
			 */
			
			/*
			 * - a large α corresponds to a large RC and therefore a low corner frequency of the filter
			 * - a small α corresponds to a small RC and therefore a high corner frequency of the filter.
			 */
			double alphaHigh = RChigh / (RChigh+durationInSeconds); 
			double[] result = new double[len];
			result[0] = data[0];
			for ( int i = 1 ; i < len ; i++) {
				result[i]= alphaHigh*(result[i-1]+data[i] - data[i-1] );
			}
			return result;
		}
	}	
	
	public MicrophoneSpectrumProvider(AudioFormat format, int bufferSizeInSamples,boolean writeWaveFile) throws LineUnavailableException 
	{
		this.format = format;
		final TargetDataLine line = AudioSystem.getTargetDataLine(format);	
		provider = new AudioProvider(line,format,bufferSizeInSamples,20,writeWaveFile);
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
		
		double[] buffer = new double[ bufferSize ];
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
				
		double durationInSeconds = buffer.length / format.getSampleRate();		
		buffer = lowPassFilter.filter( buffer , durationInSeconds );
		buffer = highPassFilter.filter( buffer , durationInSeconds );
		return buffer;
	}

	@Override
	public boolean isStatic() {
		return false;
	}
}