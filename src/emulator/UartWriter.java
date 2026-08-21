package emulator;

import java.io.OutputStream;
import java.util.LinkedList;

public class UartWriter {

	private final OutputStream os;
	LinkedList<byte[]> queue;
	LinkedList<byte[]> spare;
	LinkedList<byte[]> spareLong;
	boolean threadRunning;
	byte[] longBuf;
			
	public UartWriter(OutputStream os) {
		//this.os = new BufferedOutputStream(os);
		this.os = os;
		queue = new LinkedList<byte[]>();
		spare = new LinkedList<byte[]>();
		spareLong = new LinkedList<byte[]>();
		initSpare();
		final UartWriter instance = this;
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				byte[] snd;
				threadRunning = true;
				long startTime = System.currentTimeMillis();
				int bytesSent = 0;
				while (threadRunning) {
					snd = null;
					synchronized (queue) {
						if (queue.size() > 0) {
							snd = queue.remove(0);
						}
					}
					if (snd != null) {
						try {
							instance.os.write(snd, 0, snd.length);
							instance.os.flush();
							bytesSent += snd.length;
							//System.out.println("writing: " + snd.length);
							if (snd.length == 3) {
								// return back snd to spare 
								synchronized (spare) {
									spare.add(snd);
									//System.out.println("spa size=" + spare.size());
								}
							} else {
								// return back snd to spare 
								synchronized (spareLong) {
									spareLong.add(snd);
									//System.out.println("spa long size=" + spare.size());
								}								
							}
						} catch (Exception e) {
							System.out.println("failed to write uart: " + e.getMessage());
						}
						try {
							Thread.yield();
							//Thread.sleep(1);
						} catch (Exception e) {};
					} else {
						try {
							//Thread.yield();
							Thread.sleep(1);
						} catch (Exception e) {};
					}
					
					long now = System.currentTimeMillis(); 
					if (now - startTime > 1000) {
						//System.out.println("UART Written bytes: " + bytesSent);
						startTime = now;
						bytesSent = 0;
					}
				}
			}
		});
		t.start();
	}
	public void close() {
		try {
			os.flush();
			threadRunning = false;
			os.close();
		} catch (Exception e) {
			
		}
	}
	
	private void initSpare() {
		for (int i = 0; i < 300; i++) {
			byte[] buf = new byte[3];
			spare.add(buf);
		}
		for (int i = 0; i < 100; i++) {
			byte[] buf = new byte[12];
			spareLong.add(buf);
		}
	}
	
	public void write(int device, int addr, int value) {
		byte[] buf = null;
		//System.out.println("UART write: " + spare.size() + " dev=" + device + " addr=" + addr + " val=" + value);
		
		// handle PSG registers that are written sequentially from addr 0 to addr 10 
		if (addr < 11) {
			//System.out.println("spa long write: " + spareLong.size() + " dev=" + device + " addr=" + addr + " val=" + value);
			if (addr == 0) {
				longBuf = null;
				synchronized (spareLong) {
					int size = spareLong.size(); 
					if (size > 1) {
						longBuf = spareLong.remove(spareLong.size() - 1);
					}
				}
			}
			if (longBuf != null) {
				longBuf[1 + addr] = (byte)  (value & 0xFF);
				if (addr == 0) {
					longBuf[0] = (byte)(0xF8 | device); // write header
				} else if (addr == 10) {
					synchronized(queue) {
						queue.add(longBuf); //add to queue to be sent over UART
					}
				}
			} else {
				System.out.println("long buf missing");
			}
		} else
		// handle FM registers
		if (true){
			synchronized (spare) {
				int size = spare.size(); 
				if (size > 1) {
					buf = spare.remove(spare.size() - 1);
				}
			}
			if (buf != null) {
				//System.out.println("Spare OK");
				buf[0] = (byte) (0xF0 | device);
				buf[1] = (byte) (addr & 0xFF);
				buf[2] = (byte) (value & 0xFF);
				
				synchronized(queue) {
					queue.add(buf);
				}
				Thread.yield();
			} else {
				System.out.println("No more spares!");
			}
		}
	}
		

}
