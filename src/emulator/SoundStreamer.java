/* *************************************************
 * This code is part of the Gberet Emulator.
 * It's free, as long as this whole message is preserved.  
 * Written for educational purposes.
 *************************************************/


package emulator;


import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;

public class SoundStreamer implements Runnable{

	private static final int BUFFER_SAMPLES = 128;
	
	public static interface StreamUpdate {
		void update(int chipId, short[][] buffer, int length);
	};
	
	static SoundStreamer instance; //only one instance ATM.
	
	public static int streamInit(int totalChannels, int[] channelVolumes, int sampleRate, int chipId, StreamUpdate updater) {
		if (instance != null) {
			System.out.println("SoundStreamer already initialised!");
			return -1;
		}
		if (totalChannels < 1 || channelVolumes == null || updater == null) {
			System.out.println("Invalid parameters!");
			return -1;
		}
		instance = new SoundStreamer(totalChannels, channelVolumes, sampleRate, chipId, updater);
		return 0; // stream id 0
	}


	
	private final int totalChannels;
	private int sampleRate;
	private int chipId;
	private StreamUpdate updater;
	private SourceDataLine soundLine;
	private int[] volumes;
	

	private SoundStreamer(int totalChannels, int[] volumes, int sampleRate, int chipId, StreamUpdate updater) {
		this.totalChannels = totalChannels;
		this.volumes = volumes;
		this.sampleRate = sampleRate;
		this.chipId = chipId;
		this.updater = updater;
		
		Thread t = new Thread(this);
		t.setDaemon(true);
		t.start();
	}
	
	private void initAudioOutput() {
		final AudioFormat audioFormat = new AudioFormat(sampleRate, 16, 2, true, true);
        final DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat, BUFFER_SAMPLES << 2);
        try {
        	soundLine = (SourceDataLine) AudioSystem.getLine(info);
        	soundLine.open(audioFormat, BUFFER_SAMPLES << 2); // 16bit samples stereo
        } catch (Exception e) {
        	e.printStackTrace();
        }
	}
	
	public void run() {
		short[][] sndChipBuffer = new short[totalChannels][BUFFER_SAMPLES];
		byte[][] outputBuffer = new byte[2][BUFFER_SAMPLES << 2];
		byte[] inactiveBuffer;

		
		int bufferIndex = 0;
		
		
		initAudioOutput();
		if (soundLine == null) {
			return;
		}
		
		soundLine.start();
		
		while (true) {
			// Updater will generate samples for individual voices (channels) of the
			// sound chip.
			updater.update(chipId, sndChipBuffer, BUFFER_SAMPLES);
			
			// Use the inactive buffer (the one that's not currently played) 
			// to mix the samples into the stereo stream.
			inactiveBuffer = outputBuffer[bufferIndex];
			int index = 0;
			//process the sound chip buffer and mix it to inactive buffer
			for (int i = 0; i < BUFFER_SAMPLES; i++) {
				long sample = 0;
				
				//mix channels into a single sample
				for (int j = 0; j < totalChannels; j++) {
					sample += sndChipBuffer[j][i] * volumes[j];
				}
				sample /= totalChannels;
				final byte low = (byte)(sample & 0xFF);
				final byte high = (byte) ((sample >> 8) & 0xFF);

				//stereo sound buffer
				//Left
				inactiveBuffer[index++] = high;
				inactiveBuffer[index++] = low;
				//Right
				inactiveBuffer[index++] = high;
				inactiveBuffer[index++] = low;

			}
			// switch the playback and inactive buffer
			bufferIndex = 1 - bufferIndex;

			// a blocking call
			soundLine.write(outputBuffer[bufferIndex], 0, BUFFER_SAMPLES << 2);
		}
	}
}
