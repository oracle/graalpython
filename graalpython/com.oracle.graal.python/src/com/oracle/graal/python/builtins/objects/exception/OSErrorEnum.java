/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.exception;

import static com.oracle.graal.python.builtins.PythonOS.PLATFORM_DARWIN;
import static com.oracle.graal.python.builtins.PythonOS.getPythonOS;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.NotYetConnectedException;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.NotLinkException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.strings.TruffleString;

public enum OSErrorEnum {

    /**
     * Generated using the following:
     *
     * <pre>
     * awk -F'\\s+'  '/#define/ { printf("%s(%s, \"",$2,$3); for (i=5; i<=NF-1; i++) {if(i==5){printf("%s",$i);}else{printf("%s%s",sep,$i);} sep=OFS}; print "\"))," }' /usr/include/asm-generic/errno*
     * </pre>
     *
     * Manually are changed EWOULDBLOCK and EDEADLOCK and move before appropriate errors with the
     * same number, because ErrnoModuleBuiltins and PosixModuleBuiltins built a dictionary from this
     * enum.
     */
    EPERM(1, tsLiteral("Operation not permitted")),
    ENOENT(2, tsLiteral("No such file or directory")),
    ESRCH(3, tsLiteral("No such process")),
    EINTR(4, tsLiteral("Interrupted system call")),
    EIO(5, tsLiteral("I/O error")),
    ENXIO(6, tsLiteral("No such device or address")),
    E2BIG(7, tsLiteral("Argument list too long")),
    ENOEXEC(8, tsLiteral("Exec format error")),
    EBADF(9, tsLiteral("Bad file number")),
    ECHILD(10, tsLiteral("No child processes")),
    EWOULDBLOCK(platformSpecific(11, 35), tsLiteral("Operation would block")),
    EAGAIN(platformSpecific(11, 35), tsLiteral("Try again")),
    ENOMEM(12, tsLiteral("Out of memory")),
    EACCES(13, tsLiteral("Permission denied")),
    EFAULT(14, tsLiteral("Bad address")),
    ENOTBLK(15, tsLiteral("Block device required")),
    EBUSY(16, tsLiteral("Device or resource busy")),
    EEXIST(17, tsLiteral("File exists")),
    EXDEV(18, tsLiteral("Cross-device link")),
    ENODEV(19, tsLiteral("No such device")),
    ENOTDIR(20, tsLiteral("Not a directory")),
    EISDIR(21, tsLiteral("Is a directory")),
    EINVAL(22, tsLiteral("Invalid argument")),
    ENFILE(23, tsLiteral("File table overflow")),
    EMFILE(24, tsLiteral("Too many open files")),
    ENOTTY(25, tsLiteral("Not a typewriter")),
    ETXTBSY(26, tsLiteral("Text file busy")),
    EFBIG(27, tsLiteral("File too large")),
    ENOSPC(28, tsLiteral("No space left on device")),
    ESPIPE(29, tsLiteral("Illegal seek")),
    EROFS(30, tsLiteral("Read-only file system")),
    EMLINK(31, tsLiteral("Too many links")),
    EPIPE(32, tsLiteral("Broken pipe")),
    EDOM(33, tsLiteral("Math argument out of domain of func")),
    ERANGE(34, tsLiteral("Math result not representable")),
    EDEADLOCK(platformSpecific(35, 11)),
    EDEADLK(platformSpecific(35, 11), tsLiteral("Resource deadlock would occur")),
    ENAMETOOLONG(platformSpecific(36, 63), tsLiteral("File name too long")),
    ENOLCK(platformSpecific(37, 77), tsLiteral("No record locks available")),
    ENOSYS(platformSpecific(38, 78), tsLiteral("Invalid system call number")),
    ENOTEMPTY(platformSpecific(39, 66), tsLiteral("Directory not empty")),
    ELOOP(platformSpecific(40, 62), tsLiteral("Too many symbolic links encountered"), tsLiteral("Too many levels of symbolic links")),
    ENOMSG(platformSpecific(42, 91), tsLiteral("No message of desired type")),
    EIDRM(platformSpecific(43, 90), tsLiteral("Identifier removed")),
    ECHRNG(44, tsLiteral("Channel number out of range")),
    EL2NSYNC(45, tsLiteral("Level 2 not synchronized")),
    EL3HLT(46, tsLiteral("Level 3 halted")),
    EL3RST(47, tsLiteral("Level 3 reset")),
    ELNRNG(48, tsLiteral("Link number out of range")),
    EUNATCH(49, tsLiteral("Protocol driver not attached")),
    ENOCSI(50, tsLiteral("No CSI structure available")),
    EL2HLT(51, tsLiteral("Level 2 halted")),
    EBADE(52, tsLiteral("Invalid exchange")),
    EBADR(platformSpecific(53, -1), tsLiteral("Invalid request descriptor")),
    EXFULL(platformSpecific(54, -1), tsLiteral("Exchange full")),
    ENOANO(55, tsLiteral("No anode")),
    EBADRQC(56, tsLiteral("Invalid request code")),
    EBADSLT(57, tsLiteral("Invalid slot")),
    EBFONT(59, tsLiteral("Bad font file format")),
    ENOSTR(platformSpecific(60, 99), tsLiteral("Device not a stream")),
    ENODATA(platformSpecific(61, 96), tsLiteral("No data available")),
    ETIME(platformSpecific(62, 101), tsLiteral("Timer expired")),
    ENOSR(platformSpecific(63, 98), tsLiteral("Out of streams resources")),
    ENONET(64, tsLiteral("Machine is not on the network")),
    ENOPKG(65, tsLiteral("Package not installed")),
    EREMOTE(platformSpecific(66, 71), tsLiteral("Object is remote")),
    ENOLINK(platformSpecific(67, 97), tsLiteral("Link has been severed")),
    EADV(68, tsLiteral("Advertise error")),
    ESRMNT(69, tsLiteral("Srmount error")),
    ECOMM(70, tsLiteral("Communication error on send")),
    EPROTO(platformSpecific(71, 100), tsLiteral("Protocol error")),
    EMULTIHOP(platformSpecific(72, 95), tsLiteral("Multihop attempted")),
    EDOTDOT(73, tsLiteral("RFS specific error")),
    EBADMSG(platformSpecific(74, 94), tsLiteral("Not a data message")),
    EOVERFLOW(platformSpecific(75, 84), tsLiteral("Value too large for defined data type")),
    ENOTUNIQ(76, tsLiteral("Name not unique on network")),
    EBADFD(77, tsLiteral("File descriptor in bad state")),
    EREMCHG(78, tsLiteral("Remote address changed")),
    ELIBACC(79, tsLiteral("Can not access a needed shared library")),
    ELIBBAD(80, tsLiteral("Accessing a corrupted shared library")),
    ELIBSCN(81, tsLiteral(".lib section in a.out corrupted")),
    ELIBMAX(82, tsLiteral("Attempting to link in too many shared libraries")),
    ELIBEXEC(83, tsLiteral("Cannot exec a shared library directly")),
    EILSEQ(platformSpecific(84, 92), tsLiteral("Illegal byte sequence")),
    ERESTART(85, tsLiteral("Interrupted system call should be restarted")),
    ESTRPIPE(86, tsLiteral("Streams pipe error")),
    EUSERS(platformSpecific(87, 68), tsLiteral("Too many users")),
    ENOTSOCK(platformSpecific(88, 38), tsLiteral("Socket operation on non-socket")),
    EDESTADDRREQ(platformSpecific(89, 39), tsLiteral("Destination address required")),
    EMSGSIZE(platformSpecific(90, 40), tsLiteral("Message too long")),
    EPROTOTYPE(platformSpecific(91, 41), tsLiteral("Protocol wrong type for socket")),
    ENOPROTOOPT(platformSpecific(92, 42), tsLiteral("Protocol not available")),
    EPROTONOSUPPORT(platformSpecific(93, 43), tsLiteral("Protocol not supported")),
    ESOCKTNOSUPPORT(platformSpecific(94, 44), tsLiteral("Socket type not supported")),
    EOPNOTSUPP(platformSpecific(95, 102), tsLiteral("Operation not supported on transport endpoint")),
    EPFNOSUPPORT(platformSpecific(96, 46), tsLiteral("Protocol family not supported")),
    EAFNOSUPPORT(platformSpecific(97, 47), tsLiteral("Address family not supported by protocol")),
    EADDRINUSE(platformSpecific(98, 48), tsLiteral("Address already in use")),
    EADDRNOTAVAIL(platformSpecific(99, 49), tsLiteral("Cannot assign requested address")),
    ENETDOWN(platformSpecific(100, 50), tsLiteral("Network is down")),
    ENETUNREACH(platformSpecific(101, 51), tsLiteral("Network is unreachable")),
    ENETRESET(platformSpecific(102, 52), tsLiteral("Network dropped connection because of reset")),
    ECONNABORTED(platformSpecific(103, 53), tsLiteral("Software caused connection abort")),
    ECONNRESET(platformSpecific(104, 54), tsLiteral("Connection reset by peer")),
    ENOBUFS(platformSpecific(105, 55), tsLiteral("No buffer space available")),
    EISCONN(platformSpecific(106, 56), tsLiteral("Transport endpoint is already connected")),
    ENOTCONN(platformSpecific(107, 57), tsLiteral("Transport endpoint is not connected")),
    ESHUTDOWN(platformSpecific(108, 58), tsLiteral("Cannot send after transport endpoint shutdown")),
    ETOOMANYREFS(platformSpecific(109, 59), tsLiteral("Too many references: cannot splice")),
    ETIMEDOUT(platformSpecific(110, 60), tsLiteral("Connection timed out")),
    ECONNREFUSED(platformSpecific(111, 61), tsLiteral("Connection refused")),
    EHOSTDOWN(platformSpecific(112, 64), tsLiteral("Host is down")),
    EHOSTUNREACH(platformSpecific(113, 65), tsLiteral("No route to host")),
    EALREADY(platformSpecific(114, 37), tsLiteral("Operation already in progress")),
    EINPROGRESS(platformSpecific(115, 36), tsLiteral("Operation now in progress")),
    ESTALE(platformSpecific(116, 70), tsLiteral("Stale file handle")),
    EUCLEAN(117, tsLiteral("Structure needs cleaning")),
    ENOTNAM(118, tsLiteral("Not a XENIX named type file")),
    ENAVAIL(119, tsLiteral("No XENIX semaphores available")),
    EISNAM(120, tsLiteral("Is a named type file")),
    EREMOTEIO(121, tsLiteral("Remote I/O error")),
    EDQUOT(platformSpecific(122, 69), tsLiteral("Quota exceeded")),
    ENOMEDIUM(123, tsLiteral("No medium found")),
    EMEDIUMTYPE(124, tsLiteral("Wrong medium type")),
    ECANCELED(platformSpecific(125, 89), tsLiteral("Operation Canceled")),
    ENOKEY(126, tsLiteral("Required key not available")),
    EKEYEXPIRED(127, tsLiteral("Key has expired")),
    EKEYREVOKED(128, tsLiteral("Key has been revoked")),
    EKEYREJECTED(129, tsLiteral("Key was rejected by service")),
    EOWNERDEAD(platformSpecific(130, 105), tsLiteral("Owner died")),
    ENOTRECOVERABLE(platformSpecific(131, 104), tsLiteral("State not recoverable")),
    ERFKILL(132, tsLiteral("Operation not possible due to RF-kill")),
    EHWPOISON(133, tsLiteral("Memory page has hardware error"));

