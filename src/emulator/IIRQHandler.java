/* *************************************************
 * This code is part of the gng Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator;

public interface IIRQHandler {

	//returns interrupt vector
	public int handleIrq(Object  cpu, int irqLine, int interruptMode);
}
