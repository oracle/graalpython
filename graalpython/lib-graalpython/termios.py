# Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# The Universal Permissive License (UPL), Version 1.0
#
# Subject to the condition set forth below, permission is hereby granted to any
# person obtaining a copy of this software, associated documentation and/or
# data (collectively the "Software"), free of charge and under any and all
# copyright rights in the Software, and any and all patent rights owned or
# freely licensable by each licensor hereunder covering either (i) the
# unmodified Software as contributed to or provided by such licensor, or (ii)
# the Larger Works (as defined below), to deal in both
#
# (a) the Software, and
#
# (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
# one is included with the Software each a "Larger Work" to which the Software
# is contributed by such licensors),
#
# without restriction, including without limitation the rights to copy, create
# derivative works of, display, perform, and distribute the Software and make,
# use, sell, offer for sale, import, export, have made, and have sold the
# Software and the Larger Work(s), and to sublicense the foregoing rights on
# either these or other terms.
#
# This license is subject to the following condition:
#
# The above copyright notice and either this complete permission notice or at a
# minimum a reference to the UPL must be included in all copies or substantial
# portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
# SOFTWARE.


__doc__ = """
This module provides an interface to the Posix calls for tty I/O control.
For a complete description of these calls, see the Posix or Unix manual
pages. It is only available for those Unix versions that support Posix
termios style tty I/O control.

All functions in this module take a file descriptor fd as their first
argument. This can be an integer file descriptor, such as returned by
sys.stdin.fileno(), or a file object, such as sys.stdin itself.
"""


def tcdrain(fd):
    """
    tcdrain(fd) -> None

    Wait until all output written to file descriptor fd has been transmitted.
    """


def tcflow(fd, action):
    """
    tcflow(fd, action) -> None

    Suspend or resume input or output on file descriptor fd.
    The action argument can be termios.TCOOFF to suspend output,
    termios.TCOON to restart output, termios.TCIOFF to suspend input,
    or termios.TCION to restart input.
    """


def tcflush(fd, queue):
    """
    tcflush(fd, queue) -> None

    Discard queued data on file descriptor fd.
    The queue selector specifies which queue: termios.TCIFLUSH for the input
    queue, termios.TCOFLUSH for the output queue, or termios.TCIOFLUSH for
    both queues.
    """


def tcgetattr(fd):
    """
    tcgetattr(fd) -> list_of_attrs

    Get the tty attributes for file descriptor fd, as follows:
    [iflag, oflag, cflag, lflag, ispeed, ospeed, cc] where cc is a list
    of the tty special characters (each a string of length 1, except the items
    with indices VMIN and VTIME, which are integers when these fields are
    defined).  The interpretation of the flags and the speeds as well as the
    indexing in the cc array must be done using the symbolic constants defined
    in this module.
    """
    # Values taken from CPython 3.6.5 for the standard streams on Linux
    return [
        17664,
        5,
        191,
        35387,
        15,
        15,
        [b'\x03', b'\x1c', b'\x7f', b'\x15',
         b'\x04', b'\x00', b'\x01', b'\x00', b'\x11', b'\x13', b'\x1a',
         b'\x00', b'\x12', b'\x0f', b'\x17', b'\x16', b'\x00', b'\x00',
         b'\x00', b'\x00', b'\x00', b'\x00', b'\x00', b'\x00', b'\x00',
         b'\x00', b'\x00', b'\x00', b'\x00', b'\x00', b'\x00', b'\x00']
    ]


def tcsendbreak(fd, duration):
    """
    tcsendbreak(fd, duration) -> None

    Send a break on file descriptor fd.
    A zero duration sends a break for 0.25-0.5 seconds; a nonzero duration
    has a system dependent meaning.
    """


def tcsetattr(fd, when, attributes):
    """
    tcsetattr(fd, when, attributes) -> None

    Set the tty attributes for file descriptor fd.
    The attributes to be set are taken from the attributes argument, which
    is a list like the one returned by tcgetattr(). The when argument
    determines when the attributes are changed: termios.TCSANOW to
    change immediately, termios.TCSADRAIN to change after transmitting all
    queued output, or termios.TCSAFLUSH to change after transmitting all
    queued output and discarding all queued input.
    """


