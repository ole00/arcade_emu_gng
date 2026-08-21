/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator.gng;

import java.awt.image.BufferedImage;

import emulator.EmuScreen;

/*
 
static const gfx_layout charlayout =
{
	8,8,            // 8 x 8 pixels per 1 character
	RGN_FRAC(1,1),  //number of characters
	2,              // bits per pixel
	{ 4, 0 },       // 2 bitplanes
	{ 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 },                    // x offsets
	{ 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16 },    // y offsets
	16*8                                                   // bits per character (16 bytes)
}; 
 

 */


/**
 * Video decoding and rendering based on:
 * MAME emulator
 */

public class Video {
	final static int SCREEN_TILES_H = 32; //256 pixels
	final static int SCREEN_TILES_V = 28; //224 pixels
	
	final static int VRAM_TILES_H = 32;
	final static int VRAM_TILES_V = 32;
	final static int VRAM_SCREEN_LINE = 2; //first tile starts at the 3rd row (follows 2 empty rows)
	
	private SystemMemory m;
	private int[] palette;
	
	private BufferedImage charImage; // single character image 8x8
	private BufferedImage tileImage; // single tile image 16x16
	
	private byte[] charData;
	private byte[] tileData;
	private byte[] spriteData;

	private int tileMap[];
	private int frame;
	private int maxActiveSprites;
	private int firstSpriteEver;

	public Video(SystemMemory m ) {
		this.m = m;
	}
	
	static void delay(long time) {
		try {
			Thread.sleep(time);
		} catch (Exception e) {
			
		}
	}
	
	public void init() {
		//single character image - a tile of 8x8 pixels
		charImage = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB );
		tileImage = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB );
		
		//tile map keeps track of which tile was rendered on the screen
		tileMap = new int[SCREEN_TILES_H * SCREEN_TILES_V];
		for (int i = 0; i < tileMap.length; i++) {
			tileMap[i] = -1; //invalid tile
		}
		createPalette(); //decode palette data into ARGB8888 format
		createCharacters(); //decode characters
		createTiles(); //decode tile graphics
		createSprites(); // decode sprite pixels
		
		firstSpriteEver = 0;
	}
	

	private void setPaletteMemColor(int index, int color) {
		int palAddr1 = 0x3800;
		int palAddr2 = 0x3900;
		byte[] ram = m.getMemory();
		short c1 = (short)(((color & 0xF00000) >> 20) | ((color & 0xF000) >> 12));
		short c2 = (short) (color & 0xF0);
		ram[palAddr1 + index] = (byte) c1;
		ram[palAddr2 + index] = (byte) c2;	
	}
	private void createPalette() {
		int i;
		palette = new int[256 + 64];
		int palAddress = m.getPalAddress();
		byte[] buffer = m.getPalMemory();
	

		// palette is dynamically created during the game play
		// set up some initial colors
		for (int j = 0; j < 256; j++) {
			int r = (j & 0b11100000) >> 5;
			int g = (j & 0b00011100) >> 2;
			int b = j & 0b00000011;
			setPaletteMemColor(j, 0xFF000000 | (g << 21) | (b << 13) | (r << 6));
		}
		
		// test tile palette 
		setPaletteMemColor(0,0xFFCCCCAA);
		setPaletteMemColor(1, 0xFFEECC00);
		setPaletteMemColor(2, 0xFF665500);
		setPaletteMemColor(3, 0x00000000);
		
		// test bg tiles palette (8 colors per tile)
		setPaletteMemColor(16, 0x00000000);
		setPaletteMemColor(17, 0xFF433333);
		setPaletteMemColor(18, 0xFF655555);
		setPaletteMemColor(19, 0xFF776666);
		setPaletteMemColor(20, 0xFF877777);
		setPaletteMemColor(21, 0xFF336600);
		setPaletteMemColor(22, 0xFF558743);
		setPaletteMemColor(23, 0xFF000000);
		
		

	}

	private void decodeGraphics(byte[] rom, int srcOffset, byte[] dst, int dstOffset) {
		//every 8x8 char/tile takes 32 consecutive bytes, that is 4 bits per pixel 
		for (int j = 0; j < 32; j++) {
			int pix2 = rom[srcOffset] & 0xF;
			int pix1 = (rom[srcOffset] & 0xF0) >> 4;
			dst[dstOffset++] = (byte)(pix1);
			dst[dstOffset++] = (byte)(pix2);
			srcOffset++;
		}
	}
