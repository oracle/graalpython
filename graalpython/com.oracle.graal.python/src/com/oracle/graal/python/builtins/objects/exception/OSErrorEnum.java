/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

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
    EWOULDBLOCK(11, "Operation would block"),
    EAGAIN(11, "Try again"),
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
    EDEADLOCK(35),
    EDEADLK(35, "Resource deadlock would occur"),
    ENAMETOOLONG(36, "File name too long"),
    ENOLCK(37, "No record locks available"),
    ENOSYS(38, "Invalid system call number"),
    ENOTEMPTY(39, "Directory not empty"),
    ELOOP(40, "Too many symbolic links encountered"),
    ENOMSG(42, "No message of desired type"),
    EIDRM(43, "Identifier removed"),
    ECHRNG(44, "Channel number out of range"),
    EL2NSYNC(45, "Level 2 not synchronized"),
    EL3HLT(46, "Level 3 halted"),
    EL3RST(47, "Level 3 reset"),
    ELNRNG(48, "Link number out of range"),
    EUNATCH(49, "Protocol driver not attached"),
    ENOCSI(50, "No CSI structure available"),
    EL2HLT(51, "Level 2 halted"),
    EBADE(52, "Invalid exchange"),
    EBADR(53, "Invalid request descriptor"),
    EXFULL(54, "Exchange full"),
    ENOANO(55, "No anode"),
    EBADRQC(56, "Invalid request code"),
    EBADSLT(57, "Invalid slot"),
    EBFONT(59, "Bad font file format"),
    ENOSTR(60, "Device not a stream"),
    ENODATA(61, "No data available"),
    ETIME(62, "Timer expired"),
    ENOSR(63, "Out of streams resources"),
    ENONET(64, "Machine is not on the network"),
    ENOPKG(65, "Package not installed"),
    EREMOTE(66, "Object is remote"),
    ENOLINK(67, "Link has been severed"),
    EADV(68, "Advertise error"),
    ESRMNT(69, "Srmount error"),
    ECOMM(70, "Communication error on send"),
    EPROTO(71, "Protocol error"),
    EMULTIHOP(72, "Multihop attempted"),
    EDOTDOT(73, "RFS specific error"),
    EBADMSG(74, "Not a data message"),
    EOVERFLOW(75, "Value too large for defined data type"),
    ENOTUNIQ(76, "Name not unique on network"),
    EBADFD(77, "File descriptor in bad state"),
    EREMCHG(78, "Remote address changed"),
    ELIBACC(79, "Can not access a needed shared library"),
    ELIBBAD(80, "Accessing a corrupted shared library"),
    ELIBSCN(81, ".lib section in a.out corrupted"),
    ELIBMAX(82, "Attempting to link in too many shared libraries"),
    ELIBEXEC(83, "Cannot exec a shared library directly"),
    EILSEQ(84, "Illegal byte sequence"),
    ERESTART(85, "Interrupted system call should be restarted"),
    ESTRPIPE(86, "Streams pipe error"),
    EUSERS(87, "Too many users"),
    ENOTSOCK(88, "Socket operation on non-socket"),
    EDESTADDRREQ(89, "Destination address required"),
    EMSGSIZE(90, "Message too long"),
    EPROTOTYPE(91, "Protocol wrong type for socket"),
    ENOPROTOOPT(92, "Protocol not available"),
    EPROTONOSUPPORT(93, "Protocol not supported"),
    ESOCKTNOSUPPORT(94, "Socket type not supported"),
    EOPNOTSUPP(95, "Operation not supported on transport endpoint"),
    EPFNOSUPPORT(96, "Protocol family not supported"),
    EAFNOSUPPORT(97, "Address family not supported by protocol"),
    EADDRINUSE(98, "Address already in use"),
    EADDRNOTAVAIL(99, "Cannot assign requested address"),
    ENETDOWN(100, "Network is down"),
    ENETUNREACH(101, "Network is unreachable"),
    ENETRESET(102, "Network dropped connection because of reset"),
    ECONNABORTED(103, "Software caused connection abort"),
    ECONNRESET(104, "Connection reset by peer"),
    ENOBUFS(105, "No buffer space available"),
    EISCONN(106, "Transport endpoint is already connected"),
    ENOTCONN(107, "Transport endpoint is not connected"),
    ESHUTDOWN(108, "Cannot send after transport endpoint shutdown"),
    ETOOMANYREFS(109, "Too many references: cannot splice"),
    ETIMEDOUT(110, "Connection timed out"),
    ECONNREFUSED(111, "Connection refused"),
    EHOSTDOWN(112, "Host is down"),
    EHOSTUNREACH(113, "No route to host"),
    EALREADY(114, "Operation already in progress"),
    EINPROGRESS(115, "Operation now in progress"),
    ESTALE(116, "Stale file handle"),
    EUCLEAN(117, "Structure needs cleaning"),
    ENOTNAM(118, "Not a XENIX named type file"),
    ENAVAIL(119, "No XENIX semaphores available"),
    EISNAM(120, "Is a named type file"),
    EREMOTEIO(121, "Remote I/O error"),
    EDQUOT(122, "Quota exceeded"),
    ENOMEDIUM(123, "No medium found"),
    EMEDIUMTYPE(124, "Wrong medium type"),
    ECANCELED(125, "Operation Canceled"),
    ENOKEY(126, "Required key not available"),
    EKEYEXPIRED(127, "Key has expired"),
    EKEYREVOKED(128, "Key has been revoked"),
    EKEYREJECTED(129, "Key was rejected by service"),
    EOWNERDEAD(130, "Owner died"),
    ENOTRECOVERABLE(131, "State not recoverable"),
    ERFKILL(132, "Operation not possible due to RF-kill"),
    EHWPOISON(133, "Memory page has hardware error");

    private String message;
    private int number; // there can be more errors with the same number

    OSErrorEnum(int number, String message) {
        this.number = number;
        this.message = message;
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
        }
        return null;
    }

    @TruffleBoundary
    public static OSErrorEnum fromNumber(int number) {
        OSErrorEnum[] values = values();
        for (int i = number; i < values.length; i++) {
            if (values[i].getNumber() == number) {
                return values[i];
            }
        }
        return null;
    }
}
