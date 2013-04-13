package de.codesourcery.fft;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

import javax.sound.sampled.AudioFormat;

public class WaveWriter {

	private final File outputFile;
	private final AudioFormat format;
	private final OutputStream out;
	
	private long bytesWritten=0;
	
	protected WaveWriter(File outputFile, AudioFormat format) throws FileNotFoundException 
	{
		this.outputFile = outputFile;
		this.format = format;
		this.out = new FileOutputStream(outputFile.getAbsolutePath()+File.separatorChar+".raw");
	}
	
	public void write(byte[] buffer,int offset,int length) throws IOException {
		out.write( buffer , offset , length );
		bytesWritten+=length;
	}
	
	public void close() throws IOException {
		out.close();

		final FileOutputStream wavOut = new FileOutputStream( outputFile );
		
		wavOut.write( toASCII("RIFF" ) );
		long fileSize = bytesWritten-8; // probably wrong => total WAV file size - 8
		wavOut.write( toUnsignedLong( fileSize ) );
		wavOut.write( toASCII("WAVE" ) );
		
		// write format
		wavOut.write( toASCII("fmt " ) );
		wavOut.write( toUnsignedLong( 16 ) );
		
		wavOut.write( toUnsignedShort( 0x001 ) ); // PCM
		wavOut.write( toUnsignedShort( format.getChannels() ) );
		wavOut.write( toUnsignedLong( (int) format.getSampleRate() ) );
		wavOut.write( toUnsignedLong( (long) (( format.getSampleSizeInBits() / 8) * format.getSampleRate() * format.getChannels() ) )  );
		
		// <Anzahl der Kanäle> · ((<Bits/Sample (eines Kanals)> + 7) / 8)
		int frameSize = format.getChannels() * (format.getSampleSizeInBits()+7)/8;
		wavOut.write( toUnsignedShort( frameSize ) );
		wavOut.write( toUnsignedShort( format.getSampleSizeInBits() ) );
		
		// write data 
		wavOut.write( toASCII("data" ) );
		wavOut.write( toUnsignedLong( bytesWritten ) );
		
		byte[] buffer = new byte[1024];
		FileInputStream  in = new FileInputStream( outputFile.getAbsolutePath()+File.separatorChar+".raw" );
		int read = 0;
		while ( ( read = in.read( buffer ) ) > 0 ) 
		{
			wavOut.write( buffer , 0 , read );
		}
		wavOut.close();
	}
	
	private byte[] toUnsignedLong(long value) {
		
		byte[] result = new byte[4];
		result[0] = (byte) ((value >> 24) & 0xff);
		result[1] = (byte) ((value >> 16) & 0xff);
		result[2] = (byte) ((value >> 8) & 0xff);
		result[3] = (byte) ( value & 0xff);
		return result;
	}
	
	private byte[] toUnsignedShort(int value) {
		
		byte[] result = new byte[2];
		result[0] = (byte) ((value >> 8) & 0xff);
		result[1] = (byte) ( value & 0xff);
		return result;
	}	
	
	private byte[] toASCII(String s) 
	{
		Charset utf8charset = Charset.forName("UTF-8");
		Charset iso88591charset = Charset.forName("ISO-8859-1");

		ByteBuffer inputBuffer = ByteBuffer.wrap(s.getBytes());

		// decode UTF-8
		CharBuffer data = utf8charset.decode(inputBuffer);

		// encode ISO-8559-1
		ByteBuffer outputBuffer = iso88591charset.encode(data);
		return outputBuffer.array();		
	}
}