/*
// MAME
static const gfx_layout charlayout =
{
	8,8,                       //8 x 8 pixels
	RGN_FRAC(1,1),
	2,                         // 2 bitplanes (2 bits per pixel)
	{ 4, 0 },                  // bitplanes at bit position 0 and 4 within the byte
	{ 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3 }, // line pixel structure: first 4 pixels start at bit position 0, 1, 2 ,3, the nex4 pixels at bits 8,9,10,11
	                                    // 1st pixel: bit position 0 and 4 (value 4 is determined by the offset defined by the bitplane)
	                                    // 2nd pixel: bit position 1 and 5
	                                    // 3rd pixel: bit position 2 and 6 
	{ 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16 }, // line start offsets in bits
	                                    // 1st line : offset 0  (at byte index 0)
	                                    // 2nd line : offset 16 (at byte index 2)
	                                    // 3rd line : offset 32 (at byte index 4) 
	                                    
	16*8                        // 16 bytes per character
};	 
*/
	private void decodeCharacter(byte[] rom, int srcOffset, byte[] dst, int dstOffset) {
		//every 8x8 character takes 16 consecutive bytes, that is 2 bits per pixel 
		for (int j = 0; j < 16; j++) {
			int pix4 = (rom[srcOffset] & 0b00010001);
			int pix3 = (rom[srcOffset] & 0b00100010) >> 1;
			int pix2 = (rom[srcOffset] & 0b01000100) >> 2;
			int pix1 = (rom[srcOffset] & 0b10001000) >> 3;
			pix4 = ((pix4 & 0b1)<<1) | (pix4 >> 4);
			pix3 = ((pix3 & 0b1)<<1) | (pix3 >> 4);
			pix2 = ((pix2 & 0b1)<<1) | (pix2 >> 4);
			pix1 = ((pix1 & 0b1)<<1) | (pix1 >> 4);
			dst[dstOffset++] = (byte)(pix1);
			dst[dstOffset++] = (byte)(pix2);
			dst[dstOffset++] = (byte)(pix3);
			dst[dstOffset++] = (byte)(pix4);
			srcOffset++;
		}
	}
	
	// Characters are 2 bits per pixel, 8x8 pixels.
	// Pixels are organised sequentially, one character takes 16 consecutive bytes. 
	// In total there is 1024 characters (1024*16 => 16384 (0x4000)
	private void createCharacters() {
		//512 characters in total, each is 8 * 8 pixels
		charData = new byte[8 * 8 * 1024];
		byte[] rom = m.getCharacterMemory(0);
		int srcOffset = 0;
		int dstOffset = 0;
		
		//decode each of the 512 characters one by one
		// 2 bits per pixel for 1 character
		for (int c = 0; c < 1024; c++) {
			decodeCharacter(rom, srcOffset, charData, dstOffset);
			srcOffset += 16;
			dstOffset += 64;
		}
	}

	
	/*
	// MAME
static const gfx_layout tilelayout =
{
	16,16,
	RGN_FRAC(1,3),
	3,   // 3 bitplanes
	{ RGN_FRAC(2,3), RGN_FRAC(1,3), RGN_FRAC(0,3) },  // bitplanes are split into the thirds of the whole pixel data/
	{ 0, 1, 2, 3, 4, 5, 6, 7,
			16*8+0, 16*8+1, 16*8+2, 16*8+3, 16*8+4, 16*8+5, 16*8+6, 16*8+7 },
	{ 0*8, 1*8, 2*8, 3*8, 4*8, 5*8, 6*8, 7*8,
			8*8, 9*8, 10*8, 11*8, 12*8, 13*8, 14*8, 15*8 },
	32*8
};
	*/
		private void decodeTile(byte[] rom, int srcOffset, byte[] dst, int dstOffset) {
			int bitplaneSize = 0x8000;
			int bpOffset2 = bitplaneSize;
			int bpOffset3 = bpOffset2 + bitplaneSize;
			// 
			for (int j = 0; j < 16; j++) {
				int bp1 = ((rom[srcOffset] & 0xFF) << 8) | (rom[srcOffset + 16] & 0xFF);
				int bp2 = ((rom[bpOffset2 + srcOffset] & 0xFF) << 8) | (rom[bpOffset2 + srcOffset + 16] & 0xFF);
				int bp3 = ((rom[bpOffset3 + srcOffset] & 0xFF) << 8) | (rom[bpOffset3 + srcOffset + 16] & 0xFF);
				
				for (int i = 0; i < 16; i++) {
					int mask = 1 << i;
					int pix = ((bp3 & mask) << 2) | ((bp2 & mask) << 1) | (bp1 & mask) ;
					dst[dstOffset + 15 -i] = (byte)(pix >> i);
				}
				srcOffset++;
				dstOffset += 16;
			}
		}
	
	// Tiles are 3 bits per pixel, 16x16 pixels.
	// In total there is 1024 tiles (98304 bytes (0x18000))
	private void createTiles() {
		//1024 tiles in total, each is 16 * 16 pixels
		tileData = new byte[16 * 16 * 1024];
		byte[] rom = m.getCharacterMemory(1);
		int srcOffset = 0;
		int dstOffset = 0;
		
		//decode each of the 1024 tiles one by one
		for (int c = 0; c < 1024; c++) {
			decodeTile(rom, srcOffset, tileData, dstOffset);
			srcOffset += 32;
			dstOffset += 256;
		}
	}

	
	/*
	// MAME
static const gfx_layout spritelayout =
{
	16,16,
	RGN_FRAC(1,2),
	4,
	{ RGN_FRAC(1,2)+4, RGN_FRAC(1,2)+0, 4, 0 },
	{ 0, 1, 2, 3, 8+0, 8+1, 8+2, 8+3,
			32*8+0, 32*8+1, 32*8+2, 32*8+3, 33*8+0, 33*8+1, 33*8+2, 33*8+3 },
	{ 0*16, 1*16, 2*16, 3*16, 4*16, 5*16, 6*16, 7*16,
			8*16, 9*16, 10*16, 11*16, 12*16, 13*16, 14*16, 15*16 },
	64*8
};
	*/
		private void decodeSprite(byte[] rom, int srcOffset, byte[] dst, int dstOffset) {
			// iterate over each sprite line
			for (int j = 0; j < 16; j++) {
				int offs = srcOffset;
				// bitplanes 1 & 2
				int pix4 = (rom[srcOffset] & 0b00010001);
				int pix3 = (rom[srcOffset] & 0b00100010) >> 1;
				int pix2 = (rom[srcOffset] & 0b01000100) >> 2;
				int pix1 = (rom[srcOffset] & 0b10001000) >> 3;
				pix4 = ((pix4 & 0b1)<<1) | (pix4 >> 4);
				pix3 = ((pix3 & 0b1)<<1) | (pix3 >> 4);
				pix2 = ((pix2 & 0b1)<<1) | (pix2 >> 4);
				pix1 = ((pix1 & 0b1)<<1) | (pix1 >> 4);

				srcOffset++;
				int pix8 = (rom[srcOffset] & 0b00010001);
				int pix7 = (rom[srcOffset] & 0b00100010) >> 1;
				int pix6 = (rom[srcOffset] & 0b01000100) >> 2;
				int pix5 = (rom[srcOffset] & 0b10001000) >> 3;
				pix8 = ((pix8 & 0b1)<<1) | (pix8 >> 4);
				pix7 = ((pix7 & 0b1)<<1) | (pix7 >> 4);
				pix6 = ((pix6 & 0b1)<<1) | (pix6 >> 4);
				pix5 = ((pix5 & 0b1)<<1) | (pix5 >> 4);

				srcOffset+= 31;
				int pix12 = (rom[srcOffset] & 0b00010001);
				int pix11 = (rom[srcOffset] & 0b00100010) >> 1;
				int pix10 = (rom[srcOffset] & 0b01000100) >> 2;
				int pix9 = (rom[srcOffset] & 0b10001000) >> 3;
				pix12 = ((pix12 & 0b1)<<1) | (pix12 >> 4);
				pix11 = ((pix11 & 0b1)<<1) | (pix11 >> 4);
				pix10 = ((pix10 & 0b1)<<1) | (pix10 >> 4);
				pix9 = ((pix9 & 0b1)<<1) | (pix9 >> 4);

				srcOffset++;
				int pix16 = (rom[srcOffset] & 0b00010001);
				int pix15 = (rom[srcOffset] & 0b00100010) >> 1;
				int pix14 = (rom[srcOffset] & 0b01000100) >> 2;
				int pix13 = (rom[srcOffset] & 0b10001000) >> 3;
				pix16 = ((pix16 & 0b1)<<1) | (pix16 >> 4);
				pix15 = ((pix15 & 0b1)<<1) | (pix15 >> 4);
				pix14 = ((pix14 & 0b1)<<1) | (pix14 >> 4);
				pix13 = ((pix13 & 0b1)<<1) | (pix13 >> 4);
				
				// bitplane 3 & 4
				srcOffset = offs + 0x10000;
				int pix4b = (rom[srcOffset] & 0b00010001);
				int pix3b = (rom[srcOffset] & 0b00100010) >> 1;
				int pix2b = (rom[srcOffset] & 0b01000100) >> 2;
				int pix1b = (rom[srcOffset] & 0b10001000) >> 3;
				pix4b = ((pix4b & 0b1)<<1) | (pix4b >> 4);
				pix3b = ((pix3b & 0b1)<<1) | (pix3b >> 4);
				pix2b = ((pix2b & 0b1)<<1) | (pix2b >> 4);
				pix1b = ((pix1b & 0b1)<<1) | (pix1b >> 4);

				srcOffset++;
				int pix8b = (rom[srcOffset] & 0b00010001);
				int pix7b = (rom[srcOffset] & 0b00100010) >> 1;
				int pix6b = (rom[srcOffset] & 0b01000100) >> 2;
				int pix5b = (rom[srcOffset] & 0b10001000) >> 3;
				pix8b = ((pix8b & 0b1)<<1) | (pix8b >> 4);
				pix7b = ((pix7b & 0b1)<<1) | (pix7b >> 4);
				pix6b = ((pix6b & 0b1)<<1) | (pix6b >> 4);
				pix5b = ((pix5b & 0b1)<<1) | (pix5b >> 4);

				srcOffset+= 31;
				int pix12b = (rom[srcOffset] & 0b00010001);
				int pix11b = (rom[srcOffset] & 0b00100010) >> 1;
				int pix10b = (rom[srcOffset] & 0b01000100) >> 2;
				int pix9b = (rom[srcOffset] & 0b10001000) >> 3;
				pix12b = ((pix12b & 0b1)<<1) | (pix12b >> 4);
				pix11b = ((pix11b & 0b1)<<1) | (pix11b >> 4);
				pix10b = ((pix10b & 0b1)<<1) | (pix10b >> 4);
				pix9b = ((pix9b & 0b1)<<1) | (pix9b >> 4);

				srcOffset++;
				int pix16b = (rom[srcOffset] & 0b00010001);
				int pix15b = (rom[srcOffset] & 0b00100010) >> 1;
				int pix14b = (rom[srcOffset] & 0b01000100) >> 2;
				int pix13b = (rom[srcOffset] & 0b10001000) >> 3;
				pix16b = ((pix16b & 0b1)<<1) | (pix16b >> 4);
				pix15b = ((pix15b & 0b1)<<1) | (pix15b >> 4);
				pix14b = ((pix14b & 0b1)<<1) | (pix14b >> 4);
				pix13b = ((pix13b & 0b1)<<1) | (pix13b >> 4);

				//combine planes 1,2 & 3,4 into the final pixel on the sprite line 
				dst[dstOffset++] = (byte)(pix1 | (pix1b << 2));
				dst[dstOffset++] = (byte)(pix2 | (pix2b << 2));
				dst[dstOffset++] = (byte)(pix3 | (pix3b << 2));
				dst[dstOffset++] = (byte)(pix4 | (pix4b << 2));
				dst[dstOffset++] = (byte)(pix5 | (pix5b << 2));
				dst[dstOffset++] = (byte)(pix6 | (pix6b << 2));
				dst[dstOffset++] = (byte)(pix7 | (pix7b << 2));
				dst[dstOffset++] = (byte)(pix8 | (pix8b << 2));
				dst[dstOffset++] = (byte)(pix9 | (pix9b << 2));
				dst[dstOffset++] = (byte)(pix10 | (pix10b << 2));
				dst[dstOffset++] = (byte)(pix11 | (pix11b << 2));
				dst[dstOffset++] = (byte)(pix12 | (pix12b << 2));
				dst[dstOffset++] = (byte)(pix13 | (pix13b << 2));
				dst[dstOffset++] = (byte)(pix14 | (pix14b << 2));
				dst[dstOffset++] = (byte)(pix15 | (pix15b << 2));
				dst[dstOffset++] = (byte)(pix16 | (pix16b << 2));

							
				srcOffset = offs+2;
			}
		}	
	
	private void createSprites() {
		//1024 sprites in total, each is 16 * 16 pixels
		spriteData = new byte[16 * 16 * 1024];
		byte[] rom = m.getCharacterMemory(2);
		int srcOffset = 0;
		int dstOffset = 0;
		
		//decode each of the 1024 sprites one by one
		for (int c = 0; c < 1024; c++) {
			decodeSprite(rom, srcOffset, spriteData, dstOffset);
			srcOffset += 64;
			dstOffset += 256;
		}
	}
	
	public int getWidth() {
		return SCREEN_TILES_H * 8;
	}
	
	public int getHeight() {
		return SCREEN_TILES_V * 8;
	}
	
	//set the tile pixels into the charImage. The palette is applied.
	private void setCharacter(byte[] pixelData, int dataIndex, int palIndex, int flipXY) {
		int i = 0;
		palIndex <<= 2; // 4 colors per palette
		switch (flipXY) {
		case 0:
		while (i < 64) {
			charImage.setRGB((i & 0x7), i >> 3, palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 1:
		while (i < 64) {
			charImage.setRGB(7-(i & 0x7), i >> 3, palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 2:
		while (i < 64) {
			charImage.setRGB((i & 0x7), 7-(i >> 3), palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 3:
		while (i < 64) {
			charImage.setRGB(7-(i & 0x7), 7-(i >> 3), palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		} //end of switch
	}

	//set the tile pixels into the tileImage. The palette is applied.
	private void setTile(byte[] pixelData, int dataIndex, int palIndex, int flipXY) {
		int i = 0;
		switch (flipXY) {
		case 0:
		while (i < 256) {
			tileImage.setRGB((i & 0xF), i >> 4, palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 1:
		while (i < 256) {
			tileImage.setRGB(15-(i & 0xF), i >> 4, palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 2:
		while (i < 256) {
			tileImage.setRGB((i & 0xF), 15-(i >> 4), palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		case 3:
		while (i < 256) {
			tileImage.setRGB(15-(i & 0xF), 15-(i >> 4), palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} break;
		} //end of switch
	}

	
	private void setSprite(byte[] pixelData, int dataIndex, int palIndex, boolean flipX, boolean flipY) {
		int i = 0;

		if (!flipX  && !flipY) {
			while (i < 256) {
				tileImage.setRGB((i & 0xF), i >> 4, palette[palIndex + pixelData[dataIndex]]);
				dataIndex++;
				i++;
			}
			return;
		}
		
		if (flipX && !flipY) {
			while (i < 256) {
				tileImage.setRGB(15-(i & 0xF), i >> 4, palette[palIndex + pixelData[dataIndex]]);
				dataIndex++;
				i++;
			}
			return;
		}
		if (!flipX && flipY) {
			while (i < 256) {
				tileImage.setRGB((i & 0xF), 15-(i >> 4), palette[palIndex + pixelData[dataIndex]]);
				dataIndex++;
				i++;
			}
			return;
		}

		while (i < 256) {
			tileImage.setRGB(15-(i & 0xF), 15-(i >> 4), palette[palIndex + pixelData[dataIndex]]);
			dataIndex++;
			i++;
		} 
	}
	
	//draws a tile set on the screen - only for testing of the graphic decoder
	public void test(EmuScreen screen) {
		boolean drawCharacters = false;
		for (int i = 0; i < 16 * 32; i++) {
			int x = i % 16;
			int y = i / 16;
			if (drawCharacters) {
				setCharacter(charData, i << 6, 0 , 0); // use palette index 0
				screen.drawImage(charImage, 0, 0, 8, 8, x << 3, y << 3);
			} else {
				setTile(tileData, i << 8, 1 , 0); // use palette index 1
				screen.drawImage(tileImage, 0, 0, 16, 16, x << 4, y << 4);
			}
		}
		screen.flushGraphics();
		
		try {
			Thread.sleep(4000);
		} catch (Exception e) {
			
		}
	}
	
	//draws a palette on the screen - only for testing of the graphic decoder
	public void testPalette(EmuScreen screen) {
		for (int i = 0; i < 16 * 32; i++) {
			int x = i % 16;
			int y = i / 16;
			screen.fillRect(x << 3, y << 3, 8 , 8, palette[i]);
		}
		screen.flushGraphics();
		
		try {
			Thread.sleep(2000);
		} catch (Exception e) {
			
		}
	}
	
	//draws a palette on the screen - only for testing of the graphic decoder
	public void testSprite(EmuScreen screen) {
		screen.clear();
		for (int i = 0; i < 128; i++) {
			drawSprite(screen, i, 0, (i & 0xF) *16, (i >> 4) * 16, false, false);
			
		}
		screen.flushGraphics();
		

/*		
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x< 4; x++) {
				int value = (((y & 4)>>2) << 5) | (((x & 2)>>1) << 4) | ((y & 7) << 1) | (x &1);
				int v2 = ((y / 8) * 32) + ((x / 2) * 16) + ((y % 8) * 2) + (x %2);
				System.out.println("Value: (x=" +x + ",y=" + y + ") " + value + " v2=" + v2);
			}
		}
*/
		try {
			Thread.sleep(30000);
		} catch (Exception e) {
			
		}
	}
	
	//check whether a tile is already present on the screen.
	//returns true if a tile on particular coordinates doesn't need to be rendered again
	private boolean tileMatches(int x, int y, int tileData) {
		int index = (y * SCREEN_TILES_H) + x;
		if (tileMap[index] == tileData) {
			return true;
		}
		tileMap[index] = tileData;
		return false;
	}
	
	//makes a tile on particular coordinate dirty -> will be redrawn the next frame
	private void invalidateTileMap(int x, int y) {
		if (x >= SCREEN_TILES_H || x < 0 || y >= SCREEN_TILES_V) {
			return;
		}
		int index = (y * SCREEN_TILES_H) + x;
		tileMap[index] = -1;
	}
	
	private String toHex2(int i) {
		String val = Integer.toHexString(i);
		return "00".substring(val.length()) + val;
	}
	
	void repaintScreen(EmuScreen screen) {
		byte[] ram = m.getMemory();
		final int bgAddress = 0x2800; //address of the m6809 memory where background tilemap indices are kept
		final int fgAddress = 0x2000; //address of the m6809 memory where foreground tilemap indices are kept
		int palAddr1 = 0x3800;
		int palAddr2 = 0x3900;
		
		final int spriteAddress = 0x1e00; //address of the m6809 memory where sprite list is kept
		
		int scrollX = m.getScrollX();
		int scrollY = m.getScrollY();
		
		// axes are swapped
		int scrollXtile = scrollY >> 4; // / 16
		int scrollYtile = scrollX >> 4; // / 16
		
		int xDrawOffs = scrollX & 0xF;
		int yDrawOffs = scrollY & 0xF;

		frame++;
		
		// debug sprites
		if (false && frame == 4 * 60) {
			testSprite(screen);
		}
		
		//video memory consists of an area of 32 x 32 characters (tiles) 
		//each character contains 2 bytes where the character index and it's palette is encoded.
		
		//Here we synchronise the whole drawing block with the emulator screen.
		//Basically we wait till the screen has finished painting (note: paint method
		//of the screen is synchronised too). When the screen has finished painting
		//then we can start updating the character (tile) map. 
		synchronized(screen) 
		{
			// update palette which is dynamically copied to palette RAM every frame by the game
			for (int i = 0; i < 256; i++) {
				int p1 = ram[palAddr1++] & 0xFF;
				int p2 = ram[palAddr2++] & 0xFF;
				int r = p1 & 0xF0;
				int g = (p1 & 0xF) << 4;
				int b = p2 & 0xF0;
				int base = 0xFF000000;
				// background tiles
				if (i < 0x40) {
					int m = i % 8;
					//create copy of tile palette with transparencies - for 2nd tile rendering pass
					if (m == 0 || m ==6) {
						palette[256+i] = 0; // fully transparent
					} else {
						palette[256+i] = 0xFF000000 | (r << 16) | (g <<8) | b;
					}
				} else
				// sprites
				if (i < 0x80) {
					int m = i % 16; 
					if (m == 15) {
						base = 0;
					}
				} else
				// top most characters
				{
					if (i%4 == 3) {
						base = 0;
					}
				}
				palette[i] = base | (r << 16) | (g <<8) | b;
				//System.out.println("pal: " + i + ") " + toHex2(p1) + " " + toHex2(p2) + " val=" + Integer.toHexString(palette[i]));
			}
			
			
			// background tiles - pass 1
			// tile positions have swapped axes, so a line of tiles is stored as column in the video ram!
			// This allowed simplified preparation of a play field during scrolling by copying the visual column of tiles
			// into a video ram row. It makes perfect sense from the technical point of view, however it is a bit of a head 
			// scratcher when trying to visualise how the tiles are laid out.
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 17; x++) { // for scrolling purposes we draw an extra tile column
					int tileX = y;
					int tileY = x;
					int rollOverX = (tileX  + scrollXtile + 1) % (VRAM_TILES_H) ;
					int rollOverY = (tileY  + scrollYtile) % (VRAM_TILES_V) ;

					int memoryPos = bgAddress + (VRAM_TILES_H *  rollOverY);
					int attrPos = memoryPos + 0x400;
					int charIndex = ram[memoryPos + rollOverX] & 0xFF;
					int attr = ram[attrPos + rollOverX] & 0xFF;
					int tileGroup = (attr & 0x08) >> 3;
					
					charIndex +=  ((attr & 0xc0) << 2);
					
					int paltIndex = attr & 0x7; // low 3 bits
					int flipXY = (attr & 0x30) >> 4;

					//System.out.println("Char:" + charIndex + " pal=" + paltIndex);

					// re-color the tile according to the palette
					setTile(tileData, charIndex * 256, paltIndex << 3, flipXY); 
					//draw one tile (character)
					screen.drawImage(tileImage, 0, 0, 16, 16, (x << 4) - xDrawOffs, (y << 4) - yDrawOffs);
				}
			}
			
			//sprites - each sprite has 4 bytes, total sprite memory space is 512 bytes.
			// but only the lower part seems to be used.
			//up to 64 sprites? only sprites from position 53 were observed so far.
			// draw in reverse order
			int firstSprite = 1000;
			int activeSprites = 0;
			for (int i = 256-4; i > 0; i-=4) {
				int dataIndex = spriteAddress + i;
				int dataIndex2 = spriteAddress + i + 256; // second set of sprites ?
				
				int tileIndex = ram[dataIndex++] & 0xff;
				int attribs = ram[dataIndex++] & 0xff;
				int posY = ram[dataIndex++] & 0xff;
				int posX = ram[dataIndex] & 0xff;
				boolean flipX = (attribs & 0x4) != 0 ;
				boolean flipY = (attribs & 0x8) != 0;
				int paltIndex = (attribs >> 4) & 0x3;
				
				tileIndex |= (attribs << 2) & 0x300;
				posX -= 0x100 * (attribs & 0x1);

				//------------
				int tileIndex2 = ram[dataIndex2++] & 0xff;
				int attribs2 = ram[dataIndex2++] & 0xff;
				int posY2 = ram[dataIndex2++] & 0xff;
				int posX2 = ram[dataIndex] & 0xff;
				boolean flipX2 = (attribs2 & 0x4) != 0 ;
				boolean flipY2 = (attribs2 & 0x8) != 0;
				int paltIndex2 = (attribs2 >> 4) & 0x3;
				
				tileIndex2 |= (attribs2 << 2) & 0x300;
				posX2 -= 0x100 * (attribs2 & 0x1);

				if (false) {
					if (tileIndex != tileIndex2) {
						System.out.println("X "+ (i/4) + ") idx=" + tileIndex + "," + tileIndex2 );
					} else {
						System.out.println("O "+ (i/4) + ") idx=" + tileIndex + "," + tileIndex2 );
						
					}
				}
				
				
				if (posY > 0 && tileIndex < 768) {
					//System.out.println("spr pos=" + (i/4) + " spr index=" + tileIndex + " x=" + posX + " y=" + posY);
					drawSprite(screen, tileIndex, paltIndex, posX, posY - 16, flipX, flipY);
					activeSprites++;
					if (firstSprite == 1000) {
						firstSprite = (i/4);
					}
				}
			}
			if (frame > 6 * 60 && firstSprite != 1000 && firstSprite > firstSpriteEver) {
				firstSpriteEver = firstSprite;
			}
			
			if (frame > 5 * 60) {
				m.debugOn = true;
			}
			
			
			if (activeSprites > maxActiveSprites && activeSprites < 120) {
				maxActiveSprites = activeSprites;
			}
			if (frame % 240 == 0) {
				//System.out.println("active sprites=" + activeSprites + " / " + maxActiveSprites + " first=" + firstSprite + " / " + firstSpriteEver);
			}
			
			
			// background tiles - pass 2, some tiles are shown on top of the sprites
			for (int y = 0; y < 16; y++) {
				for (int x = 0; x < 17; x++) { // for scrolling purposes we draw an extra tile column
					int tileX = y;
					int tileY = x;
					int rollOverX = (tileX  + scrollXtile + 1) % (VRAM_TILES_H) ;
					int rollOverY = (tileY  + scrollYtile) % (VRAM_TILES_V) ;

					int memoryPos = bgAddress + (VRAM_TILES_H *  rollOverY);
					int attrPos = memoryPos + 0x400;
					int charIndex = ram[memoryPos + rollOverX] & 0xFF;
					int attr = ram[attrPos + rollOverX] & 0xFF;
					int tileGroup = (attr & 0x08) >> 3;
					
					charIndex +=  ((attr & 0xc0) << 2);
					
					int paltIndex = attr & 0x7; // low 3 bits
					int flipXY = (attr & 0x30) >> 4;

					//System.out.println("Char:" + charIndex + " pal=" + paltIndex);

					//draw one tile (character)
					if (tileGroup != 0) {
						// re-color the tile according to the palette
						setTile(tileData, charIndex * 256, 256 + (paltIndex << 3), flipXY); 
						screen.drawImage(tileImage, 0, 0, 16, 16, (x << 4) - xDrawOffs, (y << 4) - yDrawOffs);
						//screen.drawRect((x << 4) - xDrawOffs, y << 4, 16,16, 0xFF800080);
					}
				}
			}

			// foreground / OSD layer of 8x8 pixel characters
			// This one is in natural screen order in the video memory. 
			for (int y = 0; y < 28; y++) {
				int memoryPos = fgAddress + (32 * (y + 2));
				int attrPos = memoryPos + 0x400;
				for (int x = 0; x < 32; x++) {
					int charIndex = ram[memoryPos + x] & 0xFF;
					int attr = ram[attrPos + x] & 0xFF;
					
					charIndex +=  ((attr & 0xc0) << 2);
										
					int paltIndex = attr & 0xF; // low 4 bits
					int flipXY = (attr & 0x30) >> 4;
					
					paltIndex += 128  / 4 ; 

					//System.out.println("Char:" + charIndex + " pal=" + paltIndex);

					// re-color the tile according to the palette
					setCharacter(charData, charIndex * 64, paltIndex, flipXY); 
					//draw one tile (character)
					screen.drawImage(charImage, 0, 0, 8, 8, (x << 3) , y << 3);
				}
			}

			
			//System.out.println("-------------------");
		}
	}


	private void drawSprite(EmuScreen screen, int sprIndex, int paltIndex,
			int x, int y, boolean flipX, boolean flipY) {
		
			setSprite(spriteData, sprIndex * 256, 64 + (paltIndex * 16), flipX, flipY);
			screen.drawImage(tileImage, 0, 0, 16, 16, x, y);
	}

}
