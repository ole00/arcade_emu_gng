/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator;


public class Main {

	/**
	 * This is the program startup code. It creates and runs the emulated machine.
	 */
	public static void main(String[] args) {
		if (args.length < 1 || (!args[0].endsWith(".zip"))) {
			System.out.println("missing parameter: the rom zip file");
			return;
		}
		emulator.gng.Machine machine = new emulator.gng.Machine(args[0]);
		if (args.length > 2 && "-fm".equals(args[1])) {
			machine.setFmDevice(args[2]);
		}
		machine.run();
	}

}
