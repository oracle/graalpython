/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

public enum OSErrorEnum {

    /**
     * Generated using the following:
     *
     * <pre>
     * awk -F'\\s+'  '/#define/ { printf("%s(%s, \"",$2,$3); for (i=5; i<=NF-1; i++) {if(i==5){printf("%s",$i);}else{printf("%s%s",sep,$i);} sep=OFS}; print "\")," }' /usr/include/asm-generic/errno*
     * </pre>
     *
     * Manually are changed EWOULDBLOCK and EDEADLOCK and move before appropriate errors with the
     * same number, because ErrnoModuleBuiltins and PosixModuleBuiltins built a dictionary from this
     * enum.
     */
    EPERM(1, "Operation not permitted"),
    ENOENT(2, "No such file or directory"),
    ESRCH(3, "No such process"),
    EINTR(4, "Interrupted system call"),
    EIO(5, "I/O error"),
    ENXIO(6, "No such device or address"),
    E2BIG(7, "Argument list too long"),
    ENOEXEC(8, "Exec format error"),
    EBADF(9, "Bad file number"),
    ECHILD(10, "No child processes"),
    EWOULDBLOCK(platformSpecific(11, 35), "Operation would block"),
    EAGAIN(platformSpecific(11, 35), "Try again"),
    ENOMEM(12, "Out of memory"),
    EACCES(13, "Permission denied"),
    EFAULT(14, "Bad address"),
    ENOTBLK(15, "Block device required"),
    EBUSY(16, "Device or resource busy"),
    EEXIST(17, "File exists"),
    EXDEV(18, "Cross-device link"),
    ENODEV(19, "No such device"),
    ENOTDIR(20, "Not a directory"),
    EISDIR(21, "Is a directory"),
    EINVAL(22, "Invalid argument"),
    ENFILE(23, "File table overflow"),
    EMFILE(24, "Too many open files"),
    ENOTTY(25, "Not a typewriter"),
    ETXTBSY(26, "Text file busy"),
    EFBIG(27, "File too large"),
    ENOSPC(28, "No space left on device"),
    ESPIPE(29, "Illegal seek"),
    EROFS(30, "Read-only file system"),
    EMLINK(31, "Too many links"),
    EPIPE(32, "Broken pipe"),
    EDOM(33, "Math argument out of domain of func"),
    ERANGE(34, "Math result not representable"),
    EDEADLOCK(platformSpecific(35, 11)),
    EDEADLK(platformSpecific(35, 11), "Resource deadlock would occur"),
    ENAMETOOLONG(platformSpecific(36, 63), "File name too long"),
    ENOLCK(platformSpecific(37, 77), "No record locks available"),
    ENOSYS(platformSpecific(38, 78), "Invalid system call number"),
    ENOTEMPTY(platformSpecific(39, 66), "Directory not empty"),
    ELOOP(platformSpecific(40, 62), "Too many symbolic links encountered", "Too many levels of symbolic links"),
    ENOMSG(platformSpecific(42, 91), "No message of desired type"),
    EIDRM(platformSpecific(43, 90), "Identifier removed"),
    ECHRNG(44, "Channel number out of range"),
    EL2NSYNC(45, "Level 2 not synchronized"),
    EL3HLT(46, "Level 3 halted"),
    EL3RST(47, "Level 3 reset"),
    ELNRNG(48, "Link number out of range"),
    EUNATCH(49, "Protocol driver not attached"),
    ENOCSI(50, "No CSI structure available"),
    EL2HLT(51, "Level 2 halted"),
    EBADE(52, "Invalid exchange"),
    EBADR(platformSpecific(53, -1), "Invalid request descriptor"),
    EXFULL(platformSpecific(54, -1), "Exchange full"),
    ENOANO(55, "No anode"),
    EBADRQC(56, "Invalid request code"),
    EBADSLT(57, "Invalid slot"),
    EBFONT(59, "Bad font file format"),
    ENOSTR(platformSpecific(60, 99), "Device not a stream"),
    ENODATA(platformSpecific(61, 96), "No data available"),
    ETIME(platformSpecific(62, 101), "Timer expired"),
    ENOSR(platformSpecific(63, 98), "Out of streams resources"),
    ENONET(64, "Machine is not on the network"),
    ENOPKG(65, "Package not installed"),
    EREMOTE(platformSpecific(66, 71), "Object is remote"),
    ENOLINK(platformSpecific(67, 97), "Link has been severed"),
    EADV(68, "Advertise error"),
    ESRMNT(69, "Srmount error"),
    ECOMM(70, "Communication error on send"),
    EPROTO(platformSpecific(71, 100), "Protocol error"),
    EMULTIHOP(platformSpecific(72, 95), "Multihop attempted"),
    EDOTDOT(73, "RFS specific error"),
    EBADMSG(platformSpecific(74, 94), "Not a data message"),
    EOVERFLOW(platformSpecific(75, 84), "Value too large for defined data type"),
    ENOTUNIQ(76, "Name not unique on network"),
    EBADFD(77, "File descriptor in bad state"),
    EREMCHG(78, "Remote address changed"),
    ELIBACC(79, "Can not access a needed shared library"),
    ELIBBAD(80, "Accessing a corrupted shared library"),
    ELIBSCN(81, ".lib section in a.out corrupted"),
    ELIBMAX(82, "Attempting to link in too many shared libraries"),
    ELIBEXEC(83, "Cannot exec a shared library directly"),
    EILSEQ(platformSpecific(84, 92), "Illegal byte sequence"),
    ERESTART(85, "Interrupted system call should be restarted"),
    ESTRPIPE(86, "Streams pipe error"),
    EUSERS(platformSpecific(87, 68), "Too many users"),
    ENOTSOCK(platformSpecific(88, 38), "Socket operation on non-socket"),
    EDESTADDRREQ(platformSpecific(89, 39), "Destination address required"),
    EMSGSIZE(platformSpecific(90, 40), "Message too long"),
    EPROTOTYPE(platformSpecific(91, 41), "Protocol wrong type for socket"),
    ENOPROTOOPT(platformSpecific(92, 42), "Protocol not available"),
    EPROTONOSUPPORT(platformSpecific(93, 43), "Protocol not supported"),
    ESOCKTNOSUPPORT(platformSpecific(94, 44), "Socket type not supported"),
    EOPNOTSUPP(platformSpecific(95, 102), "Operation not supported on transport endpoint"),
    EPFNOSUPPORT(platformSpecific(96, 46), "Protocol family not supported"),
    EAFNOSUPPORT(platformSpecific(97, 47), "Address family not supported by protocol"),
    EADDRINUSE(platformSpecific(98, 48), "Address already in use"),
    EADDRNOTAVAIL(platformSpecific(99, 49), "Cannot assign requested address"),
    ENETDOWN(platformSpecific(100, 50), "Network is down"),
    ENETUNREACH(platformSpecific(101, 51), "Network is unreachable"),
    ENETRESET(platformSpecific(102, 52), "Network dropped connection because of reset"),
    ECONNABORTED(platformSpecific(103, 53), "Software caused connection abort"),
    ECONNRESET(platformSpecific(104, 54), "Connection reset by peer"),
    ENOBUFS(platformSpecific(105, 55), "No buffer space available"),
    EISCONN(platformSpecific(106, 56), "Transport endpoint is already connected"),
    ENOTCONN(platformSpecific(107, 57), "Transport endpoint is not connected"),
    ESHUTDOWN(platformSpecific(108, 58), "Cannot send after transport endpoint shutdown"),
    ETOOMANYREFS(platformSpecific(109, 59), "Too many references: cannot splice"),
    ETIMEDOUT(platformSpecific(110, 60), "Connection timed out"),
    ECONNREFUSED(platformSpecific(111, 61), "Connection refused"),
    EHOSTDOWN(platformSpecific(112, 64), "Host is down"),
    EHOSTUNREACH(platformSpecific(113, 65), "No route to host"),
    EALREADY(platformSpecific(114, 37), "Operation already in progress"),
    EINPROGRESS(platformSpecific(115, 36), "Operation now in progress"),
    ESTALE(platformSpecific(116, 70), "Stale file handle"),
    EUCLEAN(117, "Structure needs cleaning"),
    ENOTNAM(118, "Not a XENIX named type file"),
    ENAVAIL(119, "No XENIX semaphores available"),
    EISNAM(120, "Is a named type file"),
    EREMOTEIO(121, "Remote I/O error"),
    EDQUOT(platformSpecific(122, 69), "Quota exceeded"),
    ENOMEDIUM(123, "No medium found"),
    EMEDIUMTYPE(124, "Wrong medium type"),
    ECANCELED(platformSpecific(125, 89), "Operation Canceled"),
    ENOKEY(126, "Required key not available"),
    EKEYEXPIRED(127, "Key has expired"),
    EKEYREVOKED(128, "Key has been revoked"),
    EKEYREJECTED(129, "Key was rejected by service"),
    EOWNERDEAD(platformSpecific(130, 105), "Owner died"),
    ENOTRECOVERABLE(platformSpecific(131, 104), "State not recoverable"),
    ERFKILL(132, "Operation not possible due to RF-kill"),
    EHWPOISON(133, "Memory page has hardware error");

