/* *************************************************
 * This code is part of the Gng Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator.gng;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.concurrent.locks.LockSupport;

import emulator.EmuScreen;
import emulator.IIRQHandler;
import emulator.IMemory;
import emulator.SoundStreamer;
import emulator.UartWriter;
import emulator.processor.ay8910.Ay8910;
import emulator.processor.m6809.m6809;
import emulator.processor.m6809.m6809H;
import emulator.processor.ym2203.fm;
import emulator.processor.ym2203.fm.FmIrqHandler;
import emulator.processor.ym2203.fm.FmSsgHandler;
import emulator.processor.ym2203.fm.FmTimerHandler;
import emulator.processor.z80.Z80Core;

/**
 * This is a starting class for the gng machine. 
 *
 */
public class Machine implements IIRQHandler{

	public static int IO_P1_COIN = (1 << 0);
	public static int IO_P1_START = (1 << 1); 
	public static int IO_P1_UP = (1 << 2);
	public static int IO_P1_DOWN = (1 << 3);
	public static int IO_P1_LEFT = (1 << 6);
	public static int IO_P1_RIGHT = (1 << 5);
	public static int IO_P1_B1 = (1 << 4);
	public static int IO_P1_B2 = (1 << 7);

	
	private static int SND_CYCLES_PER_FRAME = 40000; // ~2.6 MHz
	private static int MAIN_CYCLES_PER_FRAME = 25000; //~ 1.5 MHz
	private static final long FRAME_TIME = 16660000; // in nano seconds ~ 60 Hz => 1000 / 16.66;
	private static final int FRAME_SKIP = 0; //draw every frame = 0, draw every other frame = 1 etc.
	public static final int SCALE = 5; //scale the screen 3x
	
	private static final int WATCHDOG_TIME = 10 * 60; //check every ~ 10 seconds
	
	private static Machine instance;
	
	// set to 1 to ignore watch-dog
	private static int watchdogCounter = 0;
	
	m6809   mainCpu;
	SystemMemory  memory;
	Z80Core soundCpu;
	SystemMemory soundMemory;
	EmuScreen screen;
	Video video;
	int frameSkipCounter;
	String romZipFileName;
	int[] saveStateData;
	UartWriter uw;

	
	public Machine(String romZipFileName) {
		this.romZipFileName = romZipFileName;
		saveStateData = new int[16 * 1024];
	}
	
	public static void watchDogTick() {
		//watchdog is ticked - all is OK - CPU seems to be running
		watchdogCounter++;
	}
	
	private void printHelp() {
		System.out.println(" Controls:");
		System.out.println(" -------------------------");
		System.out.println(" movement   : arrow keys");
		System.out.println(" button 1   : ctrl");
		System.out.println(" button 2   : shift or alt");
		System.out.println(" coin       : 5");
		System.out.println(" P1 start   : 1");
	}
	
