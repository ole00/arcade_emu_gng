/**
 * *************************************************************************
 *
 * ay8910.c
 *
 *
 * Emulation of the AY-3-8910 / YM2149 sound chip.
 *
 * Based on various code snippets by Ville Hallik, Michael Cuddy, Tatsuyuki
 * Satoh, Fabrice Frances, Nicola Salmoria.
 *
 **************************************************************************
 * Borrowed from the arcadeflex036 project.
 */
package emulator.processor.ay8910;

import emulator.SoundStreamer;


public class Ay8910{

	private static final boolean VERBOSE = true;
	
	private final int maxChips; // number of emulated chips
	
    public Ay8910(int ic_count, Ay8910Port portHandler) {
    	maxChips = ic_count;
    	AYPSG = new AY8910[maxChips]; /* array of PSG's */
        for (int i = 0; i < maxChips; i++) {
            AYPSG[i] = new AY8910();
            AYPSG[i].portHandler = portHandler;
        }
    }

    public static final int ALL_8910_CHANNELS = 0xFF;
    public static final int MAX_OUTPUT = 0x7fff;

    public static final int STEP = 0x8000;

    private static class AY8910 {

        int Channel;
        int SampleRate;
        Ay8910Port portHandler;
        int register_latch;
        /*unsigned*/ char Regs[] = new char[16];
        /*unsigned*/ int UpdateStep;
        int PeriodA, PeriodB, PeriodC, PeriodN, PeriodE;
        int CountA, CountB, CountC, CountN, CountE;
        /*unsigned*/ int VolA, VolB, VolC, VolE;
        /*unsigned*/ char EnvelopeA, EnvelopeB, EnvelopeC;
        /*unsigned*/ char OutputA, OutputB, OutputC, OutputN;
        /*signed char*/int CountEnv;
        /*unsigned*/ char Hold, Alternate, Attack, Holding;
        int RNG;
        /*unsigned*/ int VolTable[] = new int[32];
    }

    /* register id's */
    public static final int AY_AFINE = 0;
    public static final int AY_ACOARSE = 1;
    public static final int AY_BFINE = 2;
    public static final int AY_BCOARSE = 3;
    public static final int AY_CFINE = 4;
    public static final int AY_CCOARSE = 5;
    public static final int AY_NOISEPER = 6;
    public static final int AY_ENABLE = 7;
    public static final int AY_AVOL = 8;
    public static final int AY_BVOL = 9;
    public static final int AY_CVOL = 10;
    public static final int AY_EFINE = 11;
    public static final int AY_ECOARSE = 12;
    public static final int AY_ESHAPE = 13;

    public static final int AY_PORTA = 14;
    public static final int AY_PORTB = 15;

    static AY8910[] AYPSG;