    private final TruffleString message;
    private final TruffleString[] alternativeMessages;
    private final int number; // there can be more errors with the same number

    OSErrorEnum(int number, TruffleString message, TruffleString... alternativeMessages) {
        this.number = number;
        this.message = message != null ? message : null;
        this.alternativeMessages = new TruffleString[alternativeMessages.length];
        for (int i = 0; i < alternativeMessages.length; i++) {
            this.alternativeMessages[i] = alternativeMessages[i];
        }
    }

    OSErrorEnum(int number, TruffleString message) {
        this(number, message, PythonUtils.EMPTY_TRUFFLESTRING_ARRAY);
    }

    OSErrorEnum(int number) {
        this(number, null);
    }

    public TruffleString getMessage() {
        return message;
    }

    public int getNumber() {
        return number;
    }

    @TruffleBoundary
    private static OSErrorEnum fromMessage(TruffleString message, TruffleString.EqualNode eqNode) {
        if (message == null) {
            return null;
        }
        for (OSErrorEnum oserror : values()) {
            if (oserror.getMessage() == null) {
                continue;
            }
            if (eqNode.execute(message, oserror.getMessage(), TS_ENCODING)) {
                return oserror;
            }
            for (TruffleString altMessage : oserror.alternativeMessages) {
                if (altMessage == null) {
                    continue;
                }
                if (eqNode.execute(message, altMessage, TS_ENCODING)) {
                    return oserror;
                }
            }
        }
        return null;
    }

