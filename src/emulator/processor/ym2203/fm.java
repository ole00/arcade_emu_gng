/**
 * Taken from Arcadeflex emulator, based on mame 0.36 and 0.37b7
 *
 */
package emulator.processor.ym2203;

import emulator.processor.ym2203.PtrLib.ShortPtr;
import emulator.processor.ym2203.SubArrays.IntSubArray;


public class fm {

	// 8 or 16 bits
	public static final int FM_OUTPUT_BIT = 16;
	
	public static abstract interface FmTimerHandler {
		public abstract void fmTimerHandler(int n, int c, double count, double stepTime);
	}
	public static abstract interface FmIrqHandler {
		public abstract void fmIrqHandler(int n, int irq);
	}
	public static abstract interface FmSsgHandler {
		public abstract void SsgWrite(int n, int addr, int val);
		public abstract int  SsgRead(int n);
		public abstract void SsgReset(int n);
		public abstract void SsgClk(int n, int clk);
	}
	
    /* -------------------- sound quality define selection --------------------- */
 /* sinwave entries */
 /* used static memory = SIN_ENT * 4 (byte) */
    public static final int SIN_ENT = 2048;
    /* lower bits of envelope counter */
    public static final int ENV_BITS = 16;

    /* envelope output entries */
    public static final int EG_ENT = 4096;
    public static final double EG_STEP = (96.0 / EG_ENT);
    /* OPL == 0.1875 dB */

    /* LFO table entries */
    public static final int LFO_ENT = 512;
    public static final int LFO_SHIFT = (32 - 9);
    public static final int LFO_RATE = 0x10000;

    /* -------------------- preliminary define section --------------------- */
 /* attack/decay rate time rate */
    public static final int OPM_ARRATE = 399128;
    public static final int OPM_DRRATE = 5514396;
    /* It is not checked , because I haven't YM2203 rate */
    public static final int OPN_ARRATE = OPM_ARRATE;
    public static final int OPN_DRRATE = OPM_DRRATE;

    /* PG output cut off level : 78dB(14bit)? */
    public static final int PG_CUT_OFF = ((int) (78.0 / EG_STEP));
    /* EG output cut off level : 68dB? */
    public static final int EG_CUT_OFF = ((int) (68.0 / EG_STEP));

    public static final int FREQ_BITS = 24;
    /* frequency turn          */

 /* PG counter is 21bits @oct.7 */
    public static final int FREQ_RATE = (1 << (FREQ_BITS - 21));
    public static final int TL_BITS = (FREQ_BITS + 2);
    /* OPbit = 14(13+sign) : TL_BITS+1(sign) / output = 16bit */
    public static final int TL_SHIFT = (TL_BITS + 1 - (14 - 16));

    /* output final shift */
    public static final int FM_OUTSB = (TL_SHIFT - FM_OUTPUT_BIT);
    public static final int FM_MAXOUT = ((1 << (TL_SHIFT - 1)) - 1);
    public static final int FM_MINOUT = (-(1 << (TL_SHIFT - 1)));
    /* -------------------- local defines , macros --------------------- */
 /* envelope counter position */
    public static final int EG_AST = 0;/* start of Attack phase */
    public static final int EG_AED = (EG_ENT << ENV_BITS);/* end   of Attack phase */
    public static final int EG_DST = EG_AED;/* start of Decay/Sustain/Release phase */
    public static final int EG_DED = (EG_DST + (EG_ENT << ENV_BITS) - 1);/* end   of Decay/Sustain/Release phase */
    public static final int EG_OFF = EG_DED;/* off */


    /* register number to channel number , slot offset */
    static int OPN_CHAN(int n) {
        return n & 3;
    }

    static int OPN_SLOT(int n) {
        return (n >> 2) & 3;
    }

    static int OPM_CHAN(int n) {
        return n & 7;
    }

    static int OPM_SLOT(int n) {
        return (n >> 3) & 3;
    }
    /* slot number */
    public static final int SLOT1 = 0;
    public static final int SLOT2 = 2;
    public static final int SLOT3 = 1;
    public static final int SLOT4 = 3;

    /* bit0 = Right enable , bit1 = Left enable */
    public static final int OUTD_RIGHT = 1;
    public static final int OUTD_LEFT = 2;
    public static final int OUTD_CENTER = 3;

    /* FM timer model */
    public static final int FM_TIMER_SINGLE = 0;
    public static final int FM_TIMER_INTERVAL = 1;

    /* ---------- OPN / OPM one channel  ---------- */
    public static class FM_SLOT {

        public int[] DT;/* detune          :DT_TABLE[DT]       */
        public int DT2;/* multiple,Detune2:(DT2<<4)|ML for OPM*/
        public int TL;/* total level     :TL << 8            */
        public int /*UINT8*/ KSR;/* key scale rate  :3-KSR              */
        public IntSubArray AR;/* attack rate     :&AR_TABLE[AR<<1]   */
        public IntSubArray DR;/* decay rate      :&DR_TABLE[DR<<1]   */
        public IntSubArray SR;/* sustin rate     :&DR_TABLE[SR<<1]   */
        public int SL;/* sustin level    :SL_TABLE[SL]       */
        public IntSubArray RR;/* release rate    :&DR_TABLE[RR<<2+2] */
        public int /*UINT8*/ SEG;/* SSG EG type     :SSGEG              */
        public int /*UINT8*/ ksr;/* key scale rate  :kcode>>(3-KSR)     */
        public long /*UINT32*/ mul;/* multiple        :ML_TABLE[ML]       */
 /* Phase Generator */
        public long /*UINT32*/ Cnt;/* frequency count :                   */
        public long /*UINT32*/ Incr;/* frequency step  :                   */
 /* Envelope Generator */
        public EGPtr eg_next;
        /* pointer of phase handler */
        public int evc;/* envelope counter                    */
        public int eve;/* envelope counter end point          */
        public int evs;/* envelope counter step               */
        public int evsa;/* envelope step for Attack            */
        public int evsd;/* envelope step for Decay             */
        public int evss;/* envelope step for Sustain           */
        public int evsr;/* envelope step for Release           */
        public int TLL;/* adjusted TotalLevel                 */
    }

    public static class FM_CH {

        public FM_CH() {
            SLOT = new FM_SLOT[4];
            for (int i = 0; i < 4; i++) {
                SLOT[i] = new FM_SLOT();
            }
            op1_out = new int[2];
        }
        public FM_SLOT[] SLOT;
        public int /*UINT8*/ PAN;/* PAN :NONE,LEFT,RIGHT or CENTER */
        public int /*UINT8*/ ALGO;/* Algorythm                      */
        public int /*UINT8*/ FB;/* shift count of self feed back  */
        public int[] op1_out;/* op1 output for beedback        */
 /* Algorythm (connection) */
        public IntSubArray connect1;/* pointer of SLOT1 output    */
        public IntSubArray connect2;/* pointer of SLOT2 output    */
        public IntSubArray connect3;/* pointer of SLOT3 output    */
        public IntSubArray connect4;/* pointer of SLOT4 output    */