    public static void _AYWriteReg(int n, int r, int v) {
        AY8910 PSG = AYPSG[n];
        int old;

        PSG.Regs[r] = (char) v;

        /* A note about the period of tones, noise and envelope: for speed reasons,*/
        /* we count down from the period to 0, but careful studies of the chip     */
        /* output prove that it instead counts up from 0 until the counter becomes */
        /* greater or equal to the period. This is an important difference when the*/
        /* program is rapidly changing the period to modulate the sound.           */
        /* To compensate for the difference, when the period is changed we adjust  */
        /* our internal counter.                                                   */
        /* Also, note that period = 0 is the same as period = 1. This is mentioned */
        /* in the YM2203 data sheets. However, this does NOT apply to the Envelope */
        /* period. In that case, period = 0 is half as period = 1. */
        switch (r) {
            case AY_AFINE:
            case AY_ACOARSE:
                PSG.Regs[AY_ACOARSE] &= 0x0f;
                old = PSG.PeriodA;
                PSG.PeriodA = (int) ((PSG.Regs[AY_AFINE] + 256 * PSG.Regs[AY_ACOARSE]) * PSG.UpdateStep);
                if (PSG.PeriodA == 0) {
                    PSG.PeriodA = (int) PSG.UpdateStep;
                }
                PSG.CountA += PSG.PeriodA - old;
                if (PSG.CountA <= 0) {
                    PSG.CountA = 1;
                }
                break;
            case AY_BFINE:
            case AY_BCOARSE:
                PSG.Regs[AY_BCOARSE] &= 0x0f;
                old = PSG.PeriodB;
                PSG.PeriodB = (int) ((PSG.Regs[AY_BFINE] + 256 * PSG.Regs[AY_BCOARSE]) * PSG.UpdateStep);
                if (PSG.PeriodB == 0) {
                    PSG.PeriodB = (int) PSG.UpdateStep;
                }
                PSG.CountB += PSG.PeriodB - old;
                if (PSG.CountB <= 0) {
                    PSG.CountB = 1;
                }
                break;
            case AY_CFINE:
            case AY_CCOARSE:
                PSG.Regs[AY_CCOARSE] &= 0x0f;
                old = PSG.PeriodC;
                PSG.PeriodC = (int) ((PSG.Regs[AY_CFINE] + 256 * PSG.Regs[AY_CCOARSE]) * PSG.UpdateStep);
                if (PSG.PeriodC == 0) {
                    PSG.PeriodC = (int) PSG.UpdateStep;
                }
                PSG.CountC += PSG.PeriodC - old;
                if (PSG.CountC <= 0) {
                    PSG.CountC = 1;
                }
                break;
            case AY_NOISEPER:
                PSG.Regs[AY_NOISEPER] &= 0x1f;
                old = PSG.PeriodN;
                PSG.PeriodN = (int) (PSG.Regs[AY_NOISEPER] * PSG.UpdateStep);
                if (PSG.PeriodN == 0) {
                    PSG.PeriodN = (int) PSG.UpdateStep;
                }
                PSG.CountN += PSG.PeriodN - old;
                if (PSG.CountN <= 0) {
                    PSG.CountN = 1;
                }
                break;
            case AY_AVOL:
                PSG.Regs[AY_AVOL] &= 0x1f;
                PSG.EnvelopeA = (char) (PSG.Regs[AY_AVOL] & 0x10);
                PSG.VolA = PSG.EnvelopeA != 0 ? PSG.VolE : PSG.VolTable[PSG.Regs[AY_AVOL] != 0 ? PSG.Regs[AY_AVOL] * 2 + 1 : 0];
                break;
            case AY_BVOL:
                PSG.Regs[AY_BVOL] &= 0x1f;
                PSG.EnvelopeB = (char) (PSG.Regs[AY_BVOL] & 0x10);
                PSG.VolB = PSG.EnvelopeB != 0 ? PSG.VolE : PSG.VolTable[PSG.Regs[AY_BVOL] != 0 ? PSG.Regs[AY_BVOL] * 2 + 1 : 0];
                break;
            case AY_CVOL:
                PSG.Regs[AY_CVOL] &= 0x1f;
                PSG.EnvelopeC = (char) (PSG.Regs[AY_CVOL] & 0x10);
                PSG.VolC = PSG.EnvelopeC != 0 ? PSG.VolE : PSG.VolTable[PSG.Regs[AY_CVOL] != 0 ? PSG.Regs[AY_CVOL] * 2 + 1 : 0];
                break;
            case AY_EFINE:
            case AY_ECOARSE:
                old = PSG.PeriodE;
                PSG.PeriodE = (int) (((PSG.Regs[AY_EFINE] + 256 * PSG.Regs[AY_ECOARSE])) * PSG.UpdateStep);
                if (PSG.PeriodE == 0) {
                    PSG.PeriodE = (int) (PSG.UpdateStep / 2);
                }
                PSG.CountE += PSG.PeriodE - old;
                if (PSG.CountE <= 0) {
                    PSG.CountE = 1;
                }
                break;
            case AY_ESHAPE:
                /* envelope shapes:
                 C AtAlH
                 0 0 x x  \___
    
                 0 1 x x  /___
    
                 1 0 0 0  \\\\
    
                 1 0 0 1  \___
    
                 1 0 1 0  \/\/
                 ___
                 1 0 1 1  \
    
                 1 1 0 0  ////
                 ___
                 1 1 0 1  /
    
                 1 1 1 0  /\/\
    
                 1 1 1 1  /___
    
                 The envelope counter on the AY-3-8910 has 16 steps. On the YM2149 it
                 has twice the steps, happening twice as fast. Since the end result is
                 just a smoother curve, we always use the YM2149 behaviour.
                 */
                PSG.Regs[AY_ESHAPE] &= 0x0f;
                PSG.Attack = (PSG.Regs[AY_ESHAPE] & 0x04) != 0 ? (char) 0x1f : (char) 0x00;
                if ((PSG.Regs[AY_ESHAPE] & 0x08) == 0) {
                    /* if Continue = 0, map the shape to the equivalent one which has Continue = 1 */
                    PSG.Hold = 1;
                    PSG.Alternate = PSG.Attack;
                } else {
                    PSG.Hold = (char) (PSG.Regs[AY_ESHAPE] & 0x01);
                    PSG.Alternate = (char) (PSG.Regs[AY_ESHAPE] & 0x02);
                }
                PSG.CountE = PSG.PeriodE;
                PSG.CountEnv = 0x1f;
                PSG.Holding = 0;
                PSG.VolE = PSG.VolTable[PSG.CountEnv ^ PSG.Attack];
                if (PSG.EnvelopeA != 0) {
                    PSG.VolA = PSG.VolE;
                }
                if (PSG.EnvelopeB != 0) {
                    PSG.VolB = PSG.VolE;
                }
                if (PSG.EnvelopeC != 0) {
                    PSG.VolC = PSG.VolE;
                }
                break;
            case AY_PORTA:
                if ((PSG.Regs[AY_ENABLE] & 0x40) == 0) {
                    if (VERBOSE) {
                        System.out.println("warning: write to 8910 #" + n +" Port A set as input\n");
                    }
                }
                if (PSG.portHandler != null) {
                    PSG.portHandler.writePortA(n, v);
                } else if (VERBOSE) {
                    System.out.println("PC " + cpu_get_pc() + ": warning - write " + v + " to 8910 #" + n + " Port A\n");
                }
                break;
            case AY_PORTB:
                if ((PSG.Regs[AY_ENABLE] & 0x80) == 0) {
                    if (VERBOSE) {
                        System.out.println("warning: write to 8910 #" + n + " Port B set as input\n");
                    }
                }
                if (PSG.portHandler != null) {
                    PSG.portHandler.writePortB(n, v);
                } else if (VERBOSE) {
                	System.out.println("PC " + cpu_get_pc() + ": warning - write " + v + " to 8910 #" + n + " Port B\n");
                }
                break;
        }
    }

