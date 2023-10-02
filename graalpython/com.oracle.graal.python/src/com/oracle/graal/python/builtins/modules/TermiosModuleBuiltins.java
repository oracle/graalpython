/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(defineModule = "termios", isEager = true)
public final class TermiosModuleBuiltins extends PythonBuiltins {

    public TermiosModuleBuiltins() {
        addBuiltinConstant(SpecialAttributeNames.T___DOC__,
                        "This module provides an interface to the Posix calls for tty I/O control.\n" +
                                        "For a complete description of these calls, see the Posix or Unix manual\n" +
                                        "pages. It is only available for those Unix versions that support Posix\n" +
                                        "termios style tty I/O control.\n\n" +
                                        "All functions in this module take a file descriptor fd as their first\n" +
                                        "argument. This can be an integer file descriptor, such as returned by\n" +
                                        "sys.stdin.fileno(), or a file object, such as sys.stdin itself.\n");

        // Note: copied from CPython 3.6.5
        addBuiltinConstant("B0", 0);
        addBuiltinConstant("B1000000", 4104);
        addBuiltinConstant("B110", 3);
        addBuiltinConstant("B115200", 4098);
        addBuiltinConstant("B1152000", 4105);
        addBuiltinConstant("B1200", 9);
        addBuiltinConstant("B134", 4);
        addBuiltinConstant("B150", 5);
        addBuiltinConstant("B1500000", 4106);
        addBuiltinConstant("B1800", 10);
        addBuiltinConstant("B19200", 14);
        addBuiltinConstant("B200", 6);
        addBuiltinConstant("B2000000", 4107);
        addBuiltinConstant("B230400", 4099);
        addBuiltinConstant("B2400", 11);
        addBuiltinConstant("B2500000", 4108);
        addBuiltinConstant("B300", 7);
        addBuiltinConstant("B3000000", 4109);
        addBuiltinConstant("B3500000", 4110);
        addBuiltinConstant("B38400", 15);
        addBuiltinConstant("B4000000", 4111);
        addBuiltinConstant("B460800", 4100);
        addBuiltinConstant("B4800", 12);
        addBuiltinConstant("B50", 1);
        addBuiltinConstant("B500000", 4101);
        addBuiltinConstant("B57600", 4097);
        addBuiltinConstant("B576000", 4102);
        addBuiltinConstant("B600", 8);
        addBuiltinConstant("B75", 2);
        addBuiltinConstant("B921600", 4103);
        addBuiltinConstant("B9600", 13);
        addBuiltinConstant("BRKINT", 2);
        addBuiltinConstant("BS0", 0);
        addBuiltinConstant("BS1", 8192);
        addBuiltinConstant("BSDLY", 8192);
        addBuiltinConstant("CBAUD", 4111);
        addBuiltinConstant("CBAUDEX", 4096);
        addBuiltinConstant("CDSUSP", 25);
        addBuiltinConstant("CEOF", 4);
        addBuiltinConstant("CEOL", 0);
        addBuiltinConstant("CEOT", 4);
        addBuiltinConstant("CERASE", 127);
        addBuiltinConstant("CFLUSH", 15);
        addBuiltinConstant("CIBAUD", 269418496);
        addBuiltinConstant("CINTR", 3);
        addBuiltinConstant("CKILL", 21);
        addBuiltinConstant("CLNEXT", 22);
        addBuiltinConstant("CLOCAL", 2048);
        addBuiltinConstant("CQUIT", 28);
        addBuiltinConstant("CR0", 0);
        addBuiltinConstant("CR1", 512);
        addBuiltinConstant("CR2", 1024);
        addBuiltinConstant("CR3", 1536);
        addBuiltinConstant("CRDLY", 1536);
        addBuiltinConstant("CREAD", 128);
        addBuiltinConstant("CRPRNT", 18);
        addBuiltinConstant("CRTSCTS", 2147483648L);
        addBuiltinConstant("CS5", 0);
        addBuiltinConstant("CS6", 16);
        addBuiltinConstant("CS7", 32);
        addBuiltinConstant("CS8", 48);
        addBuiltinConstant("CSIZE", 48);
        addBuiltinConstant("CSTART", 17);
        addBuiltinConstant("CSTOP", 19);
        addBuiltinConstant("CSTOPB", 64);
        addBuiltinConstant("CSUSP", 26);
        addBuiltinConstant("CWERASE", 23);
        addBuiltinConstant("ECHO", 8);
        addBuiltinConstant("ECHOCTL", 512);
        addBuiltinConstant("ECHOE", 16);
        addBuiltinConstant("ECHOK", 32);
        addBuiltinConstant("ECHOKE", 2048);
        addBuiltinConstant("ECHONL", 64);
        addBuiltinConstant("ECHOPRT", 1024);
        addBuiltinConstant("EXTA", 14);
        addBuiltinConstant("EXTB", 15);
        addBuiltinConstant("FF0", 0);
        addBuiltinConstant("FF1", 32768);
        addBuiltinConstant("FFDLY", 32768);
        addBuiltinConstant("FIOASYNC", 21586);
        addBuiltinConstant("FIOCLEX", 21585);
        addBuiltinConstant("FIONBIO", 21537);
        addBuiltinConstant("FIONCLEX", 21584);
        addBuiltinConstant("FIONREAD", 21531);
        addBuiltinConstant("FLUSHO", 4096);
        addBuiltinConstant("HUPCL", 1024);
        addBuiltinConstant("ICANON", 2);
        addBuiltinConstant("ICRNL", 256);
        addBuiltinConstant("IEXTEN", 32768);
        addBuiltinConstant("IGNBRK", 1);
        addBuiltinConstant("IGNCR", 128);
        addBuiltinConstant("IGNPAR", 4);
        addBuiltinConstant("IMAXBEL", 8192);
        addBuiltinConstant("INLCR", 64);
        addBuiltinConstant("INPCK", 16);
        addBuiltinConstant("IOCSIZE_MASK", 1073676288);
        addBuiltinConstant("IOCSIZE_SHIFT", 16);
        addBuiltinConstant("ISIG", 1);
        addBuiltinConstant("ISTRIP", 32);
        addBuiltinConstant("IUCLC", 512);
        addBuiltinConstant("IXANY", 2048);
        addBuiltinConstant("IXOFF", 4096);
        addBuiltinConstant("IXON", 1024);
        addBuiltinConstant("NCC", 8);
        addBuiltinConstant("NCCS", 32);
        addBuiltinConstant("NL0", 0);
        addBuiltinConstant("NL1", 256);
        addBuiltinConstant("NLDLY", 256);
        addBuiltinConstant("NOFLSH", 128);
        addBuiltinConstant("N_MOUSE", 2);
        addBuiltinConstant("N_PPP", 3);
        addBuiltinConstant("N_SLIP", 1);
        addBuiltinConstant("N_STRIP", 4);
        addBuiltinConstant("N_TTY", 0);
        addBuiltinConstant("OCRNL", 8);
        addBuiltinConstant("OFDEL", 128);
        addBuiltinConstant("OFILL", 64);
        addBuiltinConstant("OLCUC", 2);
        addBuiltinConstant("ONLCR", 4);
        addBuiltinConstant("ONLRET", 32);
        addBuiltinConstant("ONOCR", 16);
        addBuiltinConstant("OPOST", 1);
        addBuiltinConstant("PARENB", 256);
        addBuiltinConstant("PARMRK", 8);
        addBuiltinConstant("PARODD", 512);
        addBuiltinConstant("PENDIN", 16384);
        addBuiltinConstant("TAB0", 0);
        addBuiltinConstant("TAB1", 2048);
        addBuiltinConstant("TAB2", 4096);
        addBuiltinConstant("TAB3", 6144);
        addBuiltinConstant("TABDLY", 6144);
        addBuiltinConstant("TCFLSH", 21515);
        addBuiltinConstant("TCGETA", 21509);
        addBuiltinConstant("TCGETS", 21505);
        addBuiltinConstant("TCIFLUSH", 0);
        addBuiltinConstant("TCIOFF", 2);
        addBuiltinConstant("TCIOFLUSH", 2);
        addBuiltinConstant("TCION", 3);
        addBuiltinConstant("TCOFLUSH", 1);
        addBuiltinConstant("TCOOFF", 0);
        addBuiltinConstant("TCOON", 1);
        addBuiltinConstant("TCSADRAIN", 1);
        addBuiltinConstant("TCSAFLUSH", 2);
        addBuiltinConstant("TCSANOW", 0);
        addBuiltinConstant("TCSBRK", 21513);
        addBuiltinConstant("TCSBRKP", 21541);
        addBuiltinConstant("TCSETA", 21510);
        addBuiltinConstant("TCSETAF", 21512);
        addBuiltinConstant("TCSETAW", 21511);
        addBuiltinConstant("TCSETS", 21506);
        addBuiltinConstant("TCSETSF", 21508);
        addBuiltinConstant("TCSETSW", 21507);
        addBuiltinConstant("TCXONC", 21514);
        addBuiltinConstant("TIOCCONS", 21533);
        addBuiltinConstant("TIOCEXCL", 21516);
        addBuiltinConstant("TIOCGETD", 21540);
        addBuiltinConstant("TIOCGICOUNT", 21597);
        addBuiltinConstant("TIOCGLCKTRMIOS", 21590);
        addBuiltinConstant("TIOCGPGRP", 21519);
        addBuiltinConstant("TIOCGSERIAL", 21534);
        addBuiltinConstant("TIOCGSOFTCAR", 21529);
        addBuiltinConstant("TIOCGWINSZ", 21523);
        addBuiltinConstant("TIOCINQ", 21531);
        addBuiltinConstant("TIOCLINUX", 21532);
        addBuiltinConstant("TIOCMBIC", 21527);
        addBuiltinConstant("TIOCMBIS", 21526);
        addBuiltinConstant("TIOCMGET", 21525);
        addBuiltinConstant("TIOCMIWAIT", 21596);
        addBuiltinConstant("TIOCMSET", 21528);
        addBuiltinConstant("TIOCM_CAR", 64);
        addBuiltinConstant("TIOCM_CD", 64);
        addBuiltinConstant("TIOCM_CTS", 32);
        addBuiltinConstant("TIOCM_DSR", 256);
        addBuiltinConstant("TIOCM_DTR", 2);
        addBuiltinConstant("TIOCM_LE", 1);
        addBuiltinConstant("TIOCM_RI", 128);
        addBuiltinConstant("TIOCM_RNG", 128);
        addBuiltinConstant("TIOCM_RTS", 4);
        addBuiltinConstant("TIOCM_SR", 16);
        addBuiltinConstant("TIOCM_ST", 8);
        addBuiltinConstant("TIOCNOTTY", 21538);
        addBuiltinConstant("TIOCNXCL", 21517);
        addBuiltinConstant("TIOCOUTQ", 21521);
        addBuiltinConstant("TIOCPKT", 21536);
        addBuiltinConstant("TIOCPKT_DATA", 0);
        addBuiltinConstant("TIOCPKT_DOSTOP", 32);
        addBuiltinConstant("TIOCPKT_FLUSHREAD", 1);
        addBuiltinConstant("TIOCPKT_FLUSHWRITE", 2);
        addBuiltinConstant("TIOCPKT_NOSTOP", 16);
        addBuiltinConstant("TIOCPKT_START", 8);
        addBuiltinConstant("TIOCPKT_STOP", 4);
        addBuiltinConstant("TIOCSCTTY", 21518);
        addBuiltinConstant("TIOCSERCONFIG", 21587);
        addBuiltinConstant("TIOCSERGETLSR", 21593);
        addBuiltinConstant("TIOCSERGETMULTI", 21594);
        addBuiltinConstant("TIOCSERGSTRUCT", 21592);
        addBuiltinConstant("TIOCSERGWILD", 21588);
        addBuiltinConstant("TIOCSERSETMULTI", 21595);
        addBuiltinConstant("TIOCSERSWILD", 21589);
        addBuiltinConstant("TIOCSER_TEMT", 1);
        addBuiltinConstant("TIOCSETD", 21539);
        addBuiltinConstant("TIOCSLCKTRMIOS", 21591);
        addBuiltinConstant("TIOCSPGRP", 21520);
        addBuiltinConstant("TIOCSSERIAL", 21535);
        addBuiltinConstant("TIOCSSOFTCAR", 21530);
        addBuiltinConstant("TIOCSTI", 21522);
        addBuiltinConstant("TIOCSWINSZ", 21524);
        addBuiltinConstant("TOSTOP", 256);
        addBuiltinConstant("VDISCARD", 13);
        addBuiltinConstant("VEOF", 4);
        addBuiltinConstant("VEOL", 11);
        addBuiltinConstant("VEOL2", 16);
        addBuiltinConstant("VERASE", 2);
        addBuiltinConstant("VINTR", 0);
        addBuiltinConstant("VKILL", 3);
        addBuiltinConstant("VLNEXT", 15);
        addBuiltinConstant("VMIN", 6);
        addBuiltinConstant("VQUIT", 1);
        addBuiltinConstant("VREPRINT", 12);
        addBuiltinConstant("VSTART", 8);
        addBuiltinConstant("VSTOP", 9);
        addBuiltinConstant("VSUSP", 10);
        addBuiltinConstant("VSWTC", 7);
        addBuiltinConstant("VSWTCH", 7);
        addBuiltinConstant("VT0", 0);
        addBuiltinConstant("VT1", 16384);
        addBuiltinConstant("VTDLY", 16384);
        addBuiltinConstant("VTIME", 5);
        addBuiltinConstant("VWERASE", 14);
        addBuiltinConstant("XCASE", 4);
        addBuiltinConstant("XTABS", 6144);
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
        static PList flow(Object fd,
                        @Cached PythonObjectFactory factory) {
            // Values taken from CPython 3.6.5 for the standard streams on Linux

            PList l = factory.createList(new Object[]{
                            factory.createBytes(new byte[]{0x03}),
                            factory.createBytes(new byte[]{0x1c}),
                            factory.createBytes(new byte[]{0x7f}),
                            factory.createBytes(new byte[]{0x15}),
                            factory.createBytes(new byte[]{0x04}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x01}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x11}),
                            factory.createBytes(new byte[]{0x13}),
                            factory.createBytes(new byte[]{0x1a}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x12}),
                            factory.createBytes(new byte[]{0x0f}),
                            factory.createBytes(new byte[]{0x17}),
                            factory.createBytes(new byte[]{0x16}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00}),
                            factory.createBytes(new byte[]{0x00})
            });
            return factory.createList(new Object[]{17664, 5, 191, 35387, 15, 15, l});
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