	public void setFmDevice(String devicePath) {
		File f = new File(devicePath);
		if (!f.exists() || f.isDirectory()) {
			System.out.println("Device not found:" + devicePath);
			uw = null;
			return;
		}
		setupUartParams(devicePath);
		try {
			uw = new UartWriter(new FileOutputStream(devicePath));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void setupUartParams(String device) {
		String [] cmd = new String[] {
/*				
			"stty", "-F", device, "921600", 
			"-parenb", "-parodd", "-cmspar", "cs8", "-hupcl", "-cstopb", "cread", "clocal", 
			"-crtscts",
			"-ignbrk", "brkint", "ignpar", "-parmrk", "-inpck", "-istrip", "-inlcr", "-igncr", "-icrnl",
			"-ixon", "-ixoff", "-iuclc", "-ixany", "-imaxbel", "iutf8", "-opost", "-olcuc", "-ocrnl",
			"-onlcr", "-onocr", "-onlret", "-ofill", "-ofdel", "nl0", "cr0", "tab0", "bs0", "vt0", "ff0", "-isig" ,
			"-icanon", "iexten", "-echo", "-echoe", "-echok", "-echonl", "-noflsh", "-xcase", "-tostop",
			"-echoprt", "-echoctl",  "-echoke", "-flusho", "-extproc"
*/
				"stty", "-F", device, "115200",
			    "raw", 
			    "cs8", "ixon", "ixoff", "-parenb", "-cstopb", "-hupcl", "cread", "clocal", "-crtscts",
			    "-ignbrk", "-brkint", "-ignpar", "-parmrk", "-inpck", "-istrip",
			    "-inlcr", "-igncr", "-icrnl", "-ixany", "-iutf8",
			    "-opost", "-isig", "-icanon", "-iexten",
			    "-echo", "-echoe", "-echok", "-echonl", "-echoctl", "-echoke",
			    "min", "1", "time", "0"				
		};
		try {
			Process p = Runtime.getRuntime().exec(cmd);
			Thread.sleep(500);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Call this method to start the emulation
	 */
	public void run() {
		printHelp();

		instance = this;
		//the memory map. Constructor starts the ROM loading from a zip file. 
		memory = new SystemMemory(romZipFileName);
		memory.machine = this;

		//video code handles screen redraws. Pass the memory instance so it 
		//can access the graphics ROMs data
		video = new Video(memory); 
		video.init();
		
		//screen -> opens up the emulator window
		screen = new EmuScreen(video.getWidth(), video.getHeight(), SCALE, getTitle());
		
		//video.testPalette(screen);
		//video.test(screen);
		//video.testSprite(screen);

		// create M6809 main CPU
		mainCpu = new m6809(memory.getMainMemory());
		mainCpu.set_irq_callback(this);
		mainCpu.reset();
		
		//create Z80 sound CPU 
		soundCpu = new Z80Core(memory.getSoundMemory(), new IO(), this);
		soundCpu.reset();
		
		// Create sound chips.
		// GNG uses 2 YM2203 FM chips which also contain so called PSG - programmable sound generator.
		// That generator is compatible with AY8910 sound chip.
		// YM2203 emulator we use here only emulates the FM part and the PSG channels are delegated
		// to separate AY8910 emulator. 
		
		// First create 2 AY8910 instances
		int[] ssgVolume = new int[] { 0xFFFF, 0xFFFF};
		final Ay8910 ssg = new Ay8910(2, null);
		ssg.start(1500000, ssgVolume, 48000); // 1.5MHz

		// Create handlers of YM2203 that deal with timing and interrupts and also interact with SSG.
		FmTimerHandler fmTimer = new FmTimerHandler() {
			@Override
			public void fmTimerHandler(int n, int c, double count, double stepTime) {
				//System.out.println("FM Timer: n=" + n + " c=" +c + " count=%c" + " stepTime=" + stepTime );
			}
		};
		FmIrqHandler fmIrq = new FmIrqHandler() {
			@Override
			public void fmIrqHandler(int n, int irq) {
				//System.out.println("FM Timer: n=" + n + " irq=" + irq);
			}
		};
		FmSsgHandler fmSsg = new FmSsgHandler() {
			int psg1Addr;
			@Override
			public void SsgWrite(int n, int addr, int val) {
				Ay8910.write(n, addr, val);
			}
			
			@Override
			public void SsgReset(int n) {
				Ay8910.reset(n);
				/*
				if (uw != null && n == 0) {
					uw.write(n, 0xFF, 0xFF); //reset
				}
				*/
			}
			
			@Override
			public int SsgRead(int n) {
				return 0;
			}
			
			@Override
			public void SsgClk(int n, int clk) {
			}
		};
		
		// initialise 2 YM2203 chips
		fm.YM2203Init(2, 1500000, 44800, fmTimer, fmIrq, fmSsg); //1.5MHz
		
		// Create a sound stream updater that is called by a sound system to get 16 bit PCM channel data
		// from various sound channels.
        SoundStreamer.StreamUpdate sndUpdate = new SoundStreamer.StreamUpdate() {
    		short[][] ssg1 = new short[3][];
    		short[][] ssg2 = new short[3][];
        	public void update(final int chipId, final short[][] buffer, final int length) {
        		// Update PC sound emulation only if the HW dual ym2203 sound card is not connected
        		if (uw == null) {
	        		fm.UpdateStream(0, buffer[0], length); // YM1 - FM
	        		fm.UpdateStream(1, buffer[1], length); // YM2 - FM
	        		ssg1[0] = buffer[2];
	        		ssg1[1] = buffer[3];
	        		ssg1[2] = buffer[4];
	        		Ay8910.update(0, ssg1, length); // YM1 - SSG
	        		ssg2[0] = buffer[5];
	        		ssg2[1] = buffer[6];
	        		ssg2[2] = buffer[7];
	        		Ay8910.update(1, ssg2, length); // YM1 - SSG
        		}
        	}
        };
        
        // volume of sound channels:  FM1, FM2,  PSG1      PSG2
        int[] channelVolume = new int[] { 2, 2,  5, 5, 5,  5,5,5};
        
        // Create sound streamer that will mix 8 channels.
        // One for each FM chip and  3 PSG channels for each PSG chip.
        SoundStreamer.streamInit(2 + 3 + 3, channelVolume, 44800, 0, sndUpdate);
				
		//start the emulation
		emulate();
	}

	private String getTitle() {
		String[] bits = getClass().getCanonicalName().replace(".", " ").split(" ");
		return bits[1];
	}

	private void saveState() {
		System.out.println("Save state");
		int[] s = saveStateData;
		int index = 1;
		
		index = mainCpu.saveState(s, index);
		IMemory im = memory.getMainMemory();
		for (int i = 0; i < 0x3000; i++) {
			s[index++] = im.readByte(i);
		}
		s[index++] = memory.getScrollX();
		s[index++] = memory.getScrollY();
		s[index++] = memory.bankSwitch;	
		s[0] = index;
		try {
			DataOutputStream dos = new DataOutputStream(new FileOutputStream("gng.sav"));
			for (int i = 0; i < index; i++) { 
				dos.writeInt(s[i]);
			}
			dos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private void loadState() {
		System.out.println("Load state");
		//if (saveStateData[0] == 0) 
		{
			try {
				DataInputStream din = new DataInputStream(new FileInputStream("gng.sav"));
				saveStateData[0] = din.readInt(); 
				System.out.println("total=" + saveStateData[0]);
				for (int i = 0; i < saveStateData[0]-1; i++) { 
					saveStateData[i + 1] = din.readInt();
				}
				din.close();
			} catch (Exception e) {
				//file may not exist
				System.out.println("Exception: " + e.getMessage());
			}
		}
		if (saveStateData[0] == 0) {
			return;
		}
		int index = 1;
		int[] s = saveStateData;
		index = mainCpu.loadState(s, index);
		IMemory im = memory.getMainMemory();
		for (int i = 0; i < 0x3000; i++) {
			im.writeByte(i, s[index++]); 
		}
		memory.scrollX = s[index++];
		memory.scrollY = s[index++];
		memory.bankSwitch = s[index++];
	}

	/**
	 * Periodically update the emulated CPU and redraw the screen.
	 */
	public void emulate() {
		int watchdogFrame = WATCHDOG_TIME; // check watchdog every 5 seconds
		
		Long time = System.nanoTime();;
		// This is the main loop!
		while (true) {
			
			while(screen.isPaused() && screen.isRunning()) {
				try {Thread.sleep(100);} catch (Exception e) {};
				if (screen.isSaveState()) {
					saveState();
				}
				if (screen.isLoadState()) {
					loadState();
				}
			}
			
			//Check the watchdog
			watchdogFrame = checkWatchdog(watchdogFrame);

			// Sound CPU
			// Sound is updated 4x per frame, each update with interrupt.
			for (int i= 0; i< 4; i++) {
				soundCpu.executeSetInput(Z80Core.INPUT_LINE_IRQ0, Z80Core.HOLD_LINE);
				soundCpu.executeCycles(SND_CYCLES_PER_FRAME /  32);
				soundCpu.executeSetInput(Z80Core.INPUT_LINE_IRQ0, Z80Core.CLEAR_LINE);
				soundCpu.executeCycles(SND_CYCLES_PER_FRAME / 4);
				//try {Thread.sleep(3);} catch (Exception e) {};
			}
			
			// Main CPU
			// simulate VBLANK IRQ is held for the duration of VBLANK 
			mainCpu.set_irq_line(m6809H.M6809_IRQ_LINE, m6809.HOLD_LINE);
			mainCpu.execute(MAIN_CYCLES_PER_FRAME / 20);
			mainCpu.set_irq_line(m6809H.M6809_IRQ_LINE, m6809.CLEAR_LINE);

			mainCpu.execute(MAIN_CYCLES_PER_FRAME);

			// screen was closed -> exit emulation
			if (!screen.isRunning()) {
				break;
			}
			// get the key states from the screen and set it to the memory handler,
			// so that main CPU can get the joystick inputs by reading from specific memory address.
			memory.setKeyState(screen.getKeyState());

			// now draw the video memory on the screen
			if (frameSkipCounter < 0) {
				frameSkipCounter = FRAME_SKIP;
				video.repaintScreen(screen);
			}
			frameSkipCounter--;
			
			// this is a naive FPS synchronisation
			long now = System.nanoTime();
			if (now - time < FRAME_TIME) {
				LockSupport.parkNanos(FRAME_TIME - (now - time));
				time = System.nanoTime();
			} else {
				Thread.yield();
				time = now;
				//System.out.println("yield");
			}
			
			// flip the screen to show the new contents
			screen.flushGraphics();
			
			// Where is the sound, you may ask? Well, the sound chips
			// are driven from the emulated CPU via memory interface.
			// The sound CPU writes to certain addresses that feed the
			// sound chips with commands and data. The FM chip emulator
			// transform these commands/data into a PCM streams which are 
			// then requested by the sound streamer at regular intervals.

			//System.out.println("-----------------");
		}
		if (uw != null) {
			uw.write(0,  0xFF, 0xFF); //reset
			try { Thread.sleep(1000); } catch (Exception e) {}
			uw.close();
		}
		System.out.println("Emulation stopped.");
	}

	private int checkWatchdog(int watchdogFrame) {
		//watch dog will reset the machine if the machine / CPU is frozen
		watchdogFrame--;
		if (watchdogFrame == 0) {
			if (watchdogCounter == 0) {
				System.out.println("Watchdog triggered - reseting machine!");
				instance.mainCpu.reset();
				instance.soundCpu.reset();
			}
			watchdogFrame = WATCHDOG_TIME;
			watchdogCounter = 0;
		}
		return watchdogFrame;
	}	

	public int handleIrq(Object cpu, int irqLine, int interruptMode) {
		//System.out.println("Handle IRQ cpu=" + cpu + " irq=" + irqLine);
		return 0xff;
	}
}
