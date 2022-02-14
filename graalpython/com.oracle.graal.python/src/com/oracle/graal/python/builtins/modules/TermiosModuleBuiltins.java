/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.modules;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "termios", isEager = true)
public class TermiosModuleBuiltins extends PythonBuiltins {

    public TermiosModuleBuiltins() {
        builtinConstants.put(SpecialAttributeNames.__DOC__,
                        "This module provides an interface to the Posix calls for tty I/O control.\n" +
                                        "For a complete description of these calls, see the Posix or Unix manual\n" +
                                        "pages. It is only available for those Unix versions that support Posix\n" +
                                        "termios style tty I/O control.\n\n" +
                                        "All functions in this module take a file descriptor fd as their first\n" +
                                        "argument. This can be an integer file descriptor, such as returned by\n" +
                                        "sys.stdin.fileno(), or a file object, such as sys.stdin itself.\n");

        // Note: copied from CPython 3.6.5
        builtinConstants.put("B0", 0);
        builtinConstants.put("B1000000", 4104);
        builtinConstants.put("B110", 3);
        builtinConstants.put("B115200", 4098);
        builtinConstants.put("B1152000", 4105);
        builtinConstants.put("B1200", 9);
        builtinConstants.put("B134", 4);
        builtinConstants.put("B150", 5);
        builtinConstants.put("B1500000", 4106);
        builtinConstants.put("B1800", 10);
        builtinConstants.put("B19200", 14);
        builtinConstants.put("B200", 6);
        builtinConstants.put("B2000000", 4107);
        builtinConstants.put("B230400", 4099);
        builtinConstants.put("B2400", 11);
        builtinConstants.put("B2500000", 4108);
        builtinConstants.put("B300", 7);
        builtinConstants.put("B3000000", 4109);
        builtinConstants.put("B3500000", 4110);
        builtinConstants.put("B38400", 15);
        builtinConstants.put("B4000000", 4111);
        builtinConstants.put("B460800", 4100);
        builtinConstants.put("B4800", 12);
        builtinConstants.put("B50", 1);
        builtinConstants.put("B500000", 4101);
        builtinConstants.put("B57600", 4097);
        builtinConstants.put("B576000", 4102);
        builtinConstants.put("B600", 8);
        builtinConstants.put("B75", 2);
        builtinConstants.put("B921600", 4103);
        builtinConstants.put("B9600", 13);
        builtinConstants.put("BRKINT", 2);
        builtinConstants.put("BS0", 0);
        builtinConstants.put("BS1", 8192);
        builtinConstants.put("BSDLY", 8192);
        builtinConstants.put("CBAUD", 4111);
        builtinConstants.put("CBAUDEX", 4096);
        builtinConstants.put("CDSUSP", 25);
        builtinConstants.put("CEOF", 4);
        builtinConstants.put("CEOL", 0);
        builtinConstants.put("CEOT", 4);
        builtinConstants.put("CERASE", 127);
        builtinConstants.put("CFLUSH", 15);
        builtinConstants.put("CIBAUD", 269418496);
        builtinConstants.put("CINTR", 3);
        builtinConstants.put("CKILL", 21);
        builtinConstants.put("CLNEXT", 22);
        builtinConstants.put("CLOCAL", 2048);
        builtinConstants.put("CQUIT", 28);
        builtinConstants.put("CR0", 0);
        builtinConstants.put("CR1", 512);
        builtinConstants.put("CR2", 1024);
        builtinConstants.put("CR3", 1536);
        builtinConstants.put("CRDLY", 1536);
        builtinConstants.put("CREAD", 128);
        builtinConstants.put("CRPRNT", 18);
        builtinConstants.put("CRTSCTS", 2147483648L);
        builtinConstants.put("CS5", 0);
        builtinConstants.put("CS6", 16);
        builtinConstants.put("CS7", 32);
        builtinConstants.put("CS8", 48);
        builtinConstants.put("CSIZE", 48);
        builtinConstants.put("CSTART", 17);
        builtinConstants.put("CSTOP", 19);
        builtinConstants.put("CSTOPB", 64);
        builtinConstants.put("CSUSP", 26);
        builtinConstants.put("CWERASE", 23);
        builtinConstants.put("ECHO", 8);
        builtinConstants.put("ECHOCTL", 512);
        builtinConstants.put("ECHOE", 16);
        builtinConstants.put("ECHOK", 32);
        builtinConstants.put("ECHOKE", 2048);
        builtinConstants.put("ECHONL", 64);
        builtinConstants.put("ECHOPRT", 1024);
        builtinConstants.put("EXTA", 14);
        builtinConstants.put("EXTB", 15);
        builtinConstants.put("FF0", 0);
        builtinConstants.put("FF1", 32768);
        builtinConstants.put("FFDLY", 32768);
        builtinConstants.put("FIOASYNC", 21586);
        builtinConstants.put("FIOCLEX", 21585);
        builtinConstants.put("FIONBIO", 21537);
        builtinConstants.put("FIONCLEX", 21584);
        builtinConstants.put("FIONREAD", 21531);
        builtinConstants.put("FLUSHO", 4096);
        builtinConstants.put("HUPCL", 1024);
        builtinConstants.put("ICANON", 2);
        builtinConstants.put("ICRNL", 256);
        builtinConstants.put("IEXTEN", 32768);
        builtinConstants.put("IGNBRK", 1);
        builtinConstants.put("IGNCR", 128);
        builtinConstants.put("IGNPAR", 4);
        builtinConstants.put("IMAXBEL", 8192);
        builtinConstants.put("INLCR", 64);
        builtinConstants.put("INPCK", 16);
        builtinConstants.put("IOCSIZE_MASK", 1073676288);
        builtinConstants.put("IOCSIZE_SHIFT", 16);
        builtinConstants.put("ISIG", 1);
        builtinConstants.put("ISTRIP", 32);
        builtinConstants.put("IUCLC", 512);
        builtinConstants.put("IXANY", 2048);
        builtinConstants.put("IXOFF", 4096);
        builtinConstants.put("IXON", 1024);
        builtinConstants.put("NCC", 8);
        builtinConstants.put("NCCS", 32);
        builtinConstants.put("NL0", 0);
        builtinConstants.put("NL1", 256);
        builtinConstants.put("NLDLY", 256);
        builtinConstants.put("NOFLSH", 128);
        builtinConstants.put("N_MOUSE", 2);
        builtinConstants.put("N_PPP", 3);
        builtinConstants.put("N_SLIP", 1);
        builtinConstants.put("N_STRIP", 4);
        builtinConstants.put("N_TTY", 0);
        builtinConstants.put("OCRNL", 8);
        builtinConstants.put("OFDEL", 128);
        builtinConstants.put("OFILL", 64);
        builtinConstants.put("OLCUC", 2);
        builtinConstants.put("ONLCR", 4);
        builtinConstants.put("ONLRET", 32);
        builtinConstants.put("ONOCR", 16);
        builtinConstants.put("OPOST", 1);
        builtinConstants.put("PARENB", 256);
        builtinConstants.put("PARMRK", 8);
        builtinConstants.put("PARODD", 512);
        builtinConstants.put("PENDIN", 16384);
        builtinConstants.put("TAB0", 0);
        builtinConstants.put("TAB1", 2048);
        builtinConstants.put("TAB2", 4096);
        builtinConstants.put("TAB3", 6144);
        builtinConstants.put("TABDLY", 6144);
        builtinConstants.put("TCFLSH", 21515);
        builtinConstants.put("TCGETA", 21509);
        builtinConstants.put("TCGETS", 21505);
        builtinConstants.put("TCIFLUSH", 0);
        builtinConstants.put("TCIOFF", 2);
        builtinConstants.put("TCIOFLUSH", 2);
        builtinConstants.put("TCION", 3);
        builtinConstants.put("TCOFLUSH", 1);
        builtinConstants.put("TCOOFF", 0);
        builtinConstants.put("TCOON", 1);
        builtinConstants.put("TCSADRAIN", 1);
        builtinConstants.put("TCSAFLUSH", 2);
        builtinConstants.put("TCSANOW", 0);
        builtinConstants.put("TCSBRK", 21513);
        builtinConstants.put("TCSBRKP", 21541);
        builtinConstants.put("TCSETA", 21510);
        builtinConstants.put("TCSETAF", 21512);
        builtinConstants.put("TCSETAW", 21511);
        builtinConstants.put("TCSETS", 21506);
        builtinConstants.put("TCSETSF", 21508);
        builtinConstants.put("TCSETSW", 21507);
        builtinConstants.put("TCXONC", 21514);
        builtinConstants.put("TIOCCONS", 21533);
        builtinConstants.put("TIOCEXCL", 21516);
        builtinConstants.put("TIOCGETD", 21540);
        builtinConstants.put("TIOCGICOUNT", 21597);
        builtinConstants.put("TIOCGLCKTRMIOS", 21590);
        builtinConstants.put("TIOCGPGRP", 21519);
        builtinConstants.put("TIOCGSERIAL", 21534);
        builtinConstants.put("TIOCGSOFTCAR", 21529);
        builtinConstants.put("TIOCGWINSZ", 21523);
        builtinConstants.put("TIOCINQ", 21531);
        builtinConstants.put("TIOCLINUX", 21532);
        builtinConstants.put("TIOCMBIC", 21527);
        builtinConstants.put("TIOCMBIS", 21526);
        builtinConstants.put("TIOCMGET", 21525);
        builtinConstants.put("TIOCMIWAIT", 21596);
        builtinConstants.put("TIOCMSET", 21528);
        builtinConstants.put("TIOCM_CAR", 64);
        builtinConstants.put("TIOCM_CD", 64);
        builtinConstants.put("TIOCM_CTS", 32);
        builtinConstants.put("TIOCM_DSR", 256);
        builtinConstants.put("TIOCM_DTR", 2);
        builtinConstants.put("TIOCM_LE", 1);
        builtinConstants.put("TIOCM_RI", 128);
        builtinConstants.put("TIOCM_RNG", 128);
        builtinConstants.put("TIOCM_RTS", 4);
        builtinConstants.put("TIOCM_SR", 16);
        builtinConstants.put("TIOCM_ST", 8);
        builtinConstants.put("TIOCNOTTY", 21538);
        builtinConstants.put("TIOCNXCL", 21517);
        builtinConstants.put("TIOCOUTQ", 21521);
        builtinConstants.put("TIOCPKT", 21536);
        builtinConstants.put("TIOCPKT_DATA", 0);
        builtinConstants.put("TIOCPKT_DOSTOP", 32);
        builtinConstants.put("TIOCPKT_FLUSHREAD", 1);
        builtinConstants.put("TIOCPKT_FLUSHWRITE", 2);
        builtinConstants.put("TIOCPKT_NOSTOP", 16);
        builtinConstants.put("TIOCPKT_START", 8);
        builtinConstants.put("TIOCPKT_STOP", 4);
        builtinConstants.put("TIOCSCTTY", 21518);
        builtinConstants.put("TIOCSERCONFIG", 21587);
        builtinConstants.put("TIOCSERGETLSR", 21593);
        builtinConstants.put("TIOCSERGETMULTI", 21594);
        builtinConstants.put("TIOCSERGSTRUCT", 21592);
        builtinConstants.put("TIOCSERGWILD", 21588);
        builtinConstants.put("TIOCSERSETMULTI", 21595);
        builtinConstants.put("TIOCSERSWILD", 21589);
        builtinConstants.put("TIOCSER_TEMT", 1);
        builtinConstants.put("TIOCSETD", 21539);
        builtinConstants.put("TIOCSLCKTRMIOS", 21591);
        builtinConstants.put("TIOCSPGRP", 21520);
        builtinConstants.put("TIOCSSERIAL", 21535);
        builtinConstants.put("TIOCSSOFTCAR", 21530);
        builtinConstants.put("TIOCSTI", 21522);
        builtinConstants.put("TIOCSWINSZ", 21524);
        builtinConstants.put("TOSTOP", 256);
        builtinConstants.put("VDISCARD", 13);
        builtinConstants.put("VEOF", 4);
        builtinConstants.put("VEOL", 11);
        builtinConstants.put("VEOL2", 16);
        builtinConstants.put("VERASE", 2);
        builtinConstants.put("VINTR", 0);
        builtinConstants.put("VKILL", 3);
        builtinConstants.put("VLNEXT", 15);
        builtinConstants.put("VMIN", 6);
        builtinConstants.put("VQUIT", 1);
        builtinConstants.put("VREPRINT", 12);
        builtinConstants.put("VSTART", 8);
        builtinConstants.put("VSTOP", 9);
        builtinConstants.put("VSUSP", 10);
        builtinConstants.put("VSWTC", 7);
        builtinConstants.put("VSWTCH", 7);
        builtinConstants.put("VT0", 0);
        builtinConstants.put("VT1", 16384);
        builtinConstants.put("VTDLY", 16384);
        builtinConstants.put("VTIME", 5);
        builtinConstants.put("VWERASE", 14);
        builtinConstants.put("XCASE", 4);
        builtinConstants.put("XTABS", 6144);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TermiosModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "tcdrain", minNumOfPositionalArgs = 1, parameterNames = {"fd"}, doc = "tcdrain(fd) -> None\n\n" +
                    "\tWait until all output written to file descriptor fd has been transmitted.")
    @GenerateNodeFactory
    abstract static class TCDrainNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone drain(Object fd) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "tcflow", minNumOfPositionalArgs = 2, parameterNames = {"fd", "action"}, doc = "tcflow(fd, action) -> None\n\n" +
                    "\tSuspend or resume input or output on file descriptor fd.\n" +
                    "\tThe action argument can be termios.TCOOFF to suspend output,\n" +
                    "\ttermios.TCOON to restart output, termios.TCIOFF to suspend input,\n" +
                    "\tor termios.TCION to restart input.")
    @GenerateNodeFactory
    abstract static class TCFlowNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone flow(Object fd, Object action) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "tcflush", minNumOfPositionalArgs = 2, parameterNames = {"fd", "queue"}, doc = "tcflush(fd, queue) -> None\n\n" +
                    "\tDiscard queued data on file descriptor fd.\n" +
                    "\tThe queue selector specifies which queue: termios.TCIFLUSH for the input\n" +
                    "\tqueue, termios.TCOFLUSH for the output queue, or termios.TCIOFLUSH for\n" +
                    "\tboth queues.")
    @GenerateNodeFactory
    abstract static class TCFlushNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone flow(Object fd, Object queue) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "tcgetattr", minNumOfPositionalArgs = 1, parameterNames = {"fd"}, doc = "tcgetattr(fd) -> list_of_attrs\n\n" +
                    "\tGet the tty attributes for file descriptor fd, as follows:\n" +
                    "\t[iflag, oflag, cflag, lflag, ispeed, ospeed, cc] where cc is a list\n" +
                    "\tof the tty special characters (each a string of length 1, except the items\n" +
                    "\twith indices VMIN and VTIME, which are integers when these fields are\n" +
                    "\tdefined).  The interpretation of the flags and the speeds as well as the\n" +
                    "\tindexing in the cc array must be done using the symbolic constants defined\n" +
                    "\tin this module.")
    @GenerateNodeFactory
    abstract static class TCGetAttrNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        PList flow(Object fd) {
            // Values taken from CPython 3.6.5 for the standard streams on Linux
            PythonObjectFactory f = factory();

            PList l = f.createList(new Object[]{
                            factory().createBytes(new byte[]{0x03}),
                            factory().createBytes(new byte[]{0x1c}),
                            factory().createBytes(new byte[]{0x7f}),
                            factory().createBytes(new byte[]{0x15}),
                            factory().createBytes(new byte[]{0x04}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x01}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x11}),
                            factory().createBytes(new byte[]{0x13}),
                            factory().createBytes(new byte[]{0x1a}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x12}),
                            factory().createBytes(new byte[]{0x0f}),
                            factory().createBytes(new byte[]{0x17}),
                            factory().createBytes(new byte[]{0x16}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00}),
                            factory().createBytes(new byte[]{0x00})
            });
            return f.createList(new Object[]{17664, 5, 191, 35387, 15, 15, l});
        }
    }

    @Builtin(name = "tcsendbreak", minNumOfPositionalArgs = 2, parameterNames = {"fd", "duration"}, doc = "tcsendbreak(fd, duration) -> None\n\n" +
                    "\tSend a break on file descriptor fd.\n" +
                    "\tA zero duration sends a break for 0.25-0.5 seconds; a nonzero duration\n" +
                    "\thas a system dependent meaning.")
    @GenerateNodeFactory
    abstract static class TCSendBreakNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone flow(Object fd, Object duration) {
            return PNone.NONE;
        }
    }

    @Builtin(name = "tcsetattr", minNumOfPositionalArgs = 2, parameterNames = {"fd", "when", "attributes"}, doc = "tcsetattr(fd, when, attributes) -> None\n\n" +
                    "\tSet the tty attributes for file descriptor fd.\n" +
                    "\tThe attributes to be set are taken from the attributes argument, which\n" +
                    "\tis a list like the one returned by tcgetattr(). The when argument\n" +
                    "\tdetermines when the attributes are changed: termios.TCSANOW to\n" +
                    "\tchange immediately, termios.TCSADRAIN to change after transmitting all\n" +
                    "\tqueued output, or termios.TCSAFLUSH to change after transmitting all\n" +
                    "\tqueued output and discarding all queued input.")
    @GenerateNodeFactory
    abstract static class TCSetAttrNode extends PythonBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization
        static PNone flow(Object fd, Object when, Object attributes) {
            return PNone.NONE;
        }
    }

}
