/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator.gng;

import emulator.IBaseDevice;



/**
 * Nothing here, just an empty class to satisfy the Z80 cpu emulator
 */
public class IO implements IBaseDevice {

	public int IORead(int address) {
		return 0;
	}

	public void IOWrite(int address, int data) {
	}
}