        /* Phase Generator */
        public long /*UINT32*/ fc;/* fnum,blk    :adjusted to sampling rate */
        public int /*UINT8*/ fn_h;/* freq latch  :                   */
        public int /*UINT8*/ kcode;/* key code    :                   */
    }

    /* OPN/OPM common state */
    public static class FM_ST {

        public FM_ST() {
            DT_TABLE = new int[8][];
            for (int i = 0; i < 8; i++) {
                DT_TABLE[i] = new int[32];
            }
            AR_TABLE = new IntSubArray(94);
            DR_TABLE = new IntSubArray(94);

        }

        public int /*UINT8*/ index;/* chip index (number of chip) */
        public int clock;/* master clock  (Hz)  */
        public int rate;/* sampling rate (Hz)  */
        public double freqbase;/* frequency base      */
        public double TimerBase;/* Timer base time     */
        public int /*UINT8*/ address;/* address register    */
        public int /*UINT8*/ irq;/* interrupt level     */
        public int /*UINT8*/ irqmask;/* irq mask            */
        public int /*UINT8*/ status;/* status flag         */
        public long /*UINT32*/ mode;/* mode  CSM / 3SLOT   */
        public int TA;/* timer a             */
        public int TAC;/* timer a counter     */
        public int /*UINT8*/ TB;/* timer b             */
        public int TBC;/* timer b counter     */
 /* speedup customize */
 /* local time tables */
        public int[][] DT_TABLE;/* DeTune tables       */
        public IntSubArray AR_TABLE;/* Atttack rate tables */
        public IntSubArray DR_TABLE;/* Decay rate tables   */
 /* Extention Timer and IRQ handler */
        public FmTimerHandler Timer_Handler;
        public FmIrqHandler IRQ_Handler;
        public FmSsgHandler SsgHandler;
        /* timer model single / interval */
        public int /*UINT8*/ timermodel;
    }

    /* -------------------- tables --------------------- */
 /* sustain lebel table (3db per step) */
 /* 0 - 15: 0, 3, 6, 9,12,15,18,21,24,27,30,33,36,39,42,93 (dB)*/
    static int SC(int db) {
        return (int) ((db * ((3 / EG_STEP) * (1 << ENV_BITS))) + EG_DST);
    }

    static int[] SL_TABLE = {
        SC(0), SC(1), SC(2), SC(3), SC(4), SC(5), SC(6), SC(7),
        SC(8), SC(9), SC(10), SC(11), SC(12), SC(13), SC(14), SC(31)
    };


    /* size of TL_TABLE = sinwave(max cut_off) + cut_off(tl + ksr + envelope + ams) */
    public static final int TL_MAX = (PG_CUT_OFF + EG_CUT_OFF + 1);

    /* TotalLevel : 48 24 12  6  3 1.5 0.75 (dB) */
 /* TL_TABLE[ 0      to TL_MAX          ] : plus  section */
 /* TL_TABLE[ TL_MAX to TL_MAX+TL_MAX-1 ] : minus section */
    static int[] TL_TABLE;

    /* pointers to TL_TABLE with sinwave output offset */
    static IntSubArray[] SIN_TABLE = new IntSubArray[SIN_ENT];

    /* envelope output curve table */
 /* attack + decay + OFF */
    public static int[] ENV_CURVE = new int[2 * EG_ENT + 1];

    /* envelope counter conversion table when change Decay to Attack phase */
    public static int[] DRAR_TABLE = new int[EG_ENT];

    static int OPN_DTTABLE[] = {
        /* this table is YM2151 and YM2612 data */
        /* FD=0 */
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        /* FD=1 */
        0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
        2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,
        /* FD=2 */
        1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
        5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,
        /* FD=3 */
        2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
        8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
    };
    static int OPM_DTTABLE[] = {
        /* this table is YM2151 and YM2612 data */
        /* FD=0 */
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        /* FD=1 */
        0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2,
        2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7, 8, 8, 8, 8,
        /* FD=2 */
        1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5,
        5, 6, 6, 7, 8, 8, 9, 10, 11, 12, 13, 14, 16, 16, 16, 16,
        /* FD=3 */
        2, 2, 2, 2, 2, 3, 3, 3, 4, 4, 4, 5, 5, 6, 6, 7,
        8, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 20, 22, 22, 22, 22
    };

    /* multiple table */
    static int ML(double n) {
        return (int) (n * 2);
    }

    static int[] MUL_TABLE = {
        /* 1/2, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,15 */
        ML(0.50), ML(1.00), ML(2.00), ML(3.00), ML(4.00), ML(5.00), ML(6.00), ML(7.00),
        ML(8.00), ML(9.00), ML(10.00), ML(11.00), ML(12.00), ML(13.00), ML(14.00), ML(15.00),
        /* DT2=1 *SQL(2)   */
        ML(0.71), ML(1.41), ML(2.82), ML(4.24), ML(5.65), ML(7.07), ML(8.46), ML(9.89),
        ML(11.30), ML(12.72), ML(14.10), ML(15.55), ML(16.96), ML(18.37), ML(19.78), ML(21.20),
        /* DT2=2 *SQL(2.5) */
        ML(0.78), ML(1.57), ML(3.14), ML(4.71), ML(6.28), ML(7.85), ML(9.42), ML(10.99),
        ML(12.56), ML(14.13), ML(15.70), ML(17.27), ML(18.84), ML(20.41), ML(21.98), ML(23.55),
        /* DT2=3 *SQL(3)   */
        ML(0.87), ML(1.73), ML(3.46), ML(5.19), ML(6.92), ML(8.65), ML(10.38), ML(12.11),
        ML(13.84), ML(15.57), ML(17.30), ML(19.03), ML(20.76), ML(22.49), ML(24.22), ML(25.95)
    };


    /* Dummy table of Attack / Decay rate ( use when rate == 0 ) */
    static int RATE_0[]
            = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    /* -------------------- state --------------------- */

 /* some globals */
    public static final int TYPE_SSG = 0x01;/* SSG support          */
 /*TODO*///#define TYPE_OPN    0x02    /* OPN device           */
    public static final int TYPE_LFOPAN = 0x04;/* OPN type LFO and PAN */
    public static final int TYPE_6CH = 0x08;/* FM 6CH / 3CH         */
    public static final int TYPE_DAC = 0x10;/* YM2612's DAC device  */
    public static final int TYPE_ADPCM = 0x20;/* two ADPCM unit       */

    public static final int TYPE_YM2203 = (TYPE_SSG);
    public static final int TYPE_YM2608 = (TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM);
    public static final int TYPE_YM2610 = (TYPE_SSG | TYPE_LFOPAN | TYPE_6CH | TYPE_ADPCM);
    public static final int TYPE_YM2612 = (TYPE_6CH |TYPE_LFOPAN |TYPE_DAC);

    /* current chip state */
    static Object cur_chip = null;/* pointer of current chip struct */
    static FM_ST State;/* basic status */
    static FM_CH[] cch = new FM_CH[8];/* pointer of FM channels */

    /* runtime work */
    static int[] out_ch = new int[4];/* channel output NONE,LEFT,RIGHT or CENTER */
    static int[] pg_in1 = new int[1], pg_in2 = new int[1], pg_in3 = new int[1], pg_in4 = new int[1];