    /* write a register on AY8910 chip number 'n' */
    private static void writeReg(int chip, int r, int v) {
        AY8910 PSG = AYPSG[chip];

        if (r > 15) {
            return;
        }
        if (r < 14) {
            if (r == AY_ESHAPE || PSG.Regs[r] != v) {
                /* update the output buffer before changing the register */
            	//TODO
                //SoundStreamer.update(PSG.Channel, 0);
            }
        }

        _AYWriteReg(chip, r, v);
    }

    static int cpu_get_pc() {
    	return 0;
    }
    
    private static char readReg(int n, int r) {
        AY8910 PSG = AYPSG[n];

        if (r > 15) {
            return 0;
        }

        switch (r) {
            case AY_PORTA:
                if ((PSG.Regs[AY_ENABLE] & 0x40) != 0) {
                    if (VERBOSE) {
                        System.out.println( "warning: read from 8910 #" + n + " Port A set as output\n");
                    }
                }
                if (PSG.portHandler != null) {
                    PSG.Regs[AY_PORTA] = (char) (PSG.portHandler.readPortA(n) & 0xFF);//(*PSG->PortAread)(0);
                } else if (VERBOSE) {
                    System.out.println( "PC " +  cpu_get_pc() + ": warning - read 8910 #" + n + " Port A\n");
                }
                break;
            case AY_PORTB:
                if ((PSG.Regs[AY_ENABLE] & 0x80) != 0) {
                    if (VERBOSE) {
                        System.out.println( "warning: read from 8910 #" +n +" Port B set as output\n");
                    }
                }
                if (PSG.portHandler != null) {
                    PSG.Regs[AY_PORTB] = (char) (PSG.portHandler.readPortB(n) & 0xFF);
                } else if (VERBOSE) {
                    System.out.println( "PC " + cpu_get_pc() + ": warning - read 8910 #" +n +" Port B\n");
                }
                break;
        }
        return (char) (PSG.Regs[r] & 0xFF);
    }

    public static void write(int chip, int a, int data) {
        AY8910 PSG = AYPSG[chip];

        if ((a & 1) != 0) {	/* Data port */

            writeReg(chip, PSG.register_latch, data);
        } else {	/* Register port */

            PSG.register_latch = data & 0x0f;
        }
    }

