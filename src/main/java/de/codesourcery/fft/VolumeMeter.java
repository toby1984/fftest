package de.codesourcery.fft;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;

import javax.swing.JPanel;

public final class VolumeMeter extends JPanel {

	private volatile double volume;
	
	public VolumeMeter(int height) {
		setPreferredSize( new Dimension(30, (int) Math.round(height*1.1)));
	}
	
	public void setVolume(double volume) {
		this.volume = volume;
	}
	
	@Override
	protected void paintComponent(Graphics g) 
	{
		Graphics2D graphics = (Graphics2D) g;
		super.paintComponent(g);

		final int height = getHeight();
		
		final int totalBarHeight = (int) Math.round( height*0.7 );
		final int actualBarHeight = (int) Math.round(totalBarHeight*volume);
		final int barWidth = getWidth() - 2 ;
		
		final int xOrigin = 1;
		final int yOrigin = height - 2;
		
		graphics.setColor(Color.BLACK);
		graphics.drawRect( xOrigin, yOrigin - totalBarHeight , barWidth , totalBarHeight );

		Paint paint= new GradientPaint(xOrigin, yOrigin, Color.BLACK, xOrigin + barWidth , yOrigin - totalBarHeight ,Color.RED);		
		graphics.setPaint( paint );
		graphics.fillRect( xOrigin, yOrigin - actualBarHeight , barWidth , actualBarHeight );
	}
}