    private final String message;
    private final String[] alternativeMessages;
    private final int number; // there can be more errors with the same number

    OSErrorEnum(int number, String message, String... alternativeMessages) {
        this.number = number;
        this.message = message;
        this.alternativeMessages = alternativeMessages;
    }

    OSErrorEnum(int number, String message) {
        this(number, message, PythonUtils.EMPTY_STRING_ARRAY);
    }

    OSErrorEnum(int number) {
        this(number, null);
    }

    public String getMessage() {
        return message;
    }

    public int getNumber() {
        return number;
    }

    @TruffleBoundary
    public static OSErrorEnum fromMessage(String message) {
        for (OSErrorEnum oserror : values()) {
            if (message.equals(oserror.getMessage())) {
                return oserror;
            }
            for (String altMessage : oserror.alternativeMessages) {
                if (message.equals(altMessage)) {
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

    public static ErrorAndMessagePair fromException(Exception e) {
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
                String reason = getReason((FileSystemException) e);
                OSErrorEnum oserror = OSErrorEnum.fromMessage(reason);
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
            throw new RuntimeException(getMessage(e), e);
        }
    }

    private static final Pattern ERRNO_PATTERN = Pattern.compile("error=(\\d+)");

    @TruffleBoundary
    private static String getMessage(Exception e) {
        return e.getMessage();
    }

    @TruffleBoundary
    private static String getReason(FileSystemException e) {
        return e.getReason();
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
        public final String message;

        public ErrorAndMessagePair(OSErrorEnum oserror, String message) {
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