    public static int read(int chip) {
        AY8910 PSG = AYPSG[chip];

        return readReg(chip, PSG.register_latch);
    }


    public static void update (int chip, short[][] buffer, int length) {
            AY8910 PSG = AYPSG[chip];
            short[] buf1, buf2, buf3;
            int outn;

            int bufOffset = 0;
            buf1 = buffer[0];
            buf2 = buffer[1];
            buf3 = buffer[2];

            /* The 8910 has three outputs, each output is the mix of one of the three */
            /* tone generators and of the (single) noise generator. The two are mixed */
            /* BEFORE going into the DAC. The formula to mix each channel is: */
            /* (ToneOn | ToneDisable) & (NoiseOn | NoiseDisable). */
            /* Note that this means that if both tone and noise are disabled, the output */
            /* is 1, not 0, and can be modulated changing the volume. */
            /* If the channels are disabled, set their output to 1, and increase the */
            /* counter, if necessary, so they will not be inverted during this update. */
            /* Setting the output to 1 is necessary because a disabled channel is locked */
            /* into the ON state (see above); and it has no effect if the volume is 0. */
            /* If the volume is 0, increase the counter, but don't touch the output. */
            if ((PSG.Regs[AY_ENABLE] & 0x01) != 0) {
                if (PSG.CountA <= length * STEP) {
                    PSG.CountA += length * STEP;
                }
                PSG.OutputA = 1;
            } else if (PSG.Regs[AY_AVOL] == 0) {
                /* note that I do count += length, NOT count = length + 1. You might think */
                /* it's the same since the volume is 0, but doing the latter could cause */
                /* interferencies when the program is rapidly modulating the volume. */
                if (PSG.CountA <= length * STEP) {
                    PSG.CountA += length * STEP;
                }
            }
            if ((PSG.Regs[AY_ENABLE] & 0x02) != 0) {
                if (PSG.CountB <= length * STEP) {
                    PSG.CountB += length * STEP;
                }
                PSG.OutputB = 1;
            } else if (PSG.Regs[AY_BVOL] == 0) {
                if (PSG.CountB <= length * STEP) {
                    PSG.CountB += length * STEP;
                }
            }
            if ((PSG.Regs[AY_ENABLE] & 0x04) != 0) {
                if (PSG.CountC <= length * STEP) {
                    PSG.CountC += length * STEP;
                }
                PSG.OutputC = 1;
            } else if (PSG.Regs[AY_CVOL] == 0) {
                if (PSG.CountC <= length * STEP) {
                    PSG.CountC += length * STEP;
                }
            }

            /* for the noise channel we must not touch OutputN - it's also not necessary */
            /* since we use outn. */
            if ((PSG.Regs[AY_ENABLE] & 0x38) == 0x38) /* all off */ {
                if (PSG.CountN <= length * STEP) {
                    PSG.CountN += length * STEP;
                }
            }

            outn = (PSG.OutputN | PSG.Regs[AY_ENABLE]);
            /* buffering loop */
            while (length != 0) {
                int vola, volb, volc;
                int left;


                /* vola, volb and volc keep track of how long each square wave stays */
                /* in the 1 position during the sample period. */
                vola = volb = volc = 0;

                left = STEP;
                do {
                    int nextevent;

                    if (PSG.CountN < left) {
                        nextevent = PSG.CountN;
                    } else {
                        nextevent = left;
                    }
                    if ((outn & 0x08) != 0) {
                        if (PSG.OutputA != 0) {
                            vola += PSG.CountA;
                        }
                        PSG.CountA -= nextevent;
                        /* PeriodA is the half period of the square wave. Here, in each */
                        /* loop I add PeriodA twice, so that at the end of the loop the */
                        /* square wave is in the same status (0 or 1) it was at the start. */
                        /* vola is also incremented by PeriodA, since the wave has been 1 */
                        /* exactly half of the time, regardless of the initial position. */
                        /* If we exit the loop in the middle, OutputA has to be inverted */
                        /* and vola incremented only if the exit status of the square */
                        /* wave is 1. */
                        while (PSG.CountA <= 0) {
                            PSG.CountA += PSG.PeriodA;
                            if (PSG.CountA > 0) {
                                PSG.OutputA ^= 1;
                                if (PSG.OutputA != 0) {
                                    vola += PSG.PeriodA;
                                }
                                break;
                            }
                            PSG.CountA += PSG.PeriodA;
                            vola += PSG.PeriodA;
                        }
                        if (PSG.OutputA != 0) {
                            vola -= PSG.CountA;
                        }
                    } else {
                        PSG.CountA -= nextevent;
                        while (PSG.CountA <= 0) {
                            PSG.CountA += PSG.PeriodA;
                            if (PSG.CountA > 0) {
                                PSG.OutputA ^= 1;
                                break;
                            }
                            PSG.CountA += PSG.PeriodA;
                        }
                    }
                    if ((outn & 0x10) != 0) {
                        if (PSG.OutputB != 0) {
                            volb += PSG.CountB;
                        }
                        PSG.CountB -= nextevent;
                        while (PSG.CountB <= 0) {
                            PSG.CountB += PSG.PeriodB;
                            if (PSG.CountB > 0) {
                                PSG.OutputB ^= 1;
                                if (PSG.OutputB != 0) {
                                    volb += PSG.PeriodB;
                                }
                                break;
                            }
                            PSG.CountB += PSG.PeriodB;
                            volb += PSG.PeriodB;
                        }
                        if (PSG.OutputB != 0) {
                            volb -= PSG.CountB;
                        }
                    } else {
                        PSG.CountB -= nextevent;
                        while (PSG.CountB <= 0) {
                            PSG.CountB += PSG.PeriodB;
                            if (PSG.CountB > 0) {
                                PSG.OutputB ^= 1;
                                break;
                            }
                            PSG.CountB += PSG.PeriodB;
                        }
                    }
                    if ((outn & 0x20) != 0) {
                        if (PSG.OutputC != 0) {
                            volc += PSG.CountC;
                        }
                        PSG.CountC -= nextevent;
                        while (PSG.CountC <= 0) {
                            PSG.CountC += PSG.PeriodC;
                            if (PSG.CountC > 0) {
                                PSG.OutputC ^= 1;
                                if (PSG.OutputC != 0) {
                                    volc += PSG.PeriodC;
                                }
                                break;
                            }
                            PSG.CountC += PSG.PeriodC;
                            volc += PSG.PeriodC;
                        }
                        if (PSG.OutputC != 0) {
                            volc -= PSG.CountC;
                        }
                    } else {
                        PSG.CountC -= nextevent;
                        while (PSG.CountC <= 0) {
                            PSG.CountC += PSG.PeriodC;
                            if (PSG.CountC > 0) {
                                PSG.OutputC ^= 1;
                                break;
                            }
                            PSG.CountC += PSG.PeriodC;
                        }
                    }
                    PSG.CountN -= nextevent;
                    if (PSG.CountN <= 0) {
                        /* Is noise output going to change? */
                        if (((PSG.RNG + 1) & 2) != 0) /* (bit0^bit1)? */ {
                            PSG.OutputN = (char) (~PSG.OutputN);
                            outn = (PSG.OutputN | PSG.Regs[AY_ENABLE]);
                        }

                        /* The Random Number Generator of the 8910 is a 17-bit shift */
                        /* register. The input to the shift register is bit0 XOR bit2 */
                        /* (bit0 is the output). */

                        /* The following is a fast way to compute bit 17 = bit0^bit2. */
                        /* Instead of doing all the logic operations, we only check */
                        /* bit 0, relying on the fact that after two shifts of the */
                        /* register, what now is bit 2 will become bit 0, and will */
                        /* invert, if necessary, bit 16, which previously was bit 18. */
                        if ((PSG.RNG & 1) != 0) {
                            PSG.RNG ^= 0x28000;
                        }
                        PSG.RNG >>= 1;
                        PSG.CountN += PSG.PeriodN;
                    }

                    left -= nextevent;
                } while (left > 0);
                /* update envelope */
                if (PSG.Holding == 0) {
                    PSG.CountE -= STEP;
                    if (PSG.CountE <= 0) {
                        do {
                            PSG.CountEnv--;
                            PSG.CountE += PSG.PeriodE;
                        } while (PSG.CountE <= 0);

                        /* check envelope current position */
                        if (PSG.CountEnv < 0) {
                            if (PSG.Hold != 0) {
                                if (PSG.Alternate != 0) {
                                    PSG.Attack ^= 0x1f;
                                }
                                PSG.Holding = 1;
                                PSG.CountEnv = 0;
                            } else {
                                /* if CountEnv has looped an odd number of times (usually 1), */
                                /* invert the output. */
                                if (PSG.Alternate != 0 && (PSG.CountEnv & 0x20) != 0) {
                                    PSG.Attack ^= 0x1f;
                                }

                                PSG.CountEnv &= 0x1f;
                            }
                        }
                        PSG.VolE = PSG.VolTable[PSG.CountEnv ^ PSG.Attack];
                        /* reload volume */
                        if (PSG.EnvelopeA != 0) {
                            PSG.VolA = PSG.VolE;
                        }
                        if (PSG.EnvelopeB != 0) {
                            PSG.VolB = PSG.VolE;
                        }
                        if (PSG.EnvelopeC != 0) {
                            PSG.VolC = PSG.VolE;
                        }
                    }
                }

                buf1[bufOffset] = (short) ((vola * PSG.VolA) / STEP);
                buf2[bufOffset] = (short) ((volb * PSG.VolB) / STEP);
                buf3[bufOffset] = (short) ((volc * PSG.VolC) / STEP);
                bufOffset++;
                length--;
            }
         //System.out.println("Buff offset=" + bufOffset);
    };

