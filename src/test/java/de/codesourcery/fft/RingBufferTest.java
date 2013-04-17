package de.codesourcery.fft;

import java.text.DecimalFormat;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import junit.framework.TestCase;
import de.codesourcery.fft.RingBuffer.BufferWriter;

public class RingBufferTest extends TestCase {

	public void testWriteNoOverflow() throws InterruptedException 
	{
		Random rnd = new Random(System.currentTimeMillis());
		
		RingBuffer buffer = new RingBuffer(10,5);
		final byte[][] data = new byte[5][];
		int expectedHash = 0;
		for ( int i = 0 ; i < 5 ; i++ ) 
		{
			final byte[] tmp = new byte[10];
			rnd.nextBytes( tmp );
			data[i] = tmp;
			
			expectedHash = 31*expectedHash + calcHash( tmp );
			
			buffer.write( new BufferWriter() 
			{
				@Override
				public int write(byte[] buffer, int bufferSize) 
				{
					System.arraycopy( tmp , 0 , buffer , 0 , bufferSize);
					return bufferSize;
				}
			});
		}

		assertEquals( 0 , buffer.getBytesLost() );
		
		int actualHash = 0 ;
		final byte[] tmp = new byte[ 10 ];
		for ( int i = 0 ; i < 5 ; i++ ) 
		{
			final int x = buffer.read( tmp );
			if ( x != 10 ) {
				fail("Got only "+x+" bytes");
			}
			actualHash = 31*actualHash + calcHash( tmp );
		}
		
		assertEquals( expectedHash , actualHash );
	}

	public void testWriteOverflowOneBuffer() 
	{
		RingBuffer buffer = new RingBuffer(10,5);
		for ( int i = 0 ; i < 6 ; i++ ) {
			buffer.write( new BufferWriter() {

				@Override
				public int write(byte[] buffer, int bufferSize) {
					return bufferSize;
				}
			});
		}

		assertEquals( 10 , buffer.getBytesLost() );
	}	

	public void testWriteOverflowTwoBuffers() 
	{
		RingBuffer buffer = new RingBuffer(10,5);
		for ( int i = 0 ; i < 7 ; i++ ) {
			buffer.write( new BufferWriter() {

				@Override
				public int write(byte[] buffer, int bufferSize) {
					return bufferSize;
				}
			});
		}

		assertEquals( 20 , buffer.getBytesLost() );
	}	

	public void testReadWrite() throws Exception {

		final int bufSize = 1024*10;
		final int totalWriteBufferCount = 255;
		final int requiredSuccessCount=5;
		
		int currentDelay = 5000;
		int window = currentDelay / 2; 
		
		final byte[] testData = generateTestData( bufSize , totalWriteBufferCount );
        final int hash = calcHash(testData);

        Boolean lastRun = null;
		while(true) 
		{
		    boolean success = true;

		    double buffersPerSecond=0;
		    System.out.print("Delay "+currentDelay+" - ");
		    for (int i = 0 ; i < requiredSuccessCount ; i++ ) 
		    {
		        try 
		        {
		            double result = runTest(bufSize, totalWriteBufferCount, currentDelay,testData,hash);
		            buffersPerSecond = Math.max( buffersPerSecond , result );
		        } 
		        catch(Exception | Error e) 
		        {
		            success = false;
		            break;
		        }
		    }
		    
		    if ( success ) 
		    {
		        final DecimalFormat DF = new DecimalFormat("############0.0##### buffers/s");
		        System.out.println("SUCCESS - "+DF.format(buffersPerSecond));
		    } else {
                System.out.println("FAILURE");
		    }
		    
		    if ( success ) 
		    {
		        if ( currentDelay - window >= 0 ) 
		        {
		            currentDelay = currentDelay - window;
		        } 
		        else 
		        {
		            System.out.println("Top-Speed reached.");
		            break;
		        }
		    } 
		    else 
		    {
	            if ( window > 1 && Boolean.TRUE.equals( lastRun ) ) 
	            {
	                window = window >> 1;
	            }		        
                currentDelay = currentDelay + window;
		    }
		    
		    lastRun = success;
		}
	}
	
	private byte[] generateTestData(int bufSize,int bufCount) 
	{
	    final int len = bufSize*bufCount;
	    final byte[] result = new byte[ len ];
	    
	    for ( int buffer = 0 ; buffer < bufCount ; buffer++ ) 
	    {
	        final int start = buffer*bufSize;
	        final int end = start + bufSize;
	        
            int intValue = (byte) buffer;
            if ( intValue < 0 ) {
                intValue = 128+(intValue+128);
            }
            
	        for ( int offset = start ; offset < end ; offset++ ) 
	        {
	            result[offset]= (byte) buffer;
	        }
	    }
	    return result;
	}
	