    /* external timer mode */
    static void INTERNAL_TIMER_A(FM_ST ST, FM_CH CSM_CH) {
    }

    static void INTERNAL_TIMER_B(FM_ST ST, int step) {
    }

    /* --------------------- subroutines  --------------------- */
 /* status set and IRQ handling */
    static void FM_STATUS_SET(FM_ST ST, int flag) {
        /* set status flag */
        ST.status |= flag;
        if (((ST.irq) == 0) && ((ST.status & ST.irqmask) != 0)) {
            ST.irq = 1;
            /* callback user interrupt handler (IRQ is OFF to ON) */
            if (ST.IRQ_Handler != null) {
                (ST.IRQ_Handler).fmIrqHandler(ST.index, 1);
            }
        }
    }

    /* status reset and IRQ handling */
    static void FM_STATUS_RESET(FM_ST ST, int flag) {
        /* reset status flag */
        ST.status &= ~flag;
        if (((ST.irq) != 0) && ((ST.status & ST.irqmask) == 0)) {
            ST.irq = 0;
            /* callback user interrupt handler (IRQ is ON to OFF) */
            if (ST.IRQ_Handler != null) {
                ST.IRQ_Handler.fmIrqHandler(ST.index, 0);
            }
        }
    }

    /* IRQ mask set */
    static void FM_IRQMASK_SET(FM_ST ST, int flag) {
        ST.irqmask = flag;
        /* IRQ handling check */
        FM_STATUS_SET(ST, 0);
        FM_STATUS_RESET(ST, 0);
    }

    /* ---------- event hander of Phase Generator ---------- */
    public static interface EGPtr {
        public void handler(FM_SLOT SLOT);
    }

    /* Release end -> stop counter */
    public static EGPtr FM_EG_Release = new EGPtr() {
        public void handler(FM_SLOT SLOT) {
            SLOT.evc = EG_OFF;
            SLOT.eve = EG_OFF + 1;
            SLOT.evs = 0;
        }
    };
    /* SUSTAIN end -> stop counter */
    public static EGPtr FM_EG_SR = new EGPtr() {
        public void handler(FM_SLOT SLOT) {
            SLOT.evs = 0;
            SLOT.evc = EG_OFF;
            SLOT.eve = EG_OFF + 1;
        }
    };
    /* Decay end -> Sustain */
    public static EGPtr FM_EG_DR = new EGPtr() {
        public void handler(FM_SLOT SLOT) {
            SLOT.eg_next = FM_EG_SR;
            SLOT.evc = SLOT.SL;
            SLOT.eve = EG_DED;
            SLOT.evs = SLOT.evss;

        }
    };
    /* Attack end -> Decay */
    public static EGPtr FM_EG_AR = new EGPtr() {
        public void handler(FM_SLOT SLOT) {
            /* next DR */
            SLOT.eg_next = FM_EG_DR;
            SLOT.evc = EG_DST;
            SLOT.eve = SLOT.SL;
            SLOT.evs = SLOT.evsd;

        }
    };


    /* ----- key on of SLOT ----- */
    static boolean FM_KEY_IS(FM_SLOT SLOT) {
        return (SLOT.eg_next != FM_EG_Release);
    }

    static void FM_KEYON(FM_CH CH, int s) {
        FM_SLOT SLOT = CH.SLOT[s];
        if (!FM_KEY_IS(SLOT)) {
            /* restart Phage Generator */
            SLOT.Cnt = 0;
            /* phase -> Attack */

            SLOT.eg_next = FM_EG_AR;
            SLOT.evs = SLOT.evsa;
            /* reset attack counter */
            SLOT.evc = EG_AST;
            SLOT.eve = EG_AED;
        }
    }

    /* ----- key off of SLOT ----- */
    static void FM_KEYOFF(FM_CH CH, int s) {
        FM_SLOT SLOT = CH.SLOT[s];
        if (FM_KEY_IS(SLOT)) {
            /* if Attack phase then adjust envelope counter */
            if (SLOT.evc < EG_DST) {
                SLOT.evc = (ENV_CURVE[SLOT.evc >> ENV_BITS] << ENV_BITS) + EG_DST;
            }
            /* phase -> Release */
            SLOT.eg_next = FM_EG_Release;
            SLOT.eve = EG_DED;
            SLOT.evs = SLOT.evsr;
        }
    }

    /* setup Algorythm and PAN connection */
    static void setup_connection(FM_CH CH) {
        IntSubArray carrier = new IntSubArray(out_ch, CH.PAN);
        /* NONE,LEFT,RIGHT or CENTER */

        switch (CH.ALGO) {
            case 0:
                /*  PG---S1---S2---S3---S4---OUT */
                CH.connect1 = new IntSubArray(pg_in2);
                CH.connect2 = new IntSubArray(pg_in3);
                CH.connect3 = new IntSubArray(pg_in4);
                break;
            case 1:
                /*  PG---S1-+-S3---S4---OUT */
 /*  PG---S2-+               */
                CH.connect1 = new IntSubArray(pg_in3);
                CH.connect2 = new IntSubArray(pg_in3);
                CH.connect3 = new IntSubArray(pg_in4);
                break;
            case 2:
                /* PG---S1------+-S4---OUT */
 /* PG---S2---S3-+          */
                CH.connect1 = new IntSubArray(pg_in4);
                CH.connect2 = new IntSubArray(pg_in3);
                CH.connect3 = new IntSubArray(pg_in4);
                break;
            case 3:
                /* PG---S1---S2-+-S4---OUT */
 /* PG---S3------+          */
                CH.connect1 = new IntSubArray(pg_in2);
                CH.connect2 = new IntSubArray(pg_in4);
                CH.connect3 = new IntSubArray(pg_in4);
                break;
            case 4:
                /* PG---S1---S2-+--OUT */
 /* PG---S3---S4-+      */
                CH.connect1 = new IntSubArray(pg_in2);
                CH.connect2 = carrier;
                CH.connect3 = new IntSubArray(pg_in4);
                break;
            case 5:
                /*         +-S2-+     */
 /* PG---S1-+-S3-+-OUT */
 /*         +-S4-+     */
                CH.connect1 = null;
                /* special case */

                CH.connect2 = carrier;
                CH.connect3 = carrier;
                break;
            case 6:
                /* PG---S1---S2-+     */
 /* PG--------S3-+-OUT */
 /* PG--------S4-+     */
                CH.connect1 = new IntSubArray(pg_in2);
                CH.connect2 = carrier;
                CH.connect3 = carrier;
                break;
            case 7:
                /* PG---S1-+     */
 /* PG---S2-+-OUT */
 /* PG---S3-+     */
 /* PG---S4-+     */
                CH.connect1 = carrier;
                CH.connect2 = carrier;
                CH.connect3 = carrier;
                break;
        }
        CH.connect4 = carrier;
    }

    /* set detune & multiple */
    static void set_det_mul(FM_ST ST, FM_CH CH, FM_SLOT SLOT, int v) {
        SLOT.mul = MUL_TABLE[v & 0x0f];
        SLOT.DT = ST.DT_TABLE[(v >> 4) & 7];
        CH.SLOT[SLOT1].Incr = -1;
    }

