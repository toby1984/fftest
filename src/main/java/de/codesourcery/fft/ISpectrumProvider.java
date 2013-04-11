package de.codesourcery.fft;

public interface ISpectrumProvider
{
    public interface ICallback 
    {
        public void calculationFailed(ISpectrumProvider provider);
        
        public void calculationFinished(ISpectrumProvider provider,Spectrum spectrum);
    }
    
    public void calcSpectrum(ICallback callback,int fftSize,boolean applyWindowingFunction);
}