    protected static final int calcHash(byte[] array) {
        return calcHash(0,array);
    }
	
	protected static final int calcHash(int startHash , byte[] array) {
        int hash = startHash;
        final int len = array.length;
        for ( int i = 0 ; i < len ; i++ ) {
            hash = 31*hash + array[i];
        }
        return hash;
	}

    private double runTest(final int bufSize, final int totalWriteBufferCount, final int delay,final byte[] testData, final int expectedHash) throws InterruptedException
    {
        final RingBuffer buffer = new RingBuffer(bufSize,50);

		final ReaderThread readerThread = new ReaderThread("reader",buffer);
		readerThread.start();
		
		final long[] bytesWritten = {0};
		
		final BufferWriter writer = new BufferWriter() 
		{
		    private int bufferPtr = 0;
		    
			@Override
			public int write(byte[] buffer, int bufferSize) 
			{
			    System.arraycopy(testData, bufferPtr , buffer , 0 , bufferSize );
			    bytesWritten[0]+=bufferSize;
			    bufferPtr+=bufferSize;
				return bufferSize;
			}
		};

		double dummy = 0;
		long time = -System.currentTimeMillis();
		for ( int i = 0 ; i < totalWriteBufferCount ; i++ ) 
		{
			buffer.write( writer );
			
			double value = 1.23d;
			for ( int j = 0 ; j < delay ; j++ ) {
			    value= value*5.23+value*value;
			}
			dummy += value;
		}
		time += System.currentTimeMillis();
		
		final double buffersPerSecond = totalWriteBufferCount / ( time / 1000.0d );
		System.out.println( "Duration: "+time);
		System.out.print( Double.toString(dummy).replaceAll(".", "" ) );
		
		Thread.sleep(1000); // let reader catch up
		
		readerThread.terminate();
		assertFalse( readerThread.isFailedUnexpectedly() );
		long bytesRead = readerThread.getBytesRead();
		assertEquals( "Byte read count mismatch", bytesWritten[0], bytesRead );
		assertEquals( "Bytes were lost" , 0 , buffer.getBytesLost() );
		assertEquals( "Hash mismatch" , expectedHash , readerThread.getHash() );
		return buffersPerSecond;
    }

	protected static final int hashByteArray(byte[] array) 
	{
		final int len = array.length;
		
		int hashCode = 0;
		for ( int i = 0 ; i < len ; i++ ) 
		{
			hashCode = (hashCode << 3) | (hashCode >> (29)) ^ array[i];
		}
		return hashCode;
	}
	
	protected static final class ReaderThread extends Thread 
	{
		private final RingBuffer buffer;
		private volatile boolean terminate;

		private boolean failedUnexpectedly;
		private long bytesRead;
		
		private Integer previousValue;
		
		private final CountDownLatch latch = new CountDownLatch(1);
		
		private int hash = 0;
		
		public ReaderThread(String name,RingBuffer buffer)
		{
			super(name);
			this.buffer = buffer;
			setDaemon(true);
		}

		@Override
		public void run() 
		{
			try {
				internalRun();
			} catch (InterruptedException e) {
				if ( ! terminate ) {
					e.printStackTrace();
				}
			} 
			finally 
			{
				if ( ! terminate ) {
					failedUnexpectedly = true;
				}
				latch.countDown();
			}
		}
		
		public int getHash()
        {
            return hash;
        }

		private void internalRun() throws InterruptedException 
		{
			final int bufferSize = buffer.getBufferSize();
			final byte[] readBuffer = new byte[ bufferSize ];
			
			while ( ! terminate ) 
			{
				int bytesRead = buffer.read( readBuffer );
				
				if ( bytesRead != bufferSize ) {
					throw new RuntimeException("Read only "+bytesRead+" bytes?");
				}
				this.hash = calcHash(this.hash , readBuffer);
				
				this.bytesRead+=bytesRead;
				
	            int intValue = readBuffer[0];
	            if ( intValue < 0 ) {
	                intValue = 128+(intValue+128);
	            }				
	            
				if ( previousValue != null ) 
				{
				    if ( intValue <= previousValue ) {
				        System.out.println("Buffer out-of-order, current: "+intValue+" , previous: "+previousValue);
				    }
				} 
			    previousValue = intValue;
			}
		}
		
		public long getBytesRead() {
			return bytesRead;
		}
		
		public boolean isFailedUnexpectedly() {
			return failedUnexpectedly;
		}
		
		public void terminate() throws InterruptedException {
			terminate = true;
			this.interrupt();
			latch.await();
		}
	}
}