    /* set total level */
    static void set_tl(FM_CH CH, FM_SLOT SLOT, int v, int csmflag) {
        v &= 0x7f;
        v = (v << 7) | v;
        /* 7bit -> 14bit */

        SLOT.TL = (v * EG_ENT) >> 14;
        /* if it is not a CSM channel , latch the total level */
        if (csmflag == 0) {

            SLOT.TLL = SLOT.TL;
        }
    }

    /* set attack rate & key scale  */
    static void set_ar_ksr(FM_CH CH, FM_SLOT SLOT, int v, IntSubArray ar_table) {
        SLOT.KSR = (3 - (v >> 6));
        SLOT.AR = (v &= 0x1f) != 0 ? new IntSubArray(ar_table, v << 1) : new IntSubArray(RATE_0);
        SLOT.evsa = SLOT.AR.read(SLOT.ksr);
        if (SLOT.eg_next == FM_EG_AR) {
            SLOT.evs = SLOT.evsa;
        }
        CH.SLOT[SLOT1].Incr = -1;

    }

    /* set decay rate */
    static void set_dr(FM_SLOT SLOT, int v, IntSubArray dr_table) {
        SLOT.DR = (v &= 0x1f) != 0 ? new IntSubArray(dr_table, v << 1) : new IntSubArray(RATE_0);
        SLOT.evsd = SLOT.DR.read(SLOT.ksr);
        if (SLOT.eg_next == FM_EG_DR) {
            SLOT.evs = SLOT.evsd;
        }
    }

    /* set sustain rate */
    static void set_sr(FM_SLOT SLOT, int v, IntSubArray dr_table) {
        SLOT.SR = (v &= 0x1f) != 0 ? new IntSubArray(dr_table, v << 1) : new IntSubArray(RATE_0);
        SLOT.evss = SLOT.SR.read(SLOT.ksr);
        if (SLOT.eg_next == FM_EG_SR) {
            SLOT.evs = SLOT.evss;
        }
    }

    /* set release rate */
    static void set_sl_rr(FM_SLOT SLOT, int v, IntSubArray dr_table) {
        SLOT.SL = SL_TABLE[(v >> 4)];
        SLOT.RR = new IntSubArray(dr_table, ((v & 0x0f) << 2) | 2);
        SLOT.evsr = SLOT.RR.read(SLOT.ksr);
        if (SLOT.eg_next == FM_EG_Release) {
            SLOT.evs = SLOT.evsr;
        }
    }

    /* operator output calcrator */
    static int OP_OUT(int PG, int EG) {
        return SIN_TABLE[(PG / (0x1000000 / SIN_ENT)) & (SIN_ENT - 1)].read(EG);
    }

    public static int FM_CALC_EG(FM_SLOT SLOT) {
        if ((SLOT.evc += SLOT.evs) >= SLOT.eve) {
            SLOT.eg_next.handler(SLOT);
        }
        int OUT = SLOT.TLL + ENV_CURVE[SLOT.evc >> ENV_BITS];
        return OUT;
    }

    /* ---------- calcrate one of channel ---------- */
    public static void FM_CALC_CH(FM_CH CH) {
        long/*UINT32*/ eg_out1, eg_out2, eg_out3, eg_out4;  //envelope output

        pg_in1[0] = (int) (CH.SLOT[SLOT1].Cnt += CH.SLOT[SLOT1].Incr);
        pg_in2[0] = (int) (CH.SLOT[SLOT2].Cnt += CH.SLOT[SLOT2].Incr);
        pg_in3[0] = (int) (CH.SLOT[SLOT3].Cnt += CH.SLOT[SLOT3].Incr);
        pg_in4[0] = (int) (CH.SLOT[SLOT4].Cnt += CH.SLOT[SLOT4].Incr);

        /* Envelope Generator */
        eg_out1 = FM_CALC_EG(CH.SLOT[SLOT1]);
        eg_out2 = FM_CALC_EG(CH.SLOT[SLOT2]);
        eg_out3 = FM_CALC_EG(CH.SLOT[SLOT3]);
        eg_out4 = FM_CALC_EG(CH.SLOT[SLOT4]);

        /* Connection */
        if (eg_out1 < EG_CUT_OFF) /* SLOT 1 */ {
            if (CH.FB != 0) {
                /* with self feed back */
                pg_in1[0] += (CH.op1_out[0] + CH.op1_out[1]) >> CH.FB;
                CH.op1_out[1] = CH.op1_out[0];
            }
            CH.op1_out[0] = OP_OUT(pg_in1[0], (int) eg_out1);
            /* output slot1 */
            if (CH.connect1 == null) {
                /* algorythm 5  */
                pg_in2[0] += CH.op1_out[0];
                pg_in3[0] += CH.op1_out[0];
                pg_in4[0] += CH.op1_out[0];
            } else {
                /* other algorythm */
                CH.connect1.write(CH.connect1.read() + CH.op1_out[0]);//*CH->connect1 += CH->op1_out[0];
            }
        }
        if (eg_out2 < EG_CUT_OFF) /* SLOT 2 */ {
            CH.connect2.write(CH.connect2.read() + OP_OUT(pg_in2[0], (int) eg_out2));//*CH->connect2 += OP_OUT(pg_in2,eg_out2);
        }
        if (eg_out3 < EG_CUT_OFF) /* SLOT 3 */ {
            CH.connect3.write(CH.connect3.read() + OP_OUT(pg_in3[0], (int) eg_out3));//*CH->connect3 += OP_OUT(pg_in3,eg_out3);
        }
        if (eg_out4 < EG_CUT_OFF) /* SLOT 4 */ {
            CH.connect4.write(CH.connect4.read() + OP_OUT(pg_in4[0], (int) eg_out4));//*CH->connect4 += OP_OUT(pg_in4,eg_out4);
        }
    }

    /* ---------- frequency counter for operater update ---------- */
    public static void CALC_FCSLOT(FM_SLOT SLOT, int fc, int kc) {
        int ksr;

        /* frequency step counter */
 /* SLOT->Incr= (fc+SLOT->DT[kc])*SLOT->mul; */
        SLOT.Incr = fc * SLOT.mul + SLOT.DT[kc];
        ksr = kc >> SLOT.KSR;
        if (SLOT.ksr != ksr) {
            SLOT.ksr = ksr;
            /* attack , decay rate recalcration */
            SLOT.evsa = SLOT.AR.read(ksr);
            SLOT.evsd = SLOT.DR.read(ksr);
            SLOT.evss = SLOT.SR.read(ksr);
            SLOT.evsr = SLOT.RR.read(ksr);
        }
    }

    /* ---------- frequency counter  ---------- */
    static void OPN_CALC_FCOUNT(FM_CH CH) {
        if (CH.SLOT[SLOT1].Incr == -1) {
            int fc = (int) CH.fc;
            int kc = CH.kcode;
            CALC_FCSLOT(CH.SLOT[SLOT1], fc, kc);
            CALC_FCSLOT(CH.SLOT[SLOT2], fc, kc);
            CALC_FCSLOT(CH.SLOT[SLOT3], fc, kc);
            CALC_FCSLOT(CH.SLOT[SLOT4], fc, kc);
        }
    }