    private static void setClock(int chip, int clock) {
        AY8910 PSG = AYPSG[chip];

        /* the step clock for the tone and noise generators is the chip clock    */
        /* divided by 8; for the envelope generator of the AY-3-8910, it is half */
        /* that much (clock/16), but the envelope of the YM2149 goes twice as    */
        /* fast, therefore again clock/8.                                        */
        /* Here we calculate the number of steps which happen during one sample  */
        /* at the given sample rate. No. of events = sample rate / (clock/8).    */
        /* STEP is a multiplier used to turn the fraction into a fixed point     */
        /* number.                                                               */
        PSG.UpdateStep = (int) (((double) STEP * PSG.SampleRate * 8) / clock);
    }

    static void AY8910_set_volume(int chip, int channel, int volume) {
    	//TODO - implement mixer
        /*
        AY8910 PSG = AYPSG[chip];

        for (int ch = 0; ch < 3; ch++) {
            if (channel == ch || channel == ALL_8910_CHANNELS) {
                mixer_set_volume(PSG.Channel + ch, volume);
            }
        }
        */
    }

    public static void build_mixer_table(int chip) {
        AY8910 PSG = AYPSG[chip];
        int i;
        double out;


        /* calculate the volume.voltage conversion table */
        /* The AY-3-8910 has 16 levels, in a logarithmic scale (3dB per step) */
        /* The YM2149 still has 16 levels for the tone generators, but 32 for */
        /* the envelope generator (1.5dB per step). */
        out = MAX_OUTPUT;
        for (i = 31; i > 0; i--) {
            PSG.VolTable[i] = (int) (out + 0.5);	/* round to nearest */

            out /= 1.188502227;	/* = 10 ^ (1.5/20) = 1.5dB */

        }
        PSG.VolTable[0] = 0;
    }

    public static void reset(int chip) {
        AY8910 PSG = AYPSG[chip];

        PSG.register_latch = 0;
        PSG.RNG = 1;
        PSG.OutputA = 0;
        PSG.OutputB = 0;
        PSG.OutputC = 0;
        PSG.OutputN = 0xff;
        for (int i = 0; i < AY_PORTA; i++) {
            _AYWriteReg(chip, i, 0);
        }
    }

    static int init(int chip, int clock, int volume, int sample_rate ) {

        AY8910 PSG = AYPSG[chip];
        int[] vol = new int[3];
        PSG.SampleRate = sample_rate;
 
        for (int i = 0; i < 3; i++) {
            vol[i] = volume;
        }

        if (PSG.Channel == -1) {
            return 1;
        }

        setClock(chip, clock);
        reset(chip);
        return 0;
    }

	public int start(int baseClock, int[] mixingLevel, int sampleRate) {
        int chip;

        for (chip = 0; chip < maxChips; chip++) {
            if (init(chip, baseClock, mixingLevel[chip] & 0xffff, sampleRate) != 0) {
                return 1;
            }
            build_mixer_table(chip);
        }
        return 0;
    }
}
