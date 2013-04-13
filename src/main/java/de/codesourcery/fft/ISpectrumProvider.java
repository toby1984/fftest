package de.codesourcery.fft;

import javax.sound.sampled.AudioFormat;

public interface ISpectrumProvider
{
    public interface ICallback 
    {
        public void calculationFailed(ISpectrumProvider provider);
        
        public void calculationFinished(ISpectrumProvider provider,Spectrum spectrum);
    }
    
    public void close();
    
    public AudioFormat getAudioFormat(); 
    
    public void calcSpectrum(ICallback callback,int fftSize,boolean applyWindowingFunction);
    
    public boolean isStatic();
}
