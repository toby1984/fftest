package de.codesourcery.fft;

import de.codesourcery.fft.Spectrum.FrequencyAndSlot;

public final class TuningHelper {

	private static final String[] keys = { "A" , "A#" , "H" , "C" , "C#" , "D" , "D#" , "E" , "F" , "F#" , "G" , "G#" };
	
	public static final int FIRST_KEY = 1;
	public static final int LAST_KEY = 88;
	
	public static final double MIN_FREQUENCY = keyToFrequency( FIRST_KEY );
	public static final double MAX_FREQUENCY = keyToFrequency( LAST_KEY );
	
	/*
	 * Guitar keys:
	 * 
	 * E2 - 82.4  (#20) 
     * A2 - 110   (#25)
     * D3 - 146.8 (#30)
     * G3 - 196   (#35)
     * H3 - 246.9 (#39)
     * E4 - 329.6 (#44)
	 */
	public static final Key E2 = TuningHelper.getKey( 20 );
	public static final Key A2 = TuningHelper.getKey( 25 );
	public static final Key D3 = TuningHelper.getKey( 30 );
	public static final Key G3 = TuningHelper.getKey( 35 );
	public static final Key H3 = TuningHelper.getKey( 39 );
	public static final Key E4 = TuningHelper.getKey( 44 );	
	
	public static final Key[] GUITAR_KEYS = {E2,A2,D3,G3,H3,E4};
	
	public static final class Key implements Comparable<Key>
	{
		private final String name;
		private final int keyIndex;
		private final double targetFrequency;
		private final double actualFrequency;
		
		public Key(int keyIndex , String name, double targetFrequency, double actualFrequency) {
			this.keyIndex = keyIndex;
			this.name = name;
			this.targetFrequency = targetFrequency;
			this.actualFrequency = actualFrequency;
		}
		
		public Key nextKey() {
			return getKey( this.keyIndex + 1 );
		}
		
		public boolean equals(Object o) {
			if ( o instanceof Key ) {
				return this.keyIndex == ((Key) o).keyIndex;
			}
			return false;
		}
		
		@Override
		public int hashCode() {
			return keyIndex;
		}
		
		public Key previousKey() {
			return getKey( this.keyIndex - 1 );
		}		
		
		public Key withActualFrequency(double actualFrequency) 
		{
			return new Key( this.keyIndex , this.name , this.targetFrequency , actualFrequency);
		}
		
		public String getName() { return name; }
		public int getKeyIndex() { return keyIndex; }
		public double getTargetFrequency() { return targetFrequency; }
		public double getActualFrequency() { return actualFrequency; }

		@Override
		public String toString() {
			return "Key [name=" + name + ", keyIndex=" + keyIndex
					+ ", frequency=" + targetFrequency + ", actualFrequency="
					+ actualFrequency + "]";
		}

		@Override
		public int compareTo(Key o) 
		{
			return Integer.compare( this.keyIndex , o.keyIndex );
		}
	}
	
	public static boolean isValidKey( FrequencyAndSlot entry ) 
	{
		double frequency = entry.getFrequency();
		if ( frequency < MIN_FREQUENCY ) {
			return false;
		}
		if ( frequency > MAX_FREQUENCY ) {
			return false;
		}
		return true;
	}
	
	public static Key getKey( int keyIndex ) 
	{
		if ( keyIndex < FIRST_KEY || keyIndex > LAST_KEY ) {
			return null;
		}
		return getKey( keyToFrequency( keyIndex ) );
	}	
	
	public static Key getKey( double actualFrequency ) 
	{
		int n = frequencyToKeyIndex(actualFrequency);
		
		if ( n < 1 || n > 88 ) {
			return null;
		}
		final double targetFrequency = keyToFrequency( n );
		return new Key(n , frequencyToKey( actualFrequency ) , targetFrequency , actualFrequency );
	}
	
	public static int frequencyToKeyIndex(double frequency) 
	{
		return (int) Math.round(12.0 * log2( frequency / 440 ) + 49);
	}
	
	public static String frequencyToKey(double frequency) {
		
		int n = frequencyToKeyIndex(frequency);
		
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
		return Math.pow(2, a)*440.0; 
	}
	
	public static double log2(double v) {
		return Math.log10( v ) / Math.log10( 2 );
	}
}
