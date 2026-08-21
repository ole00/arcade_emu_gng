/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator.gng;

import java.io.IOException;
import java.io.InputStream;

import res.ResourceLoader;
import emulator.IMemory;
import emulator.processor.ym2203.fm;

/**
 	Memory map based on the MAME driver.
    ====================================================
    
    static ADDRESS_MAP_START( gng_map, AS_PROGRAM, 8, gng_state )
	AM_RANGE(0x0000, 0x1dff) AM_RAM
	AM_RANGE(0x1e00, 0x1fff) AM_RAM AM_SHARE("spriteram")
	AM_RANGE(0x2000, 0x27ff) AM_RAM_WRITE(gng_fgvideoram_w) AM_SHARE("fgvideoram")
	AM_RANGE(0x2800, 0x2fff) AM_RAM_WRITE(gng_bgvideoram_w) AM_SHARE("bgvideoram")
	AM_RANGE(0x3000, 0x3000) AM_READ_PORT("SYSTEM")
	AM_RANGE(0x3001, 0x3001) AM_READ_PORT("P1")
	AM_RANGE(0x3002, 0x3002) AM_READ_PORT("P2")
	AM_RANGE(0x3003, 0x3003) AM_READ_PORT("DSW1")
	AM_RANGE(0x3004, 0x3004) AM_READ_PORT("DSW2")
	AM_RANGE(0x3800, 0x38ff) AM_DEVWRITE("palette", palette_device, write_ext) AM_SHARE("palette_ext")
	AM_RANGE(0x3900, 0x39ff) AM_DEVWRITE("palette", palette_device, write) AM_SHARE("palette")
	AM_RANGE(0x3a00, 0x3a00) AM_WRITE(soundlatch_byte_w)
	AM_RANGE(0x3b08, 0x3b09) AM_WRITE(gng_bgscrollx_w)
	AM_RANGE(0x3b0a, 0x3b0b) AM_WRITE(gng_bgscrolly_w)
	AM_RANGE(0x3c00, 0x3c00) AM_NOP // watchdog?
	AM_RANGE(0x3d00, 0x3d00) AM_WRITE(gng_flipscreen_w)
    //  { 0x3d01, 0x3d01, reset sound cpu?
	AM_RANGE(0x3d02, 0x3d03) AM_WRITE(gng_coin_counter_w)
	AM_RANGE(0x3e00, 0x3e00) AM_WRITE(gng_bankswitch_w)
	AM_RANGE(0x4000, 0x5fff) AM_ROMBANK("bank1")
	AM_RANGE(0x6000, 0xffff) AM_ROM
	
	-----
	static ADDRESS_MAP_START( sound_map, AS_PROGRAM, 8, gng_state )
	AM_RANGE(0x0000, 0x7fff) AM_ROM
	AM_RANGE(0xc000, 0xc7ff) AM_RAM
	AM_RANGE(0xc800, 0xc800) AM_READ(soundlatch_byte_r)
	AM_RANGE(0xe000, 0xe001) AM_DEVWRITE("ym1", ym2203_device, write)
	AM_RANGE(0xe002, 0xe003) AM_DEVWRITE("ym2", ym2203_device, write)
	

 */

public class SystemMemory {
	
	Machine machine;
	int keyState = 0;
	
	int gfxBank = 0;  //gfx bank index
	int palBank = 0;  //palette bank index
	
	byte[] mainMem;
	byte[] sndMem;
	byte[] gfxrom1;
	byte[] gfxrom2;
	byte[] gfxrom3;
	byte[] prom;
		
	byte dipSwitches  = 0x7e;
	int sndLatch;
	long scanTime;
	int bankSwitch;
	int scrollX;
	int scrollY;
	int fm1Addr;
	int fm2Addr;
	
	public boolean debugOn;
	
	IMemory mainMemory;
	IMemory soundMemory;
	
	public SystemMemory(String romZipFileName) {
		mainMem = new byte[0x18000];
		sndMem = new byte[0xc800];
		gfxrom1 = new byte[ 1 * 0x4000];
		gfxrom2 = new byte[ 6 * 0x4000];
		gfxrom3 = new byte[ 8 * 0x4000];
		prom = new byte[0x200];
		load(romZipFileName);
		bankSwitch = 4;
		
		mainMemory = new IMemory() {
			public void writeWord(int address, int data) {
				writeWordMain(address, data);
			}
			
			public void writeByte(int address, int data) {
				writeByteMain(address, data);
			}
			
			public int readWord(int address) {
				return readWordMain(address);
			}
			
			public int readByte(int address) {
				return readByteMain(address);
			}
		};
		
		soundMemory = new IMemory() {
			
			public void writeWord(int address, int data) {
				writeWordSound(address, data);
			}
			
			public void writeByte(int address, int data) {
				writeByteSound(address, data);
			}
			
			public int readWord(int address) {
				return readWordSound(address);
			}
			
			public int readByte(int address) {
				return readByteSound(address);
			}
		};
	}
	
	public IMemory getMainMemory() {
		return mainMemory;
	}
	
