package de.codesourcery.fft;

import de.codesourcery.fft.Spectrum.FrequencyAndSlot;

public final class TuningHelper {

	private static final String[] keys = { "A" , "A#" , "B" , "C" , "C#" , "D" , "D#" , "E" , "F" , "F#" , "G" , "G#" };
	
	public static final int FIRST_KEY = 1;
	public static final int LAST_KEY = 88;
	
	public static boolean isValidKey( FrequencyAndSlot entry ) 
	{
		final int n = (int) Math.round(12.0 * log2( entry.getFrequency() / 440 ) + 49);
		return n >= 1 && n <= 88;
	}
	
	public static String frequencyToKey(double frequency) {
		
		int n = (int) Math.round(12.0 * log2( frequency / 440 ) + 49);
		
		if ( n < 1 || n > 88 ) {
			return null;
		}
		
		int index = n-1;
		
		final int octave;
		if ( index <= 3 ) {
			octave = 0;
		} else {
			octave = 1+( (index-3) / keys.length); // (index-3) because piano keyboards starts with (A0,A#0,B0),C1			
		}
		final String key = keys[ index % keys.length ];
		
		return key+octave;
	}
	
	public static double keyToFrequency(int n) 
	{
		final double a = (n-49.0)/12;
		return Math.pow(a, 2)*440.0; 
	}
	
	public static double log2(double v) {
		return Math.log10( v ) / Math.log10( 2 );
	}
}