# Note: copied from CPython 3.6.5
B0 = 0
B1000000 = 4104
B110 = 3
B115200 = 4098
B1152000 = 4105
B1200 = 9
B134 = 4
B150 = 5
B1500000 = 4106
B1800 = 10
B19200 = 14
B200 = 6
B2000000 = 4107
B230400 = 4099
B2400 = 11
B2500000 = 4108
B300 = 7
B3000000 = 4109
B3500000 = 4110
B38400 = 15
B4000000 = 4111
B460800 = 4100
B4800 = 12
B50 = 1
B500000 = 4101
B57600 = 4097
B576000 = 4102
B600 = 8
B75 = 2
B921600 = 4103
B9600 = 13
BRKINT = 2
BS0 = 0
BS1 = 8192
BSDLY = 8192
CBAUD = 4111
CBAUDEX = 4096
CDSUSP = 25
CEOF = 4
CEOL = 0
CEOT = 4
CERASE = 127
CFLUSH = 15
CIBAUD = 269418496
CINTR = 3
CKILL = 21
CLNEXT = 22
CLOCAL = 2048
CQUIT = 28
CR0 = 0
CR1 = 512
CR2 = 1024
CR3 = 1536
CRDLY = 1536
CREAD = 128
CRPRNT = 18
CRTSCTS = 2147483648
CS5 = 0
CS6 = 16
CS7 = 32
CS8 = 48
CSIZE = 48
CSTART = 17
CSTOP = 19
CSTOPB = 64
CSUSP = 26
CWERASE = 23
ECHO = 8
ECHOCTL = 512
ECHOE = 16
ECHOK = 32
ECHOKE = 2048
ECHONL = 64
ECHOPRT = 1024
EXTA = 14
EXTB = 15
FF0 = 0
FF1 = 32768
FFDLY = 32768
FIOASYNC = 21586
FIOCLEX = 21585
FIONBIO = 21537
FIONCLEX = 21584
FIONREAD = 21531
FLUSHO = 4096
HUPCL = 1024
ICANON = 2
ICRNL = 256
IEXTEN = 32768
IGNBRK = 1
IGNCR = 128
IGNPAR = 4
IMAXBEL = 8192
INLCR = 64
INPCK = 16
IOCSIZE_MASK = 1073676288
IOCSIZE_SHIFT = 16
ISIG = 1
ISTRIP = 32
IUCLC = 512
IXANY = 2048
IXOFF = 4096
IXON = 1024
NCC = 8
NCCS = 32
NL0 = 0
NL1 = 256
NLDLY = 256
NOFLSH = 128
N_MOUSE = 2
N_PPP = 3
N_SLIP = 1
N_STRIP = 4
N_TTY = 0
OCRNL = 8
OFDEL = 128
OFILL = 64
OLCUC = 2
ONLCR = 4
ONLRET = 32
ONOCR = 16
OPOST = 1
PARENB = 256
PARMRK = 8
PARODD = 512
PENDIN = 16384
TAB0 = 0
TAB1 = 2048
TAB2 = 4096
TAB3 = 6144
TABDLY = 6144
TCFLSH = 21515
TCGETA = 21509
TCGETS = 21505
TCIFLUSH = 0
TCIOFF = 2
TCIOFLUSH = 2
TCION = 3
TCOFLUSH = 1
TCOOFF = 0
TCOON = 1
TCSADRAIN = 1
TCSAFLUSH = 2
TCSANOW = 0
TCSBRK = 21513
TCSBRKP = 21541
TCSETA = 21510
TCSETAF = 21512
TCSETAW = 21511
TCSETS = 21506
TCSETSF = 21508
TCSETSW = 21507
TCXONC = 21514
TIOCCONS = 21533
TIOCEXCL = 21516
TIOCGETD = 21540
TIOCGICOUNT = 21597
TIOCGLCKTRMIOS = 21590
TIOCGPGRP = 21519
TIOCGSERIAL = 21534
TIOCGSOFTCAR = 21529
TIOCGWINSZ = 21523
TIOCINQ = 21531
TIOCLINUX = 21532
TIOCMBIC = 21527
TIOCMBIS = 21526
TIOCMGET = 21525
TIOCMIWAIT = 21596
TIOCMSET = 21528
TIOCM_CAR = 64
TIOCM_CD = 64
TIOCM_CTS = 32
TIOCM_DSR = 256
TIOCM_DTR = 2
TIOCM_LE = 1
TIOCM_RI = 128
TIOCM_RNG = 128
TIOCM_RTS = 4
TIOCM_SR = 16
TIOCM_ST = 8
TIOCNOTTY = 21538
TIOCNXCL = 21517
TIOCOUTQ = 21521
TIOCPKT = 21536
TIOCPKT_DATA = 0
TIOCPKT_DOSTOP = 32
TIOCPKT_FLUSHREAD = 1
TIOCPKT_FLUSHWRITE = 2
TIOCPKT_NOSTOP = 16
TIOCPKT_START = 8
TIOCPKT_STOP = 4
TIOCSCTTY = 21518
TIOCSERCONFIG = 21587
TIOCSERGETLSR = 21593
TIOCSERGETMULTI = 21594
TIOCSERGSTRUCT = 21592
TIOCSERGWILD = 21588
TIOCSERSETMULTI = 21595
TIOCSERSWILD = 21589
TIOCSER_TEMT = 1
TIOCSETD = 21539
TIOCSLCKTRMIOS = 21591
TIOCSPGRP = 21520
TIOCSSERIAL = 21535
TIOCSSOFTCAR = 21530
TIOCSTI = 21522
TIOCSWINSZ = 21524
TOSTOP = 256
VDISCARD = 13
VEOF = 4
VEOL = 11
VEOL2 = 16
VERASE = 2
VINTR = 0
VKILL = 3
VLNEXT = 15
VMIN = 6
VQUIT = 1
VREPRINT = 12
VSTART = 8
VSTOP = 9
VSUSP = 10
VSWTC = 7
VSWTCH = 7
VT0 = 0
VT1 = 16384
VTDLY = 16384
VTIME = 5
VWERASE = 14
XCASE = 4
XTABS = 6144


def init_module():
    import sys
    sys.modules["termios"] = type(sys)("termios")
    sys.modules["termios"].__dict__.update(globals())


init_module()
