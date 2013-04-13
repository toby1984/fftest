package de.codesourcery.fft;

import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineEvent.Type;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

public class AudioProvider 
{
	private static final boolean DEBUG = true;
	private final TargetDataLine line;

	private final CaptureThread captureThread;

	public static void main(String[] args) throws LineUnavailableException, InterruptedException 
	{
//		Mixer defaultMixer=null;
//		for ( Info mixer : AudioSystem.getMixerInfo() ) 
//		{
//			logDebug("Got mixer: "+mixer.getName());
//			Mixer mixerInstance = AudioSystem.getMixer( mixer );
//			for ( javax.sound.sampled.Line.Info line : mixerInstance.getTargetLineInfo() )
//			{
//				logDebug("Got target line "+line);
//			}
//			
//			if ( defaultMixer == null )
//			{
//				defaultMixer = AudioSystem.getMixer( mixer );
//			}
//		}
//		
//		if ( defaultMixer == null ) {
//			throw new RuntimeException("Failed to locate default mixer");
//		}
		
		final AudioFormat format = new AudioFormat(8000, 16, 1, true , true);
		final TargetDataLine line = AudioSystem.getTargetDataLine(format);	
		
		Mixer defaultMixer = AudioSystem.getMixer( null );
		for ( javax.sound.sampled.Line.Info info : defaultMixer.getSourceLineInfo() ) 
		{
			System.out.println("Line info: "+info );
		}
		final AudioProvider provider = new AudioProvider(line,format,8000,20);
		
		long bytesRead = 0;
		try {
			byte[] buffer = new byte[ provider.getBufferSizeInBytes() ];
			provider.startCapturing();

			for ( int i = 0 ; i < 10 ; i++ ) {
				System.out.println("Reading frame");
				bytesRead += provider.readFrame( buffer );
				System.out.println("Read frame");
			}
		} 
		finally 
		{
			provider.close();
			System.out.println("Bytes read: "+bytesRead);
			System.out.println("Bytes lost: "+provider.getLostBytesCount());
		}
	}

	public AudioProvider(TargetDataLine line , AudioFormat format,int bufferSizeInSamples,int bufferCount) throws LineUnavailableException
	{
		this.line = line;
		captureThread = new CaptureThread( line , format , bufferSizeInSamples,bufferCount );
		captureThread.start();
	}

	protected final class CaptureThread extends Thread implements LineListener {

		private final Object LOCK = new Object();

		private final TargetDataLine line;
		
		private final RingBuffer ringBuffer;
		
		// @GuardedBy( LOCK )
		private boolean terminate;
		// @GuardedBy( LOCK )
		private boolean doCapture=false;
		
		public CaptureThread(TargetDataLine line,AudioFormat format,int bufferSizeInSamples,int bufferCount) throws LineUnavailableException 
		{
			setDaemon(true);
			setName("audio-capture-thread");
			this.line = line;
			
			System.out.println("Audio line buffer size: "+line.getBufferSize());
			
			final int bufferSizeInBytes = bufferSizeInSamples*(format.getSampleSizeInBits()/8);
			this.ringBuffer = new RingBuffer( bufferSizeInBytes , bufferCount );
			
			line.addLineListener( this );
			line.open(format);
		}

		public long getLostBytesCount() 
		{
			return ringBuffer.getBytesLost();
		}		

		public int getBufferSizeInBytes() {
			return ringBuffer.getBufferSize();
		}

		public boolean isCapturing() 
		{
			synchronized(LOCK) {
				return isAlive() && doCapture;
			}
		}
		
		private final RingBuffer.BufferWriter writer = new RingBuffer.BufferWriter() {
			
			@Override
			public int write(byte[] buffer, int bufferSize) 
			{
				return line.read(buffer, 0, bufferSize );
			}
		};

		@Override
		public void run() 
		{
			try {
				while( true)
				{
					synchronized(LOCK) 
					{
						if ( terminate ) 
						{
							break;
						}
						
						if ( ! doCapture ) 
						{
							logDebug("Capture thread sleeping");
							try {
								LOCK.wait();
							} 
							catch (InterruptedException e) 
							{
								Thread.currentThread().interrupt();
							}
							if ( doCapture ) {
								logDebug("Starting to capture");
							} else {
								logDebug("Capture thread woke up");
							}
							continue;
						}
					}

					ringBuffer.write( writer );
				}
			} 
			finally 
			{
				logDebug("Capture thread terminated");
				line.removeLineListener( this );
			}
		}

		public int read(byte[] targetBuffer) throws InterruptedException 
		{
			return ringBuffer.read( targetBuffer );
		}

		@Override
		public void update(LineEvent event) 
		{
			final Type t = event.getType();
			logDebug("Capture thread received: "+event);
			
			if ( t.equals( Type.CLOSE ) ) 
			{
				logDebug("Terminate capturing...");
				synchronized( LOCK ) {
					terminate = true;
					LOCK.notifyAll();
				}				
			}
			else if ( t.equals( Type.STOP ) ) 
			{
				logDebug("Stop capturing...");
				synchronized( LOCK ) {
					doCapture = false;
				}				
			} 
			else if ( t.equals( Type.START ) ) 
			{
				logDebug("Start capturing...");
				synchronized( LOCK ) {
					doCapture = true;
					LOCK.notifyAll();
				}				
			}
		}

		public void startCapturing() 
		{
			logDebug("Starting line");
			line.start();
			synchronized( LOCK ) 
			{
				doCapture = true;
				LOCK.notifyAll();
			}
		}
	}

	public void startCapturing() {
		captureThread.startCapturing();
	}

	public void stopCapturing() {
		line.stop();
	}
	
	private static void logDebug(String s) {
		if ( DEBUG ) {
			System.out.println(s);
		}
	}

	public void close() 
	{
		long processedFrames = line.getLongFramePosition();
		final int frameSize = line.getFormat().getFrameSize();
		System.out.println("\nBytes processed: "+processedFrames*frameSize);			
		line.close();
	}

	public int getBufferSizeInBytes() {
		return captureThread.getBufferSizeInBytes();
	}

	public int readFrame(byte[] buffer) throws InterruptedException 
	{
		return captureThread.read( buffer );
	}

	public long getLostBytesCount() {
		return captureThread.getLostBytesCount();
	}
}