	public IMemory getSoundMemory() {
		return soundMemory;
	}
	
	public int getScrollX() {
		return scrollX;
	}
	public int getScrollY() {
		return scrollY;
	}
	
	public void reset() {
		
		//clean the ram only
		for (int i = 0x000; i < 0x3000; i++) {
			mainMem[i] = 0;
		}
		bankSwitch = 4;
		scrollX = 0;
		scrollY = 0;
		
		//Sn76496.reset(0);
	}
	
	private void load(String rom) {
		
		//map roms and pages
		try {
			//Code ROM at 0x0000 - 0xffff
			readRom(rom, "gg4.bin", mainMem, 0x04000, 0x4000);
			readRom(rom, "gg3.bin", mainMem, 0x08000, 0x8000);
			readRom(rom, "gg5.bin", mainMem, 0x10000, 0x8000);
			
			//GFX1 - characters
			readRom(rom, "gg1.bin", gfxrom1, 0x00000, 0x4000);
			//GFX2 - tiles
			readRom(rom, "gg11.bin", gfxrom2, 0x00000, 0x4000); // tiles 0-1 Plane 1
			readRom(rom, "gg10.bin", gfxrom2, 0x04000, 0x4000);
			readRom(rom, "gg9.bin",  gfxrom2, 0x08000, 0x4000);
			readRom(rom, "gg8.bin",  gfxrom2, 0x0C000, 0x4000);		
			readRom(rom, "gg7.bin",  gfxrom2, 0x10000, 0x4000);		
			readRom(rom, "gg6.bin",  gfxrom2, 0x14000, 0x4000);
			
			//GFX3 - sprites
			readRom(rom, "gg17.bin", gfxrom3, 0x00000, 0x4000); // sprites 0, plane 1-2
			readRom(rom, "gg16.bin", gfxrom3, 0x04000, 0x4000); // sprites 1, plane 1-2
			readRom(rom, "gg15.bin",  gfxrom3, 0x08000, 0x4000);// sprites 2, plane 1-2
			//hole
			readRom(rom, "gg14.bin",  gfxrom3, 0x10000, 0x4000); //sprites 0, plane 3-4		
			readRom(rom, "gg13.bin",  gfxrom3, 0x14000, 0x4000); //sprites 1, plane 3-4		
			readRom(rom, "gg12.bin",  gfxrom3, 0x18000, 0x4000);// sprites 2, plane 3-4
			
			readRom(rom, "gg2.bin",  sndMem, 0x0000, 0x8000); //sound code
			
			ResourceLoader.getInstance().closeZipResource(rom);
			
		} catch (IOException ioe) {
			throw new RuntimeException(ioe.getMessage() + " : " + rom);
		}
	}

	private void readRom(String zipName, String name, byte[] m, int offset, int size) throws IOException {
		InputStream in = ResourceLoader.getInstance().getZipResource(zipName, name);
		try {
			readRom(in, m, offset, size);
			in.close();
		} catch (Exception e) {
			throw new IOException("failed to read file: " + name + " size=" + size);
		}
	}

	
	private void readRom(InputStream is, byte[] m, int offset, int size) throws IOException {
		int total = 0;
		
		while (total < size) {
			int read = is.read(m, offset + total, size - total);
			if (read >= 0) {
				total += read;
			} else {
				throw new IOException ("failed to read stream. Read size="  + size);
			}
		}
	}
	
	public int getCharacterAddress() {
		return 0x0000;
	}
	
	public byte[] getCharacterMemory(int index) {
		if (index == 0) {
			return gfxrom1;
		}
		if (index == 1) {
			return gfxrom2;
		}
		return gfxrom3;
	}
	
	public int getPalAddress() {
		return 0x000;
	}
	
	public byte[] getPalMemory() {
		return prom;
	}
	
	public byte[] getMemory() {
		return mainMem;
	}
	
	public void setKeyState(int state) {
		keyState = state;
	
	}
	
	private int readByteMain(int addr) {
		
		if (addr == 0x3003) {
			//return 0xFF; // dip switch 1
			return 0b11011111; // attract sound on
			//System.out.println("dip 1");
			//return 0b10111111; // service mode
		} else
		if (addr == 0x3004) {
			return 0xFF; // dip switch 2
			//return 0;
		} else
		//buttons P1
		if (addr == 0x3001) {
			int value = 0xFF;
			if ((keyState & Machine.IO_P1_LEFT) == 0) {
				value &= ~2; //active low
			}
			if ((keyState & Machine.IO_P1_RIGHT) == 0) {
				value &= ~1; //active low
			}
			if ((keyState & Machine.IO_P1_UP) == 0) {
				value &= ~8; //active low
			}
			if ((keyState & Machine.IO_P1_DOWN) == 0) {
				value &= ~4; //active low
			}
			if ((keyState & Machine.IO_P1_B1) == 0) {
				value &= ~0x10; //active low
			}
			if ((keyState & Machine.IO_P1_B2) == 0) {
				value &= ~0x20; //active low
			}
			return value;
		} else
		// buttons P2
		if (addr == 0x3002) {
			int value = 0xFF;
			return value;
		} else
		
		// system buttons (coin / start)
		if (addr == 0x3000) {
			
			int value = 0xFF;
			if ((keyState &  Machine.IO_P1_COIN) == 0) { //coin
				value &= ~0x40; //active low
			}
			if ((keyState & Machine.IO_P1_START) == 0) { //start
				value &= ~1; //active low
			}
			return value;
		}
		else
		// bank switched memory area
		if (addr >= 0x4000 && addr < 0x6000) {
			int bankAddr = bankSwitch == 4 ? 0x4000 : (0x10000 | (bankSwitch << 13));
	

			//System.out.println("Bank switch: " + Integer.toHexString(bankAddr));
			return mainMem[bankAddr + (addr - 0x4000)] & 0xFF;
		}
		else if (addr == 0x5A0A ) {
			//System.out.println("0x26 Level! " + debugOn);
			return debugOn ? 3 : mainMem[addr] & 0xff;
		}
		//System.out.println("read from: 0x" + Integer.toHexString(addr) + " val=0x" + Integer.toHexString(mainMem[addr] & 0xFF));
		return mainMem[addr] & 0xFF;
	}
	
