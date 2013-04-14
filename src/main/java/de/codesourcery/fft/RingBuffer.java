package de.codesourcery.fft;


public final class RingBuffer {

	private final Object LOCK = new Object();
	
	private final int bufferSize;
	private final int bufferCount;

	private final byte[][] data;
	
	private byte[] writeBuffer;

	private final int bytesInBuffer[];

	private int readPtr = 0;
	private int writePtr = 0;

	private long bytesLost = 0;

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
		return bytesLost;
	}

	public void write(BufferWriter writer) 
	{
		final int written = writer.write( writeBuffer , bufferSize );
		if ( written == 0 ) {
			return;
		}
		
		synchronized( LOCK ) 
		{
			final int ptr = writePtr % bufferCount;
			
			if ( writePtr-readPtr >= bufferCount ) 
			{
				bytesLost += bytesInBuffer[ptr];
				if ( ( writePtr % 50 ) == 0 ) {
					System.out.println("Total bytes lost "+bytesLost+" : write-ptr: "+writePtr+" / read-ptr: "+readPtr);
				}
			}
			
			byte[] tmp = data[ptr];
			data[ptr] = writeBuffer;
			writeBuffer = tmp;			
			bytesInBuffer[ptr]=written;
			writePtr++;
			LOCK.notifyAll();
		}		
	}

	public int read(byte[] target) throws InterruptedException 
	{
		int bytesRead;
		synchronized( LOCK ) 
		{
			while( readPtr == writePtr ) 
			{
				LOCK.wait();
			}
			final int ptr = readPtr % bufferCount;
			readPtr++;
			bytesRead = bytesInBuffer[ ptr ];		
			System.arraycopy( data[ptr] , 0 , target , 0 , bytesRead );
		}
		return bytesRead;
	}
}