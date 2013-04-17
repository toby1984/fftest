package de.codesourcery.fft;

import java.util.concurrent.atomic.AtomicReferenceArray;


public final class RingBuffer {

	private final int bufferSize;
	private final int bufferCount;

	private byte[] writeBuffer;
    private final byte[][] refData;
    
    private final AtomicReferenceArray<byte[]> data;
	   
	private int readPtr = 0;
	private int writePtr = 0;

	private volatile long lostBytesCount = 0 ;

	public interface BufferWriter 
	{
		public int write(byte[] buffer,int bufferSize);
	}

	public RingBuffer(int bufferSize,int bufferCount) 
	{
	    this.writeBuffer = new byte[bufferSize];
        this.bufferCount = bufferCount;
        this.bufferSize = bufferSize;
        
	    this.data = new AtomicReferenceArray<byte[]>(bufferCount);
	    
		this.refData = new byte[bufferCount][];
		
		for ( int i = 0 ;i < bufferCount ; i++  ) {
			refData[i] = new byte[ bufferSize ];
		}
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public int getBufferCount() {
		return bufferCount;
	}

	public long getLostBytesCount() {
		return lostBytesCount;
	}

	public void write(BufferWriter writer) 
	{
        final byte[] tmpBuffer=this.writeBuffer;
        
        /*
         * We require the writer to always write
         * zero or 'bufferSize' bytes, anything else 
         * will be discarded-
         */
        final int written = writer.write( tmpBuffer , bufferSize );        
	    if ( written == 0 ) 
	    {
		    return;
		} 
	    else if ( written < bufferSize ) 
	    {
	        System.err.println("Lost "+written+" bytes because writer returned less than the buffer size");
	        lostBytesCount += written;
	        return;
		}
	    
        final int writePtr = this.writePtr;
        final int ptr = writePtr % bufferCount;	    

	    this.writeBuffer = refData[ptr];
	    refData[ptr] = tmpBuffer;
	    
		final byte[] oldBuffer = data.getAndSet( ptr , tmpBuffer );
		if ( oldBuffer != null ) 
		{
	          lostBytesCount += bufferSize;
	          if ( ( writePtr % 50 ) == 0 ) {
	             System.out.println("Total bytes lost "+lostBytesCount+" : write-ptr: "+writePtr+" / read-ptr: "+readPtr);
	          }
		}
		this.writePtr++;
	}

	public int read(byte[] target) throws InterruptedException 
	{
        final int ptr = this.readPtr % bufferCount;
	       
        byte[] currentBuffer;
	    while ( ( currentBuffer = data.get( ptr ) ) == null ) 
	    {
	        if ( Thread.interrupted() ) 
	        {
	            throw new InterruptedException("Interrupted");
	        }
	    }
        
	    System.arraycopy( currentBuffer , 0 , target , 0 , bufferSize );
	    
        data.compareAndSet( ptr , currentBuffer , null );
        this.readPtr++;        
		return bufferSize;
	}
}