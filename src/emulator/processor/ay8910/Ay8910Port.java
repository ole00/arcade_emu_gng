/* *************************************************
 * This code is part of the Arktayt Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator.processor.ay8910;

public interface Ay8910Port {
	
	public int readPortA(int cpu);
	public int readPortB(int cpu);
	
	public void writePortA(int cpu, int value);
	public void writePortB(int cpu, int value);

}
