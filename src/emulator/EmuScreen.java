/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;

import emulator.gng.Machine;



/**
 * This class:
 *  - opens up an AWT frame
 *  - creates a bitmap (layer) the emulator writes to 
 *  - handles key inputs 
 *
 */
public class EmuScreen extends Canvas implements KeyListener, MouseListener, MouseMotionListener {

	//layer is double buffered
	private Image layer;
	private Graphics lg;
	private Frame frame;
	private int scale;
	private int width;
	private int height;
	private int keyState;
	private int frontBuffer;
	private int mouseX;
	private boolean paused;
	
	private boolean saveState;
	private boolean loadState;
	
	public EmuScreen(int width, int height, int scale, String title) {
		this.scale = scale;
		setBackground(Color.BLACK);
		addKeyListener(this);
		addMouseMotionListener(this);
		addMouseListener(this);
		
		createLayer(width, height);
		createFrame(title);
		keyState = 0xFFFFFFF;
		paused = false;
		saveState = false;
		loadState = false;
	}
	
	
	private void createLayer(int w, int h) {
		layer = createImage(w, h);
		if (layer == null) {
			layer = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		}
		lg = layer.getGraphics();
	}
	
	private void createFrame(String title) {
		frame = new Frame(title == null ? "emu" : title);
		frame.add(this);
		frame.addWindowListener(new EmuScreenNotifier(this));
		frame.setLocation(300, 300);
		frame.setSize(10, 10);
		frame.setVisible(true);
		width = layer.getWidth(null) * scale;
		height = layer.getHeight(null) * scale;
		Insets insets = frame.getInsets();
		int w = width + insets.right + insets.left;
		int h = height + insets.top + insets.bottom + 8;
		frame.setSize(w,h);
		frame.doLayout();
		this.requestFocus();
	}
	

	/**
	 * Draw the image on the screen
	 */
	public synchronized void update(Graphics g) {
		paint(g);
	}
	
	/**
	 * Draw the image on the screen
	 */
	public synchronized void paint(Graphics g) {
		if (scale == 1) {
			g.drawImage(layer, 0, 0, null);
		} else {
			g.drawImage(layer, 0, 0, width, height, 0, 0, width / scale, height / scale, null);
		}
	}
	
	public void drawImage(Image img, int srcX, int srcY, int w, int h, int dstX, int dstY) {
		lg.setClip(dstX, dstY, w, h);
		lg.drawImage(img, dstX - srcX, dstY - srcY, null);
	}
	public void drawRect(int dstX, int dstY, int w, int h, int color) {
		lg.setColor(new Color(color));
		lg.setClip(dstX, dstY, w, h);
		lg.drawRect(dstX, dstY, w -1, h -1);
	}
	
	
	public void fillRect(int dstX, int dstY, int w, int h, int color) {
		lg.setColor(new Color(color));
		lg.setClip(dstX, dstY, w, h);
		lg.fillRect(dstX, dstY, w, h);
	}
	
	public void clear() {
		lg.setColor(Color.BLACK);
		lg.setClip(0, 0, width / scale, height / scale);
		lg.fillRect(0, 0, width / scale, height / scale);
	}

	public void flushGraphics() {
		frontBuffer = 1 - frontBuffer;
		repaint();
	}

	public void keyTyped(KeyEvent e) {
	}

	public synchronized int getKeyState() {
		return keyState;
	}
	
	public int getMouseX() {
		return mouseX;
	}
	
	public synchronized void keyPressed(KeyEvent e) {
		switch (e.getKeyCode()) {
		case KeyEvent.VK_5 : keyState &= ~Machine.IO_P1_COIN;	break; //coin 1
		case KeyEvent.VK_1 : keyState &= ~Machine.IO_P1_START; break;  //start 1
		case KeyEvent.VK_UP : keyState &= ~Machine.IO_P1_UP; break;  //UP
		case KeyEvent.VK_DOWN : keyState &= ~Machine.IO_P1_DOWN; break;  //DOWN
		case KeyEvent.VK_LEFT : keyState &= ~Machine.IO_P1_LEFT; break;  //left
		case KeyEvent.VK_RIGHT : keyState &= ~Machine.IO_P1_RIGHT; break;  //right
		case KeyEvent.VK_CONTROL : keyState &= ~Machine.IO_P1_B1; break;  //fire 1
		case KeyEvent.VK_SHIFT : keyState &= ~Machine.IO_P1_B2; break;  //fire 2
		case KeyEvent.VK_ALT : keyState &= ~Machine.IO_P1_B2; break;  //fire 2
		case KeyEvent.VK_P : paused = !paused; break;
		case KeyEvent.VK_F8 : saveState = true; break;
		case KeyEvent.VK_F12 : loadState = true; break;
		}
		
	}

	public synchronized void keyReleased(KeyEvent e) {
		switch (e.getKeyCode()) {
		case KeyEvent.VK_5 : keyState |= Machine.IO_P1_COIN;	break; //coin 1
		case KeyEvent.VK_1 : keyState |= Machine.IO_P1_START; break;  //start 1
		case KeyEvent.VK_UP : keyState |= Machine.IO_P1_UP; break;  //UP
		case KeyEvent.VK_DOWN : keyState |= Machine.IO_P1_DOWN; break;  //DOWN
		case KeyEvent.VK_LEFT : keyState |= Machine.IO_P1_LEFT; break;  //left
		case KeyEvent.VK_RIGHT : keyState |= Machine.IO_P1_RIGHT; break;  //right
		case KeyEvent.VK_CONTROL : keyState |= Machine.IO_P1_B1; break;  //fire 1
		case KeyEvent.VK_SHIFT : keyState |= Machine.IO_P1_B2; break;  //fire 2
		case KeyEvent.VK_ALT : keyState |= Machine.IO_P1_B2; break;  //fire 2
		}
		
	}

	void closeFrame() {
		frame.setVisible(false);
		frame.dispose();
	}
	
	public boolean isRunning() {
		return frame.isVisible();
	}

	public boolean isPaused() {
		return paused;
	}
	
	public boolean isSaveState() {
		boolean result = saveState;
		saveState = false;
		return result;
	}
	
	public boolean isLoadState() {
		boolean result = loadState;
		loadState = false;
		return result;
	}

	
	// mouse events for controlling the paddle
	public void mouseDragged(MouseEvent e) {	
	}

	public void mouseMoved(MouseEvent e) {
		mouseX = e.getX();
	}

	public void mouseClicked(MouseEvent e) {
	}

	public void mousePressed(MouseEvent e) {
		keyState &= ~(1 << 4); // Fire
	}

	public void mouseReleased(MouseEvent e) {
		keyState |= (1 << 4); // Fire	
	}

	public void mouseEntered(MouseEvent e) {
	}

	public void mouseExited(MouseEvent e) {
	}
}
