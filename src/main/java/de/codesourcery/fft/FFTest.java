package de.codesourcery.fft;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.commons.lang.StringUtils;

public class FFTest
{
    private static final JTextField currentFile = new JTextField("/home/tgierke/workspace/fftest/src/main/resources/10000hz.wav") 
    {
        {
            setEditable( false );
        }
    };
    
    public static void main(String[] args) throws Exception
    {
        final JFrame frame = new JFrame("FFT");
        
        // setup FFT spectrum panel
        final SpectrumPanel panel = new SpectrumPanel( new AudioFile( currentFile.getText() ) , 128 ); 
        
        panel.setSize( new Dimension(600,400 ) );
        panel.setPreferredSize( new Dimension(600,400 ) );
        
        final JPanel combined = new JPanel();
        combined.setLayout( new GridBagLayout() );
        
        // add file & chooser button
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.HORIZONTAL;
        cnstrs.gridx=0;
        cnstrs.gridy=0;
        cnstrs.gridwidth=1;        
        cnstrs.weightx=1.0;
        cnstrs.weighty=0;
        
        combined.add( currentFile ,cnstrs );
        
        final JButton button = new JButton("Choose...");
        button.addActionListener( new ActionListener() {
            
            @Override
            public void actionPerformed(ActionEvent e)
            {
                final JFileChooser fc;
                if ( StringUtils.isNotBlank( currentFile.getText() ) && new File( currentFile.getText() ).getParentFile().isDirectory() ) 
                {
                    fc = new JFileChooser( new File( currentFile.getText() ).getParentFile() );
                } else {
                    fc = new JFileChooser();
                }
                final int returnVal = fc.showDialog( frame, "Analyze audio file");                
                if ( returnVal == JFileChooser.APPROVE_OPTION ) 
                {
                    try {
                        AudioFile file = new AudioFile( fc.getSelectedFile() );
                        currentFile.setText( fc.getSelectedFile().getAbsolutePath() );
                        panel.setFile( file );
                    } 
                    catch (IOException | UnsupportedAudioFileException ex) 
                    {
                        ex.printStackTrace();
                    }
                }
            }
        });
        
        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.gridx=1;
        cnstrs.gridy=0;
        cnstrs.gridwidth=1;        
        cnstrs.weightx=0;
        cnstrs.weighty=0;
        
        combined.add( button ,cnstrs );        
        
        // add FFT spectrum panel
        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridwidth=2;
        cnstrs.gridx=0;
        cnstrs.gridy=1;
        cnstrs.weightx=1.0;
        cnstrs.weighty=1.0;
        
        combined.add( panel ,cnstrs );
        
        // add to frame's content panel
        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx=1.0;
        cnstrs.weighty=1.0;   
        
        frame.getContentPane().setLayout( new GridBagLayout() );
        frame.getContentPane().add( combined , cnstrs );
        
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        panel.attachKeyListener( frame );
        
        panel.setFocusable(true);
        panel.attachKeyListener( panel );
        
        combined.setFocusable( true );
        panel.attachKeyListener( combined );
//        panel.attachKeyListener( frame );
        
        frame.pack();
        frame.setVisible( true );
    }
}