    /* ----------- initialize time tabls ----------- */
    static void init_timetables(FM_ST ST, int[] DTTABLE, int ARRATE, int DRRATE) {
        int i, d;
        double rate;

        /* DeTune table */
        for (d = 0; d <= 3; d++) {
            for (i = 0; i <= 31; i++) {
                rate = (double) DTTABLE[d * 32 + i] * ST.freqbase * FREQ_RATE;
                ST.DT_TABLE[d][i] = (int) rate;
                ST.DT_TABLE[d + 4][i] = (int) -rate;
            }
        }
        /* make Attack & Decay tables */
        for (i = 0; i < 4; i++) {
            ST.AR_TABLE.write(i, 0);
            ST.DR_TABLE.write(i, 0);
        }
        for (i = 4; i < 64; i++) {
            rate = ST.freqbase;
            /* frequency rate */

            if (i < 60) {
                rate *= 1.0 + (i & 3) * 0.25;
                /* b0-1 : x1 , x1.25 , x1.5 , x1.75 */

            }
            rate *= 1 << ((i >> 2) - 1);
            /* b2-5 : shift bit */

            rate *= (double) (EG_ENT << ENV_BITS);
            ST.AR_TABLE.write(i, (int) (rate / ARRATE));
            ST.DR_TABLE.write(i, (int) (rate / DRRATE));
        }
        ST.AR_TABLE.write(62, EG_AED);
        ST.AR_TABLE.write(63, EG_AED);
        for (i = 64; i < 94; i++) {
            /* make for overflow area */

            ST.AR_TABLE.write(i, ST.AR_TABLE.read(63));
            ST.DR_TABLE.write(i, ST.DR_TABLE.read(63));
        }

    }

    /* ---------- reset one of channel  ---------- */
    static void reset_channel(FM_ST ST, FM_CH[] CH, int chan) {
        int c, s;

        ST.mode = 0;
        /* normal mode */

        FM_STATUS_RESET(ST, 0xff);
        ST.TA = 0;
        ST.TAC = 0;
        ST.TB = 0;
        ST.TBC = 0;

        for (c = 0; c < chan; c++) {
            CH[c].fc = 0;
            CH[c].PAN = OUTD_CENTER;
            for (s = 0; s < 4; s++) {
                CH[c].SLOT[s].SEG = 0;
                CH[c].SLOT[s].eg_next = FM_EG_Release;
                CH[c].SLOT[s].evc = EG_OFF;
                CH[c].SLOT[s].eve = EG_OFF + 1;
                CH[c].SLOT[s].evs = 0;
            }
        }
    }

    /* ---------- generic table initialize ---------- */
    static int FMInitTable() {
        int s, t;
        double rate;
        int i, j;
        double pom;

        /* allocate total level table plus+minus section */
        TL_TABLE = new int[2 * TL_MAX];
        /* make total level table */
        for (t = 0; t < TL_MAX; t++) {
            if (t >= PG_CUT_OFF) {
                rate = 0;
                /* under cut off area */

            } else {
                rate = ((1 << TL_BITS) - 1) / Math.pow(10, EG_STEP * t / 20);
                /* dB -> voltage */

            }
            TL_TABLE[t] = (int) rate;
            TL_TABLE[TL_MAX + t] = -TL_TABLE[t];
            /*		Log(LOG_INF,"TotalLevel(%3d) = %x\n",t,TL_TABLE[t]);*/
        }
        /* make sinwave table (pointer of total level) */
        for (s = 1; s <= SIN_ENT / 4; s++) {
            pom = Math.sin(2.0 * Math.PI * s / SIN_ENT);
            /* sin   */

            pom = 20 * Math.log10(1 / pom);
            /* -> decibel */

            j = (int) (pom / EG_STEP);
            /* TL_TABLE steps */
 /* cut off check */

            if (j > PG_CUT_OFF) {
                j = PG_CUT_OFF;
            }
            /* degree 0   -  90    , degree 180 -  90 : plus section */
            SIN_TABLE[s] = SIN_TABLE[SIN_ENT / 2 - s] = new IntSubArray(TL_TABLE, j);
            /* degree 180 - 270    , degree 360 - 270 : minus section */
            SIN_TABLE[SIN_ENT / 2 + s] = SIN_TABLE[SIN_ENT - s] = new IntSubArray(TL_TABLE, TL_MAX + j);
            /* Log(LOG_INF,"sin(%3d) = %f:%f db\n",s,pom,(double)j * EG_STEP); */
        }
        /* degree 0 = degree 180                   = off */
        SIN_TABLE[0] = SIN_TABLE[SIN_ENT / 2] = new IntSubArray(TL_TABLE, PG_CUT_OFF);

        /* envelope counter -> envelope output table */
        for (i = 0; i < EG_ENT; i++) {
            /* ATTACK curve */
 /* !!!!! preliminary !!!!! */
            pom = Math.pow(((double) (EG_ENT - 1 - i) / EG_ENT), 8) * EG_ENT;
            /* if( pom >= EG_ENT ) pom = EG_ENT-1; */
            ENV_CURVE[i] = (int) pom;
            /* DECAY ,RELEASE curve */
            ENV_CURVE[(EG_DST >> ENV_BITS) + i] = i;
            /*TODO*///#if FM_SEG_SUPPORT
/*TODO*///		/* DECAY UPSIDE (SSG ENV) */
/*TODO*///		ENV_CURVE[(EG_UST>>ENV_BITS)+i]= EG_ENT-1-i;
/*TODO*///#endif
        }
        /* off */
        ENV_CURVE[EG_OFF >> ENV_BITS] = EG_ENT - 1;

        /* decay to reattack envelope converttable */
        j = EG_ENT - 1;
        for (i = 0; i < EG_ENT; i++) {
            while (j != 0 && (ENV_CURVE[j] < i)) {
                j--;
            }
            DRAR_TABLE[i] = j << ENV_BITS;
            /* Log(LOG_INF,"DR %06X = %06X,AR=%06X\n",i,DRAR_TABLE[i],ENV_CURVE[DRAR_TABLE[i]>>ENV_BITS] ); */
        }
        return 1;
    }

    static void FMCloseTable() {
        if (TL_TABLE != null) {
            TL_TABLE = null;
        }
    }

