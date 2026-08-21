/*
 * ported to 0.36
 */
package emulator.processor.m6809;

public class m6809H {

    public static final int M6809_PC = 1;
    public static final int M6809_S = 2;
    public static final int M6809_CC = 3;
    public static final int M6809_A = 4;
    public static final int M6809_B = 5;
    public static final int M6809_U = 6;
    public static final int M6809_X = 7;
    public static final int M6809_Y = 8;
    public static final int M6809_DP = 9;
    public static final int M6809_NMI_STATE = 10;
    public static final int M6809_IRQ_STATE = 11;
    public static final int M6809_FIRQ_STATE = 12;

    public static final int M6809_INT_NONE = 0;/* No interrupt required */
    public static final int M6809_INT_IRQ = 1;/* Standard IRQ interrupt */
    public static final int M6809_INT_FIRQ = 2;/* Fast IRQ */
    public static final int M6809_INT_NMI = 4;/* NMI */ /* NS 970909 */
    public static final int M6809_IRQ_LINE = 0;/* IRQ line number */
    public static final int M6809_FIRQ_LINE = 1;/* FIRQ line number */

    public static final int HD6309_INT_NONE = M6809_INT_NONE;
    public static final int HD6309_INT_IRQ = M6809_INT_IRQ;
    public static final int HD6309_INT_FIRQ = M6809_INT_FIRQ;
    public static final int HD6309_INT_NMI = M6809_INT_NMI;
    public static final int M6309_IRQ_LINE = M6809_IRQ_LINE;
    public static final int M6309_FIRQ_LINE = M6809_FIRQ_LINE;

}