    @TruffleBoundary
    public static OSErrorEnum fromNumber(int number) {
        OSErrorEnum[] values = values();
        for (int i = 0; i < values.length; i++) {
            if (values[i].getNumber() == number) {
                return values[i];
            }
        }
        return null;
    }

    public static ErrorAndMessagePair fromException(Exception e, TruffleString.EqualNode eqNode) {
        if (e instanceof IOException) {
            if (e instanceof NoSuchFileException || e instanceof FileNotFoundException) {
                return new ErrorAndMessagePair(OSErrorEnum.ENOENT, OSErrorEnum.ENOENT.getMessage());
            } else if (e instanceof AccessDeniedException) {
                return new ErrorAndMessagePair(OSErrorEnum.EACCES, OSErrorEnum.EACCES.getMessage());
            } else if (e instanceof FileAlreadyExistsException) {
                return new ErrorAndMessagePair(OSErrorEnum.EEXIST, OSErrorEnum.EEXIST.getMessage());
            } else if (e instanceof NotDirectoryException) {
                return new ErrorAndMessagePair(OSErrorEnum.ENOTDIR, OSErrorEnum.ENOTDIR.getMessage());
            } else if (e instanceof DirectoryNotEmptyException) {
                return new ErrorAndMessagePair(OSErrorEnum.ENOTEMPTY, OSErrorEnum.ENOTEMPTY.getMessage());
            } else if (e instanceof FileSystemLoopException) {
                return new ErrorAndMessagePair(OSErrorEnum.ELOOP, OSErrorEnum.ELOOP.getMessage());
            } else if (e instanceof NotLinkException) {
                return new ErrorAndMessagePair(OSErrorEnum.EINVAL, OSErrorEnum.EINVAL.getMessage());
            } else if (e instanceof ClosedChannelException) {
                return new ErrorAndMessagePair(OSErrorEnum.EPIPE, OSErrorEnum.EPIPE.getMessage());
            } else if (e instanceof FileSystemException) {
                // Unfortunately we don't have any better means of getting the error code than
                // matching the error message. This is an issue if the Python code checks for
                // specific error numbers like in the glob module: failing to match the correct
                // error number of 40 (link loop) breaks that module's code
                TruffleString reason = getReason((FileSystemException) e);
                OSErrorEnum oserror = OSErrorEnum.fromMessage(reason, eqNode);
                if (oserror == null) {
                    return new ErrorAndMessagePair(OSErrorEnum.EIO, reason);
                } else {
                    return new ErrorAndMessagePair(oserror, oserror.getMessage());
                }
            } else { // Generic IOException
                OSErrorEnum oserror = tryFindErrnoFromMessage(e);
                if (oserror == null) {
                    return new ErrorAndMessagePair(OSErrorEnum.EIO, getMessage(e));
                } else {
                    return new ErrorAndMessagePair(oserror, oserror.getMessage());
                }
            }
        } else if (e instanceof SecurityException) {
            return new ErrorAndMessagePair(OSErrorEnum.EPERM, OSErrorEnum.EPERM.getMessage());
        } else if (e instanceof IllegalArgumentException) {
            return new ErrorAndMessagePair(OSErrorEnum.EINVAL, OSErrorEnum.EINVAL.getMessage());
        } else if (e instanceof UnsupportedOperationException) {
            return new ErrorAndMessagePair(OSErrorEnum.EOPNOTSUPP, OSErrorEnum.EOPNOTSUPP.getMessage());
        } else if (e instanceof NonReadableChannelException || e instanceof NonWritableChannelException) {
            return new ErrorAndMessagePair(OSErrorEnum.EBADF, OSErrorEnum.EBADF.getMessage());
        } else if (e instanceof OperationWouldBlockException) {
            return new ErrorAndMessagePair(OSErrorEnum.EWOULDBLOCK, OSErrorEnum.EWOULDBLOCK.getMessage());
        } else if (e instanceof NotYetConnectedException) {
            // TODO for UDP send without connect it should probably be EDESTADDRREQ
            return new ErrorAndMessagePair(OSErrorEnum.ENOTCONN, OSErrorEnum.ENOTCONN.getMessage());
        } else if (e instanceof AlreadyConnectedException) {
            return new ErrorAndMessagePair(OSErrorEnum.EISCONN, OSErrorEnum.EISCONN.getMessage());
        } else if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw toRuntimeException(e);
        }
    }

    private static final Pattern ERRNO_PATTERN = Pattern.compile("error=(\\d+)");

    @TruffleBoundary
    private static TruffleString getMessage(Exception e) {
        return toTruffleStringUncached(e.getMessage());
    }

    @TruffleBoundary
    private static RuntimeException toRuntimeException(Exception e) {
        return new RuntimeException(e.getMessage(), e);
    }

    @TruffleBoundary
    private static TruffleString getReason(FileSystemException e) {
        return toTruffleStringUncached(e.getReason());
    }

    @TruffleBoundary
    private static OSErrorEnum tryFindErrnoFromMessage(Exception e) {
        if (e.getMessage().contains("Broken pipe")) {
            return OSErrorEnum.EPIPE;
        }
        Matcher m = ERRNO_PATTERN.matcher(e.getMessage());
        if (m.find()) {
            return fromNumber(Integer.parseInt(m.group(1)));
        }
        return null;
    }

    @ValueType
    public static final class ErrorAndMessagePair {
        public final OSErrorEnum oserror;
        public final TruffleString message;

        public ErrorAndMessagePair(OSErrorEnum oserror, TruffleString message) {
            this.oserror = oserror;
            this.message = message;
        }
    }

    private static int platformSpecific(int linuxValue, int darwinValue) {
        return getPythonOS() == PLATFORM_DARWIN ? darwinValue : linuxValue;
    }

    public static class OperationWouldBlockException extends IllegalStateException {
        private static final long serialVersionUID = -6947337041526311362L;
    }
}