    /* OPN/OPM Mode  Register Write */
    static void FMSetMode(FM_ST ST, int n, int v) {
        /* b7 = CSM MODE */
 /* b6 = 3 slot mode */
 /* b5 = reset b */
 /* b4 = reset a */
 /* b3 = timer enable b */
 /* b2 = timer enable a */
 /* b1 = load b */
 /* b0 = load a */
        ST.mode = v;

        /* reset Timer b flag */
        if ((v & 0x20) != 0) {
            FM_STATUS_RESET(ST, 0x02);
        }
        /* reset Timer a flag */
        if ((v & 0x10) != 0) {
            FM_STATUS_RESET(ST, 0x01);
        }
        /* load b */
        if ((v & 0x02) != 0) {
            if (ST.TBC == 0) {
                ST.TBC = (256 - ST.TB) << 4;
                /* External timer handler */
                if (ST.Timer_Handler != null) {
                    ST.Timer_Handler.fmTimerHandler(n, 1, ST.TBC, ST.TimerBase);
                }
            }
        } else if (ST.timermodel == FM_TIMER_INTERVAL) {
            /* stop interval timer */

            if (ST.TBC != 0) {
                ST.TBC = 0;
                if (ST.Timer_Handler != null) {
                    ST.Timer_Handler.fmTimerHandler(n, 1, 0, ST.TimerBase);
                }
            }
        }
        /* load a */
        if ((v & 0x01) != 0) {
            if (ST.TAC == 0) {
                ST.TAC = (1024 - ST.TA);
                /* External timer handler */
                if (ST.Timer_Handler != null) {
                    ST.Timer_Handler.fmTimerHandler(n, 0, ST.TAC, ST.TimerBase);
                }
            }
        } else if (ST.timermodel == FM_TIMER_INTERVAL) {
            /* stop interval timer */

            if (ST.TAC != 0) {
                ST.TAC = 0;
                if (ST.Timer_Handler != null) {
                    ST.Timer_Handler.fmTimerHandler(n, 0, 0, ST.TimerBase);
                }
            }
        }
    }

    /* Timer A Overflow */
    static void TimerAOver(FM_ST ST) {
        /* status set if enabled */
        if ((ST.mode & 0x04) != 0) {
            FM_STATUS_SET(ST, 0x01);
        }
        /* clear or reload the counter */
        if (ST.timermodel == FM_TIMER_INTERVAL) {
            ST.TAC = (1024 - ST.TA);
            if (ST.Timer_Handler != null) {
                ST.Timer_Handler.fmTimerHandler(ST.index, 0, ST.TAC, ST.TimerBase);
            }
        } else {
            ST.TAC = 0;
        }
    }

    /* Timer B Overflow */
    static void TimerBOver(FM_ST ST) {
        /* status set if enabled */
        if ((ST.mode & 0x08) != 0) {
            FM_STATUS_SET(ST, 0x02);
        }
        /* clear or reload the counter */
        if (ST.timermodel == FM_TIMER_INTERVAL) {
            ST.TBC = (256 - ST.TB) << 4;
            if (ST.Timer_Handler != null) {
                ST.Timer_Handler.fmTimerHandler(ST.index, 1, ST.TBC, ST.TimerBase);
            }
        } else {
            ST.TBC = 0;
        }
    }

    /* CSM Key Control */
    static void CSMKeyControll(FM_CH CH) {

    /* total level latch */
        CH.SLOT[SLOT1].TLL = CH.SLOT[SLOT1].TL;
        CH.SLOT[SLOT2].TLL = CH.SLOT[SLOT2].TL;
        CH.SLOT[SLOT3].TLL = CH.SLOT[SLOT3].TL;
        CH.SLOT[SLOT4].TLL = CH.SLOT[SLOT4].TL;
        /* all key on */
        FM_KEYON(CH, SLOT1);
        FM_KEYON(CH, SLOT2);
        FM_KEYON(CH, SLOT3);
        FM_KEYON(CH, SLOT4);
    }

    /**
     * ********************************************************
     * OPN unit
     */
    /* OPN 3slot struct */
    public static class FM_3SLOT {

        public FM_3SLOT() {
            fc = new long[3];
            fn_h = new int[3];
            kcode = new int[3];
        }
        public long[] /*UINT32*/ fc;/* fnum3,blk3  :calcrated */
        public int[] /*UINT8*/ fn_h;/* freq3 latch            */
        public int[] /*UINT8*/ kcode;/* key code    :          */
    }

    /* OPN/A/B common state */
    public static class FM_OPN {

        public FM_OPN() {
            ST = new FM_ST();
            SL3 = new FM_3SLOT();
            FN_TABLE = new long[2048];
        }

        public int /*UINT8*/ type;/* chip type         */
        public FM_ST ST;/* general state     */
        public FM_3SLOT SL3;/* 3 slot mode state */
        public FM_CH[] P_CH;/* pointer of CH     */
        public long[] /*UINT32*/ FN_TABLE;/* fnumber -> increment counter */

    }

    /* OPN key frequency number -> key code follow table */
    /* fnum higher 4bit -> keycode lower 2bit */
    static int OPN_FKTABLE[] = {0, 0, 0, 0, 0, 0, 0, 1, 2, 3, 3, 3, 3, 3, 3, 3};


    static int OPNInitTable() {
        int i;

        return FMInitTable();
    }

    /* ---------- prescaler set(and make time tables) ---------- */
    static void OPNSetPrescaler(FM_OPN OPN, int pres, int TimerPris, int SSGpris) {
        int i;

        /* frequency base */
        OPN.ST.freqbase = (OPN.ST.rate) != 0 ? ((double) OPN.ST.clock / OPN.ST.rate) / pres : 0;
        /* Timer base time */
        OPN.ST.TimerBase = 1.0 / ((double) OPN.ST.clock / (double) TimerPris);
        /* SSG part  prescaler set */
        if (SSGpris != 0) {
            OPN.ST.SsgHandler.SsgClk(OPN.ST.index, OPN.ST.clock * 2 / SSGpris);
        }
        /* make time tables */
        init_timetables(OPN.ST, OPN_DTTABLE, OPN_ARRATE, OPN_DRRATE);
        /* make fnumber -> increment counter table */

        for (i = 0; i < 2048; i++) {
            /* it is freq table for octave 7 */
        	/* opn freq counter = 20bit */
            OPN.FN_TABLE[i] = (long) ((double) i * OPN.ST.freqbase * FREQ_RATE * (1 << 7) / 2) & 0xFFFFFFFFL;
        }
    }

    /* ---------- write a OPN mode register 0x20-0x2f ---------- */
    static void OPNWriteMode(FM_OPN OPN, int r, int v) {
        int /*UINT8*/ c;
        FM_CH CH;

        switch (r) {
            case 0x21:
                /* Test */
                break;

            case 0x24:
                /* timer A High 8*/
                OPN.ST.TA = (OPN.ST.TA & 0x03) | (((int) v) << 2);
                break;
            case 0x25:
                /* timer A Low 2*/
                OPN.ST.TA = (OPN.ST.TA & 0x3fc) | (v & 3);
                break;
            case 0x26:
                /* timer B */
                OPN.ST.TB = v;
                break;
            case 0x27:
                /* mode , timer controll */
                FMSetMode((OPN.ST), OPN.ST.index, v);
                break;
            case 0x28:
                /* key on / off */
                c = v & 0x03;
                if (c == 3) {
                    break;
                }
                if ((v & 0x04) != 0 && (OPN.type & TYPE_6CH) != 0) {
                    c += 3;
                }
                CH = OPN.P_CH[c];//CH = &CH[c];
                /* csm mode */
 /* if( c == 2 && (OPN->ST.mode & 0x80) ) break; */
                if ((v & 0x10) != 0) {
                    FM_KEYON(CH, SLOT1);
                } else {
                    FM_KEYOFF(CH, SLOT1);
                }
                if ((v & 0x20) != 0) {
                    FM_KEYON(CH, SLOT2);
                } else {
                    FM_KEYOFF(CH, SLOT2);
                }
                if ((v & 0x40) != 0) {
                    FM_KEYON(CH, SLOT3);
                } else {
                    FM_KEYOFF(CH, SLOT3);
                }
                if ((v & 0x80) != 0) {
                    FM_KEYON(CH, SLOT4);
                } else {
                    FM_KEYOFF(CH, SLOT4);
                }
                /*		LOG(LOG_INF,("OPN %d:%d : KEY %02X\n",n,c,v&0xf0));*/
                break;
        }
    }

