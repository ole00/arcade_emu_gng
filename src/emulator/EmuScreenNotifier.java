/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator;

import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;


public class EmuScreenNotifier implements WindowListener {

	EmuScreen canvas;
	
	public EmuScreenNotifier(EmuScreen canvas) {
		this.canvas = canvas;
	}
	
	public void windowOpened(WindowEvent e) {
	}

	public void windowClosing(WindowEvent e) {
		canvas.closeFrame();
	}

	public void windowClosed(WindowEvent e) {
	}

	public void windowIconified(WindowEvent e) {
	}

	public void windowDeiconified(WindowEvent e) {
	}

	public void windowActivated(WindowEvent e) {
	}

	public void windowDeactivated(WindowEvent e) {
	}

}
