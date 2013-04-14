package de.codesourcery.fft;

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
			data[i] = tmp;
			rnd.nextBytes( data[i] );
			expectedHash = 31*expectedHash + hashByteArray(tmp);
			
			buffer.write( new BufferWriter() {

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
			actualHash = 31*actualHash + hashByteArray( tmp );
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

		final int threadCount = Runtime.getRuntime().availableProcessors()+1;

		final int bufSize = 1024;
		
		final RingBuffer buffer = new RingBuffer(bufSize,50);

		final ReaderThread[] readerThreads = new ReaderThread[threadCount]; 
		for ( int i = 0 ; i < threadCount ; i++ ) 
		{
			readerThreads[i] = new ReaderThread("reader-"+i,buffer);
			readerThreads[i].start();
		}
		
		final long[] bytesWritten = {0};
		final BufferWriter writer = new BufferWriter() {
			
			private final byte[] data = new byte[ bufSize ];
			
			@Override
			public int write(byte[] buffer, int bufferSize) 
			{
				System.arraycopy( data , 0 , buffer , 0 , bufferSize );
				bytesWritten[0]+=bufferSize;
				return bufferSize;
			}
		};

		for ( int i = 0 ; i < 1000 ; i++ ) {
			buffer.write( writer );
			Thread.sleep(1);
		}
		
		Thread.sleep(1000); // let readers catch up
		
		long bytesRead = 0;
		for ( int i = 0 ; i < threadCount ; i++ ) {
			ReaderThread t = readerThreads[i]; 
			t.terminate();
			assertFalse( t.isFailedUnexpectedly() );
			System.out.println("Thread "+t.getName()+" read "+t.getBytesRead()+" bytes");
			bytesRead += t.getBytesRead();
		}
		System.out.println("Bytes written: "+bytesWritten[0]);
		System.out.println("Bytes read: "+bytesRead);
		System.out.println("Bytes lost: "+buffer.getBytesLost());
		assertEquals( bytesWritten[0], bytesRead );
		assertEquals( 0 , buffer.getBytesLost() );
	}

	protected static final int hashByteArray(byte[] array) {
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
		private int hash;
		private long bytesRead;
		
		private final CountDownLatch latch = new CountDownLatch(1);
		
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
				this.bytesRead += bytesRead;
				hash = 23*hash+hashByteArray(readBuffer);
			}
		}
		
		public long getBytesRead() {
			return bytesRead;
		}
		
		public int getHash() {
			return hash;
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