    /* ---------- write a OPN register (0x30-0xff) ---------- */
    static void OPNWriteReg(FM_OPN OPN, int r, int v) {
        int/*UINT8*/ c;
        FM_CH CH;
        FM_SLOT SLOT;

        /* 0x30 - 0xff */
        if ((c = OPN_CHAN(r)) == 3) {
            return;
            /* 0xX3,0xX7,0xXB,0xXF */

        }
        if ((r >= 0x100) /* && (OPN->type & TYPE_6CH) */) {
            c += 3;
        }
        CH = OPN.P_CH[c];//CH = &CH[c];

        SLOT = (CH.SLOT[OPN_SLOT(r)]);
        switch (r & 0xf0) {
            case 0x30:
                /* DET , MUL */
                set_det_mul(OPN.ST, CH, SLOT, v);
                break;
            case 0x40:
                /* TL */
                set_tl(CH, SLOT, v, (c == 2) && ((OPN.ST.mode & 0x80) != 0) ? 1 : 0);
                break;
            case 0x50:
                /* KS, AR */
                set_ar_ksr(CH, SLOT, v, OPN.ST.AR_TABLE);
                break;
            case 0x60:
                /*     DR */
 /* bit7 = AMS_ON ENABLE(YM2612) */
                set_dr(SLOT, v, OPN.ST.DR_TABLE);
                break;
            case 0x70:
                /*     SR */
                set_sr(SLOT, v, OPN.ST.DR_TABLE);
                break;
            case 0x80:
                /* SL, RR */
                set_sl_rr(SLOT, v, OPN.ST.DR_TABLE);
                break;
            case 0x90:
                /* SSG-EG */
                //if(v&0x08) LOG(LOG_ERR,("OPN %d,%d,%d :SSG-TYPE envelope selected (not supported )\n",OPN->ST.index,c,OPN_SLOT(r)));
                SLOT.SEG = v & 0x0f;
                break;
            case 0xa0:
                switch (OPN_SLOT(r)) {
                    case 0: /* 0xa0-0xa2 : FNUM1 */ {
                        long fn = (((long) ((CH.fn_h) & 7)) << 8) + v;
                        int blk = CH.fn_h >> 3;
                        /* make keyscale code */
                        CH.kcode = (blk << 2) | OPN_FKTABLE[(int) (fn >> 7)];
                        /* make basic increment counter 32bit = 1 cycle */
                        CH.fc = OPN.FN_TABLE[(int) fn] >> (7 - blk);
                        CH.SLOT[SLOT1].Incr = -1;
                    }
                    break;
                    case 1:
                        /* 0xa4-0xa6 : FNUM2,BLK */
                        CH.fn_h = v & 0x3f;
                        break;
                    case 2:
                        /* 0xa8-0xaa : 3CH FNUM1 */
                        if (r < 0x100) {
                            long fn = (((long) (OPN.SL3.fn_h[c] & 7)) << 8) + v;
                            int blk = OPN.SL3.fn_h[c] >> 3;
                            /* make keyscale code */
                            OPN.SL3.kcode[c] = (blk << 2) | OPN_FKTABLE[(int) (fn >> 7)];
                            /* make basic increment counter 32bit = 1 cycle */
                            OPN.SL3.fc[c] = OPN.FN_TABLE[(int) fn] >> (7 - blk);
                            (OPN.P_CH)[2].SLOT[SLOT1].Incr = -1;
                        }
                        break;
                    case 3:
                        /* 0xac-0xae : 3CH FNUM2,BLK */
                        if (r < 0x100) {
                            OPN.SL3.fn_h[c] = v & 0x3f;
                        }
                        break;
                }
                break;
            case 0xb0:
                switch (OPN_SLOT(r)) {
                    case 0: /* 0xb0-0xb2 : FB,ALGO */ {
                        int feedback = (v >> 3) & 7;
                        CH.ALGO = v & 7;
                        CH.FB = feedback != 0 ? 8 + 1 - feedback : 0;
                        setup_connection(CH);
                    }
                    break;
                    case 1:
                        /* 0xb4-0xb6 : L , R , AMS , PMS (YM2612/YM2608) */
                        if ((OPN.type & TYPE_LFOPAN) != 0) {

                            /* PAN */
                            CH.PAN = (v >> 6) & 0x03;
                            /* PAN : b6 = R , b7 = L */
                            setup_connection(CH);
                            /* LOG(LOG_INF,("OPN %d,%d : PAN %d\n",n,c,CH->PAN));*/
                        }
                        break;
                }
                break;
        }
    }

    /**
     * ****************************************************************************
     */
    /*		YM2203 local section                                                   */
    /**
     * ****************************************************************************
     */
    public static class YM2203 {

        public FM_OPN OPN;
        public FM_CH[] CH;

        public YM2203() {
            OPN = new FM_OPN();
            CH = new FM_CH[3];
            for (int i = 0; i < 3; i++) {
                CH[i] = new FM_CH();
            }
        }
    }
    static YM2203[] FM2203 = null;/* array of YM2203's */
    static int YM2203NumChips;/* total chip */

 /* ---------- update one of chip ----------- */
    public static void UpdateStream(int num, short[] buffer, int length) {
        YM2203 F2203 = (FM2203[num]);
        FM_OPN OPN = (FM2203[num].OPN);
        int i;
        int ch;

        cur_chip = F2203;
        State = F2203.OPN.ST;
        cch[0] = F2203.CH[0];
        cch[1] = F2203.CH[1];
        cch[2] = F2203.CH[2];

        /* frequency counter channel A */
        OPN_CALC_FCOUNT(cch[0]);/* frequency counter channel B */
        OPN_CALC_FCOUNT(cch[1]);/* frequency counter channel C */
        if (((State.mode & 0xc0) != 0)) {
            /* 3SLOT MODE */
            if (cch[2].SLOT[SLOT1].Incr == -1) {
                /* 3 slot mode */
                CALC_FCSLOT(cch[2].SLOT[SLOT1], (int) OPN.SL3.fc[1], OPN.SL3.kcode[1]);
                CALC_FCSLOT(cch[2].SLOT[SLOT2], (int) OPN.SL3.fc[2], OPN.SL3.kcode[2]);
                CALC_FCSLOT(cch[2].SLOT[SLOT3], (int) OPN.SL3.fc[0], OPN.SL3.kcode[0]);
                CALC_FCSLOT(cch[2].SLOT[SLOT4], (int) cch[2].fc, cch[2].kcode);
            }
        } else {
            OPN_CALC_FCOUNT(cch[2]);
        }

        for (i = 0; i < length; i++) {
            /*            channel A         channel B         channel C      */
            out_ch[OUTD_CENTER] = 0;
            /* calculate FM */
            for (ch = 0; ch <= 2; ch++) {
                FM_CALC_CH(cch[ch]);
            }
            /* limit check */
            //Limit( out_ch[OUTD_CENTER] , FM_MAXOUT, FM_MINOUT );
            if (out_ch[OUTD_CENTER] > FM_MAXOUT) {
                out_ch[OUTD_CENTER] = FM_MAXOUT;
            } else if (out_ch[OUTD_CENTER] < FM_MINOUT) {
                out_ch[OUTD_CENTER] = FM_MINOUT;
            }
            /* store to sound buffer */
            buffer[i] = (short) (out_ch[OUTD_CENTER] >> FM_OUTSB);
            /* timer controll */
            INTERNAL_TIMER_A(State, cch[2]);
        }
        INTERNAL_TIMER_B(State, length);
    }

