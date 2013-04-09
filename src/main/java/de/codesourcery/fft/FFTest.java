package de.codesourcery.fft;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JFrame;

public class FFTest
{
    public static void main(String[] args) throws Exception
    {
        AudioFile file = new AudioFile("/home/tobi/workspace/fftest/src/main/resources/10000hz.wav");
        System.out.println( file );
        
        final FFTPanel panel = new FFTPanel( file , 128 ); 

        final JFrame frame = new JFrame("FFT");
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx=1.0;
        cnstrs.weighty=1.0;
        frame.setSize( new Dimension(600,400 ) );
        frame.setPreferredSize( new Dimension(600,400 ) );
        frame.getContentPane().setLayout( new GridBagLayout() );
        frame.getContentPane().add( panel , cnstrs );
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        panel.attachKeyListener( frame );
        frame.setVisible( true );
    }
}
