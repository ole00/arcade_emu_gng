# arcade_emu_gng
Ghosts'n Goblins arcade emulator written in java 

For educational purposes only.

to compile:
<pre>
  cd src
  javac emulator/Main.java
</pre>

to run:
<pre>
  cd src
  java emulator.Main gng.zip
</pre>

On Linux, it is possible to route the FM chip instructions from the emulated Z80 audio handling processor
to two physical YM2203 audio chips to produce authentic arcade sound output of the game. 
To do that use open-source/open-hardware external audio board 'YM Bard 2203' located here:

https://github.com/ole00/ym_bard_2203

and pass the serial port of the sound card as an additional parameter when starting the emulator:
<pre>
  -fm /dev/ttyUSB0
</pre>
or 
<pre>
  -fm /dev/ttyACM0
</pre>
depending on the actual serial port name.