    /* ---------- reset one of chip ---------- */
    public static void YM2203ResetChip(int num) {
        int i;
        FM_OPN OPN = (FM2203[num].OPN);

        /* Reset Prescaler */
        OPNSetPrescaler(OPN, 6 * 12, 6 * 12, 4);
        /* 1/6 , 1/4 */
        /* reset SSG section */
        OPN.ST.SsgHandler.SsgReset(OPN.ST.index);
        /* status clear */
        FM_IRQMASK_SET(OPN.ST, 0x03);
        OPNWriteMode(OPN, 0x27, 0x30);
        /* mode 0 , timer reset */
        reset_channel(OPN.ST, FM2203[num].CH, 3);
        /* reset OPerator parameter */
        for (i = 0xb6; i >= 0xb4; i--) {
            OPNWriteReg(OPN, i, 0xc0);
            /* PAN RESET */
        }
        for (i = 0xb2; i >= 0x30; i--) {
            OPNWriteReg(OPN, i, 0);
        }
        for (i = 0x26; i >= 0x20; i--) {
            OPNWriteReg(OPN, i, 0);
        }
    }

    /* ----------  Initialize YM2203 emulator(s) ----------    */
    /* 'num' is the number of virtual YM2203's to allocate     */
    /* 'rate' is sampling rate */
    public static int YM2203Init(int num, int clock, int rate, FmTimerHandler TimerHandler, FmIrqHandler IRQHandler, FmSsgHandler SsgHandler) {
        int i;

        if (FM2203 != null) {
            return (-1);
            /* duplicate init. */
        }
        cur_chip = null;
        /* hiro-shi!! */

        YM2203NumChips = num;

        FM2203 = new YM2203[YM2203NumChips];
        for (i = 0; i < YM2203NumChips; i++) {
            FM2203[i] = new YM2203();
        }

        /* allocate total level table (128kb space) */
        if (OPNInitTable() == 0) {
            FM2203 = null;
            return (-1);
        }
        for (i = 0; i < YM2203NumChips; i++) {
            FM2203[i].OPN.ST.index = i;
            FM2203[i].OPN.type = TYPE_YM2203;
            FM2203[i].OPN.P_CH = FM2203[i].CH;
            FM2203[i].OPN.ST.clock = clock;
            FM2203[i].OPN.ST.rate = rate;
            FM2203[i].OPN.ST.timermodel = FM_TIMER_INTERVAL;
            /* Extend handler */
            FM2203[i].OPN.ST.Timer_Handler = TimerHandler;
            FM2203[i].OPN.ST.IRQ_Handler = IRQHandler;
            FM2203[i].OPN.ST.SsgHandler = SsgHandler;
            YM2203ResetChip(i);
        }
        return (0);
    }

    /* ---------- shut down emulator ----------- */
    public static void YM2203Shutdown() {
        if (FM2203 == null) {
            return;
        }

        FMCloseTable();
        FM2203 = null;
    }

    /* ---------- YM2203 I/O interface ---------- */
    private static void YM2203UpdateRequest(int n) {
    	//TODO - implement
    }
    
    public static int YM2203Write(int n, int a, int v) {
        FM_OPN OPN = (FM2203[n].OPN);

        if ((a & 1) == 0) {
            /* address port */
            OPN.ST.address = v & 0xff;
            /* Write register to SSG emurator */
            if (v < 16) {
            	OPN.ST.SsgHandler.SsgWrite(n, 0, v);
            }
            switch (OPN.ST.address) {
                case 0x2d:
                    /* divider sel */
                    OPNSetPrescaler(OPN, 6 * 12, 6 * 12, 4);
                    /* OPN 1/6 , SSG 1/4 */
                    break;
                case 0x2e:
                    /* divider sel */
                    OPNSetPrescaler(OPN, 3 * 12, 3 * 12, 2);
                    /* OPN 1/3 , SSG 1/2 */
                    break;
                case 0x2f:
                    /* divider sel */
                    OPNSetPrescaler(OPN, 2 * 12, 2 * 12, 1);
                    /* OPN 1/2 , SSG 1/1 */
                    break;
            }
        } else {
            /* data port */
            int addr = OPN.ST.address;
            switch (addr & 0xf0) {
                case 0x00:
                    /* 0x00-0x0f : SSG section */
                	/* Write data to SSG emulator */
                	OPN.ST.SsgHandler.SsgWrite(n, a, v);
                    break;
                case 0x20:
                    /* 0x20-0x2f : Mode section */
                    YM2203UpdateRequest(n);
                    /* write register */
                    OPNWriteMode(OPN, addr, v);
                    break;
                default:
                    /* 0x30-0xff : OPN section */
                    YM2203UpdateRequest(n);
                    /* write register */
                    OPNWriteReg(OPN, addr, v);
            }
        }
        return OPN.ST.irq;
    }

    public static int YM2203Read(int n, int a) {
        YM2203 F2203 = (FM2203[n]);
        int addr = F2203.OPN.ST.address;
        int ret = 0;

        if ((a & 1) == 0) {
            /* status port */
            ret = F2203.OPN.ST.status;
        } else {
            /* data port (ONLY SSG) */
            if (addr < 16) {
                ret = F2203.OPN.ST.SsgHandler.SsgRead(n);
            }
        }
        return ret;
    }

    public static int YM2203TimerOver(int n, int c) {
        YM2203 F2203 = (FM2203[n]);

        if (c != 0) {
            /* Timer B */
            TimerBOver((F2203.OPN.ST));
        } else {
            /* Timer A */
            YM2203UpdateRequest(n);
            /* timer update */
            TimerAOver((F2203.OPN.ST));
            /* CSM mode key,TL control */
            if ((F2203.OPN.ST.mode & 0x80) != 0) {
                /* CSM mode total level latch and auto key on */
                CSMKeyControll((F2203.CH[2]));
            }
        }
        return F2203.OPN.ST.irq;
    }

}
