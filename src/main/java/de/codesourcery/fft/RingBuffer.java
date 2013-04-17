package de.codesourcery.fft;

import java.util.concurrent.atomic.AtomicInteger;


public final class RingBuffer {

	private final int bufferSize;
	private final int bufferCount;

	private byte[] writeBuffer;
    private byte[] readBuffer;	

	private volatile byte[][] data;
	   
	private final AtomicInteger readPtr = new AtomicInteger(0);
	private final AtomicInteger writePtr = new AtomicInteger(0);

	private volatile long bytesLost = 0 ;

	public interface BufferWriter {
		public int write(byte[] buffer,int bufferSize);
	}

	public RingBuffer(int bufferSize,int bufferCount) 
	{
		this.data = new byte[ bufferCount ][];
		for ( int i = 0 ;i < bufferCount ; i++  ) {
			data[i] = new byte[ bufferSize ];
		}
		this.writeBuffer = new byte[bufferSize];
        this.readBuffer = new byte[bufferSize];		
		this.bufferCount = bufferCount;
		this.bufferSize = bufferSize;
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public int getBufferCount() {
		return bufferCount;
	}

	public long getBytesLost() {
		return bytesLost;
	}

	public void write(BufferWriter writer) 
	{
	    /*
	     * We require the writer to always write
	     * zero or 'bufferSize' bytes, anything else 
	     * will be discarded-
	     */
	    final int written = writer.write( writeBuffer , bufferSize );
	    
	    if ( written == 0 ) {
		    return;
		} 
	    else if ( written < bufferSize ) 
	    {
	        System.err.println("Lost "+written+" bytes because writer returned less than the buffer size");
	        bytesLost += written;
	        return;
		}
		
	    final int writePtr = this.writePtr.get();
	    
		if ( writePtr - readPtr.get() >= bufferCount ) 
		{
			bytesLost += bufferSize;
			if ( ( writePtr % 50 ) == 0 ) {
				System.out.println("Total bytes lost "+bytesLost+" : write-ptr: "+writePtr+" / read-ptr: "+readPtr);
			}
		}

		final int ptr = writePtr % bufferCount;
		final byte[] tmp = data[ptr];
		data[ptr] = writeBuffer;
		writeBuffer = tmp;			
		
		//  DO NOT REMOVE THE NEXT LINE , see http://stackoverflow.com/questions/8827820/necessity-of-volatile-array-write-while-in-synchronized-block
		this.data = this.data; // perform volatile write to make sure changes to the array are being published to the reader thread
		
		this.writePtr.incrementAndGet();
	}

	public int read(byte[] target) throws InterruptedException 
	{
	    int readPtr = this.readPtr.get();
	    
	    while( readPtr == writePtr.get() ) {
	        if ( Thread.interrupted() ) {
	            throw new InterruptedException("Interrupted");
	        }
	    }
	    
		final int ptr = readPtr % bufferCount;
		
		final byte[] tmp = readBuffer;
		readBuffer = data[ptr];
		data[ptr] = tmp;
		
        this.readPtr.incrementAndGet();
        
        System.arraycopy( readBuffer , 0 , target , 0 , bufferSize );		
        
		return bufferSize;
	}
}