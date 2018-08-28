/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.runtime.PythonCore;
import com.oracle.truffle.api.dsl.NodeFactory;

@CoreFunctions(defineModule = "errno")
public class ErrnoModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return new ArrayList<>();
    }

    /**
     * Generated using the following:
     *
     * <pre>
     * grep -RPo "#define\s+([A-Z]+)\s+(\d+)" /usr/include/asm-generic/errno* | awk '{print "builtinConstants.put(\"" $2 "\", " $3 ");"}'
     * </pre>
     */
    @Override
    public void initialize(PythonCore core) {
        super.initialize(core);
        builtinConstants.put("EPERM", 1);
        builtinConstants.put("ENOENT", 2);
        builtinConstants.put("ESRCH", 3);
        builtinConstants.put("EINTR", 4);
        builtinConstants.put("EIO", 5);
        builtinConstants.put("ENXIO", 6);
        builtinConstants.put("ENOEXEC", 8);
        builtinConstants.put("EBADF", 9);
        builtinConstants.put("ECHILD", 10);
        builtinConstants.put("EAGAIN", 11);
        builtinConstants.put("ENOMEM", 12);
        builtinConstants.put("EACCES", 13);
        builtinConstants.put("EFAULT", 14);
        builtinConstants.put("ENOTBLK", 15);
        builtinConstants.put("EBUSY", 16);
        builtinConstants.put("EEXIST", 17);
        builtinConstants.put("EXDEV", 18);
        builtinConstants.put("ENODEV", 19);
        builtinConstants.put("ENOTDIR", 20);
        builtinConstants.put("EISDIR", 21);
        builtinConstants.put("EINVAL", 22);
        builtinConstants.put("ENFILE", 23);
        builtinConstants.put("EMFILE", 24);
        builtinConstants.put("ENOTTY", 25);
        builtinConstants.put("ETXTBSY", 26);
        builtinConstants.put("EFBIG", 27);
        builtinConstants.put("ENOSPC", 28);
        builtinConstants.put("ESPIPE", 29);
        builtinConstants.put("EROFS", 30);
        builtinConstants.put("EMLINK", 31);
        builtinConstants.put("EPIPE", 32);
        builtinConstants.put("EDOM", 33);
        builtinConstants.put("ERANGE", 34);
        builtinConstants.put("EDEADLK", 35);
        builtinConstants.put("ENAMETOOLONG", 36);
        builtinConstants.put("ENOLCK", 37);
        builtinConstants.put("ENOSYS", 38);
        builtinConstants.put("ENOTEMPTY", 39);
        builtinConstants.put("ELOOP", 40);
        builtinConstants.put("ENOMSG", 42);
        builtinConstants.put("EIDRM", 43);
        builtinConstants.put("ECHRNG", 44);
        builtinConstants.put("ELNRNG", 48);
        builtinConstants.put("EUNATCH", 49);
        builtinConstants.put("ENOCSI", 50);
        builtinConstants.put("EBADE", 52);
        builtinConstants.put("EBADR", 53);
        builtinConstants.put("EXFULL", 54);
        builtinConstants.put("ENOANO", 55);
        builtinConstants.put("EBADRQC", 56);
        builtinConstants.put("EBADSLT", 57);
        builtinConstants.put("EBFONT", 59);
        builtinConstants.put("ENOSTR", 60);
        builtinConstants.put("ENODATA", 61);
        builtinConstants.put("ETIME", 62);
        builtinConstants.put("ENOSR", 63);
        builtinConstants.put("ENONET", 64);
        builtinConstants.put("ENOPKG", 65);
        builtinConstants.put("EREMOTE", 66);
        builtinConstants.put("ENOLINK", 67);
        builtinConstants.put("EADV", 68);
        builtinConstants.put("ESRMNT", 69);
        builtinConstants.put("ECOMM", 70);
        builtinConstants.put("EPROTO", 71);
        builtinConstants.put("EMULTIHOP", 72);
        builtinConstants.put("EDOTDOT", 73);
        builtinConstants.put("EBADMSG", 74);
        builtinConstants.put("EOVERFLOW", 75);
        builtinConstants.put("ENOTUNIQ", 76);
        builtinConstants.put("EBADFD", 77);
        builtinConstants.put("EREMCHG", 78);
        builtinConstants.put("ELIBACC", 79);
        builtinConstants.put("ELIBBAD", 80);
        builtinConstants.put("ELIBSCN", 81);
        builtinConstants.put("ELIBMAX", 82);
        builtinConstants.put("ELIBEXEC", 83);
        builtinConstants.put("EILSEQ", 84);
        builtinConstants.put("ERESTART", 85);
        builtinConstants.put("ESTRPIPE", 86);
        builtinConstants.put("EUSERS", 87);
        builtinConstants.put("ENOTSOCK", 88);
        builtinConstants.put("EDESTADDRREQ", 89);
        builtinConstants.put("EMSGSIZE", 90);
        builtinConstants.put("EPROTOTYPE", 91);
        builtinConstants.put("ENOPROTOOPT", 92);
        builtinConstants.put("EPROTONOSUPPORT", 93);
        builtinConstants.put("ESOCKTNOSUPPORT", 94);
        builtinConstants.put("EOPNOTSUPP", 95);
        builtinConstants.put("EPFNOSUPPORT", 96);
        builtinConstants.put("EAFNOSUPPORT", 97);
        builtinConstants.put("EADDRINUSE", 98);
        builtinConstants.put("EADDRNOTAVAIL", 99);
        builtinConstants.put("ENETDOWN", 100);
        builtinConstants.put("ENETUNREACH", 101);
        builtinConstants.put("ENETRESET", 102);
        builtinConstants.put("ECONNABORTED", 103);
        builtinConstants.put("ECONNRESET", 104);
        builtinConstants.put("ENOBUFS", 105);
        builtinConstants.put("EISCONN", 106);
        builtinConstants.put("ENOTCONN", 107);
        builtinConstants.put("ESHUTDOWN", 108);
        builtinConstants.put("ETOOMANYREFS", 109);
        builtinConstants.put("ETIMEDOUT", 110);
        builtinConstants.put("ECONNREFUSED", 111);
        builtinConstants.put("EHOSTDOWN", 112);
        builtinConstants.put("EHOSTUNREACH", 113);
        builtinConstants.put("EALREADY", 114);
        builtinConstants.put("EINPROGRESS", 115);
        builtinConstants.put("ESTALE", 116);
        builtinConstants.put("EUCLEAN", 117);
        builtinConstants.put("ENOTNAM", 118);
        builtinConstants.put("ENAVAIL", 119);
        builtinConstants.put("EISNAM", 120);
        builtinConstants.put("EREMOTEIO", 121);
        builtinConstants.put("EDQUOT", 122);
        builtinConstants.put("ENOMEDIUM", 123);
        builtinConstants.put("EMEDIUMTYPE", 124);
        builtinConstants.put("ECANCELED", 125);
        builtinConstants.put("ENOKEY", 126);
        builtinConstants.put("EKEYEXPIRED", 127);
        builtinConstants.put("EKEYREVOKED", 128);
        builtinConstants.put("EKEYREJECTED", 129);
        builtinConstants.put("EOWNERDEAD", 130);
        builtinConstants.put("ENOTRECOVERABLE", 131);
        builtinConstants.put("ERFKILL", 132);
        builtinConstants.put("EHWPOISON", 133);
    }
}