	public void writeByteMain(int addr, int val) {
		if (addr == 0x3C00) {
			Machine.watchDogTick();
			//System.out.println("Watchdog reset val=" + val);
		} else
		if (addr == 0x3b08) {
			//System.out.println("scrollX-low=" + val);
			scrollX = (scrollX & 0xFF00) | (val & 0xFF);
			//System.out.println("scrollX=" + scrollX);
		} else
		if (addr == 0x3b09) {
			//System.out.println("scrollX-high=" + val);
			scrollX = ((val & 0xFF) << 8) | (scrollX & 0xFF);
			//System.out.println("scrollX=" + scrollX);
			
		} else
		if (addr == 0x3b0a) {
			//System.out.println("scrollY-low=" + val);
			scrollY = (scrollY & 0xFF00) | (val & 0xFF);
			//System.out.println("scrollY=" + scrollY);
		} else
		if (addr == 0x3b0b) {
			//System.out.println("scrollY-high=" + val);
			scrollY = ((val & 0xFF) << 8) | (scrollY & 0xFF);
			//System.out.println("scrollY=" + scrollY);
		} else
		if (addr == 0x3e00) {
			//System.out.println("bank switch: " + val);
			bankSwitch = val;
		} else
		if (addr == 0x3a00) {
			//System.out.println("snd write latch val=" + val);
			sndLatch = val & 0xFF;
		} else
		if (addr >= 0x3800 && addr <= 0x38FF) {
			//System.out.println("palette2 update at " + (addr - 0x3800));
			mainMem[addr] = (byte) (val & 0xFF);
		} else
		if (addr >= 0x3900 && addr <= 0x39FF) {
			//System.out.println("palette1 update at " + (addr - 0x3900));
			mainMem[addr] = (byte) (val & 0xFF);
		} else
		if (addr >= 0x4000) {
			System.out.println("writing to ROM addr  0x" + Integer.toHexString(addr) );
		} else
		{
			//System.out.println("unknown write addr=0x" + Integer.toHexString(addr) + " val=0x" + Integer.toHexString(val));
			mainMem[addr] = (byte) (val & 0xFF);
		}
	}
	

	public int readWordMain(int address) {
		return ((mainMem[address + 1] & 0xFF) << 8) | (mainMem[address] & 0xFF);
	}


	public void writeWordMain(int address, int data) {
		mainMem[address++] = (byte)(data & 0xFF);
		mainMem[address] = (byte)(data >> 8);
	}

	private int readByteSound(int addr) {
		if (addr == 0xC800) {
			//System.out.println("SN read latch");			
			return sndLatch;
		}
		return sndMem[addr] & 0xFF;
	}
	
	public void writeByteSound(int addr, int val) {
		if (addr == 0xe000 || addr == 0xe001) {
			//System.out.println("YM1 write val=" + val);
			//ym2203-1
			fm.YM2203Write(0, addr, val);
			if (machine.uw != null) {
				if ((addr & 1) == 0) {
					fm1Addr = val;
				} else {
					machine.uw.write(0, fm1Addr, val);
				}
			}
		} else
		if (addr == 0xe002 || addr == 0xe003) {
			//System.out.println("YM2 write val=" + val);
			//ym2203-2
			fm.YM2203Write(1, addr, val);
			if (machine.uw != null) {
				if ((addr & 1) == 0) {
					fm2Addr = val;
				} else {
					machine.uw.write(1, fm2Addr, val);
				}
			}
		} else
			sndMem[addr] = (byte) (val & 0xFF);
	}
	
	public int readWordSound(int address) {
		if (address >= 0xe804) {
			return 0;
		}
		return ((sndMem[address + 1] & 0xFF) << 8) | (sndMem[address] & 0xFF);
	}

	public void writeWordSound(int address, int data) {
		sndMem[address++] = (byte)(data & 0xFF);
		sndMem[address] = (byte)(data >> 8);
	}
}
