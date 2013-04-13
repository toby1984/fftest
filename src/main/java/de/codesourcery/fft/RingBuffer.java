package de.codesourcery.fft;

import java.util.concurrent.atomic.AtomicLong;

public final class RingBuffer {

	private final int bufferSize;
	private final int bufferCount;

	private final byte[][] data;

	private final int bytesInBuffer[];

	private int readPtr = 0;
	private int writePtr = 0;

	private final AtomicLong bytesWritten = new AtomicLong(0);
	private final AtomicLong bytesRead = new AtomicLong(0);

	private final Object BUFFER_NOT_EMPTY = new Object();

	private final Object LOCK = new Object();

	public interface BufferWriter {
		public int write(byte[] buffer,int bufferSize);
	}

	public RingBuffer(int bufferSize,int bufferCount) 
	{
		this.data = new byte[ bufferCount ][];
		for ( int i = 0 ;i < bufferCount ; i++  ) {
			data[i] = new byte[ bufferSize ];
		}
		this.bufferCount = bufferCount;
		this.bufferSize = bufferSize;
		this.bytesInBuffer = new int[ bufferCount ];
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public int getBufferCount() {
		return bufferCount;
	}

	public long getBytesLost() {
		return bytesWritten.get() - bytesRead.get();
	}

	public void write(BufferWriter writer) 
	{
		synchronized( data[writePtr] ) 
		{
			int written = writer.write( data[writePtr] , bufferSize );
			if ( written == 0 ) {
				return;
			}
			bytesInBuffer[writePtr]=written;
			bytesWritten.addAndGet( written );
		}
		
		synchronized(LOCK) 
		{
			writePtr = (writePtr+1) % bufferCount;
		}
		synchronized( BUFFER_NOT_EMPTY ) {
			BUFFER_NOT_EMPTY.notifyAll();
		}		
	}

	public int read(byte[] target) throws InterruptedException 
	{
		int read;
		int ptr;
		while(true) 
		{
			synchronized( LOCK ) 
			{
				if ( readPtr != writePtr ) 
				{
					ptr = readPtr;
					readPtr = (readPtr+1) % bufferCount;					
					break;
				}
			}
			synchronized( BUFFER_NOT_EMPTY ) {
				BUFFER_NOT_EMPTY.wait();
			}				
		}
		
		synchronized( data[ ptr ]) 
		{
			read = bytesInBuffer[ptr ];		
			bytesRead.addAndGet( read );
			System.arraycopy( data[ptr] , 0 , target , 0 , read );
		}
		return read;
	}
}