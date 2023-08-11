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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OverflowError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_ANONYMOUS;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_PRIVATE;
import static com.oracle.graal.python.runtime.PosixConstants.MAP_SHARED;
import static com.oracle.graal.python.runtime.PosixConstants.PROT_READ;
import static com.oracle.graal.python.runtime.PosixConstants.PROT_WRITE;
import static com.oracle.graal.python.runtime.PosixSupportLibrary.ST_SIZE;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.MMapModuleBuiltinsClinicProviders.MMapNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes;
import com.oracle.graal.python.builtins.objects.cext.capi.NativeCAPISymbol;
import com.oracle.graal.python.builtins.objects.cext.capi.transitions.CApiTransitions.PythonToNativeNode;
import com.oracle.graal.python.builtins.objects.mmap.PMMap;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "mmap")
public final class MMapModuleBuiltins extends PythonBuiltins {

    public static final TruffleString T_INIT_BUFFERPROTOCOL = tsLiteral("mmap_init_bufferprotocol");

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return MMapModuleBuiltinsFactory.getFactories();
    }

    public MMapModuleBuiltins() {
        addBuiltinConstant("ACCESS_DEFAULT", PMMap.ACCESS_DEFAULT);
        addBuiltinConstant("ACCESS_READ", PMMap.ACCESS_READ);
        addBuiltinConstant("ACCESS_WRITE", PMMap.ACCESS_WRITE);
        addBuiltinConstant("ACCESS_COPY", PMMap.ACCESS_COPY);
        // 'PROT_EXEC': 4,
        // 'PROT_READ': 1,
        // 'PROT_WRITE': 2,
        // 'MAP_SHARED': 1,
        // 'MAP_PRIVATE': 2,
        // 'MAP_ANON': 4096,
        // 'MAP_ANONYMOUS': 4096,
        // 'MADV_NORMAL': 0,
        // 'MADV_RANDOM': 1,
        // 'MADV_SEQUENTIAL': 2,
        // 'MADV_WILLNEED': 3,
        // 'MADV_DONTNEED': 4,
        // 'MADV_FREE': 5

        addBuiltinConstant("ALLOCATIONGRANULARITY", 4096);
        addBuiltinConstant("PAGESIZE", 4096);

        for (PosixConstants.IntConstant c : PosixConstants.mmapFlags) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }

        for (PosixConstants.IntConstant c : PosixConstants.mmapProtection) {
            if (c.defined) {
                addBuiltinConstant(c.name, c.getValueIfDefined());
            }
        }
    }

    @Override
    public void postInitialize(Python3Core core) {
        super.postInitialize(core);
        core.getContext().registerCApiHook(() -> {
            CExtNodes.PCallCapiFunction.getUncached().call(NativeCAPISymbol.FUN_MMAP_INIT_BUFFERPROTOCOL, PythonToNativeNode.executeUncached(PythonBuiltinClassType.PMMap));
        });
    }

    @Builtin(name = "mmap", minNumOfPositionalArgs = 3, parameterNames = {"cls", "fd", "length", "flags", "prot", "access", "offset"}, constructsClass = PythonBuiltinClassType.PMMap)
    @GenerateNodeFactory
    // Note: it really should not call fileno on fd as per Python spec
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "length", conversion = ClinicConversion.LongIndex)
    @ArgumentClinic(name = "flags", conversion = ClinicConversion.Int, defaultValue = "FLAGS_DEFAULT")
    @ArgumentClinic(name = "prot", conversion = ClinicConversion.Int, defaultValue = "PROT_DEFAULT")
    @ArgumentClinic(name = "access", conversion = ClinicConversion.Int, defaultValue = "ACCESS_ARG_DEFAULT")
    @ArgumentClinic(name = "offset", conversion = ClinicConversion.Long, defaultValue = "0")
    public abstract static class MMapNode extends PythonClinicBuiltinNode {
        protected static final int ACCESS_ARG_DEFAULT = PMMap.ACCESS_DEFAULT;
        protected static final int FLAGS_DEFAULT = MAP_SHARED.value;
        protected static final int PROT_DEFAULT = PROT_WRITE.value | PROT_READ.value;

        private static final int ANONYMOUS_FD = -1;

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return MMapNodeClinicProviderGen.INSTANCE;
        }

        // mmap(fileno, length, tagname=None, access=ACCESS_DEFAULT[, offset=0])
        @Specialization(guards = "!isIllegal(fd)")
        PMMap doFile(VirtualFrame frame, Object clazz, int fd, long lengthIn, int flagsIn, int protIn, @SuppressWarnings("unused") int accessIn, long offset,
                        @Bind("this") Node inliningTarget,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixSupport) {
            checkLength(lengthIn);
            checkOffset(offset);
            int flags = flagsIn;
            int prot = protIn;
            int access = accessIn;
            switch (access) {
                case PMMap.ACCESS_READ:
                    flags = MAP_SHARED.value;
                    prot = PROT_READ.value;
                    break;
                case PMMap.ACCESS_WRITE:
                    flags = MAP_SHARED.value;
                    prot = PROT_READ.value | PROT_WRITE.value;
                    break;
                case PMMap.ACCESS_COPY:
                    flags = MAP_PRIVATE.value;
                    prot = PROT_READ.value | PROT_WRITE.value;
                    break;
                case PMMap.ACCESS_DEFAULT:
                    // map prot to access type
                    if (((prot & PROT_READ.value) != 0) && ((prot & PROT_WRITE.value) != 0)) {
                        // ACCESS_DEFAULT
                    } else if ((prot & PROT_WRITE.value) != 0) {
                        access = PMMap.ACCESS_WRITE;
                    } else {
                        access = PMMap.ACCESS_READ;
                    }
                    break;
                default:
                    throw raise(ValueError, ErrorMessages.MEM_MAPPED_OFFSET_INVALID_ACCESS);
            }

            auditNode.audit(inliningTarget, "mmap.__new__", fd, lengthIn, access, offset);

            // For file mappings we use fstat to validate the length or to initialize the length if
            // it is 0 meaning that we should find it out for the user
            long length = lengthIn;
            if (fd != ANONYMOUS_FD) {
                long[] fstatResult = null;
                try {
                    fstatResult = posixSupport.fstat(getPosixSupport(), fd);
                } catch (PosixException ignored) {
                }
                if (fstatResult != null && length == 0) {
                    if (fstatResult[ST_SIZE] == 0) {
                        throw raise(ValueError, ErrorMessages.CANNOT_MMAP_AN_EMPTY_FILE);
                    }
                    if (offset >= fstatResult[ST_SIZE]) {
                        throw raise(ValueError, ErrorMessages.MMAP_S_IS_GREATER_THAN_FILE_SIZE, "offset");
                    }
                    // Unlike in CPython, this always fits in the long range
                    length = fstatResult[ST_SIZE] - offset;
                } else if (fstatResult != null && (offset > fstatResult[ST_SIZE] || fstatResult[ST_SIZE] - offset < length)) {
                    throw raise(ValueError, ErrorMessages.MMAP_S_IS_GREATER_THAN_FILE_SIZE, "length");
                }
            }

            // Fixup the flags if we want to use anonymous map
            int dupFd;
            if (fd == ANONYMOUS_FD) {
                dupFd = ANONYMOUS_FD;
                flags |= MAP_ANONYMOUS.value;
                // TODO: CPython uses mapping to "/dev/zero" on systems that do not support
                // MAP_ANONYMOUS, maybe this can be detected and handled by the POSIX layer
            } else {
                try {
                    dupFd = posixSupport.dup(getPosixSupport(), fd);
                } catch (PosixException e) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            }

            Object mmapHandle;
            try {
                mmapHandle = posixSupport.mmap(getPosixSupport(), length, prot, flags, dupFd, offset);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return factory().createMMap(getContext(), clazz, mmapHandle, dupFd, length, access);
        }

        @Specialization(guards = "isIllegal(fd)")
        @SuppressWarnings("unused")
        PMMap doIllegal(Object clazz, int fd, long lengthIn, int flagsIn, int protIn, int accessIn, long offset) {
            throw raise(PythonBuiltinClassType.OSError);
        }

        protected static boolean isIllegal(int fd) {
            return fd < -1;
        }

        private void checkLength(long length) {
            if (length < 0) {
                throw raise(OverflowError, ErrorMessages.MEM_MAPPED_LENGTH_MUST_BE_POSITIVE);
            }
        }

        private void checkOffset(long offset) {
            if (offset < 0) {
                throw raise(OverflowError, ErrorMessages.MEM_MAPPED_OFFSET_MUST_BE_POSITIVE);
            }
        }
    }
}
