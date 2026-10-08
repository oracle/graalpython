/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.annotations.PythonOS.PLATFORM_WIN32;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PWindowsConsoleIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_ISATTY;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NAME;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_OPEN_CONSOLE_INPUT_BUFFER_FOR_WRITING;
import static com.oracle.graal.python.nodes.ErrorMessages.CANNOT_OPEN_NON_CONSOLE_FILE;
import static com.oracle.graal.python.nodes.ErrorMessages.CONSOLE_BUFFER_DOES_NOT_SUPPORT_WRITING;
import static com.oracle.graal.python.nodes.ErrorMessages.CONSOLE_INPUT_IS_NOT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_HAVE_EXACTLY_ONE_OF_READ_WRITE_MODE;
import static com.oracle.graal.python.nodes.ErrorMessages.NEG_FILE_DESC;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.Builtin;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.annotations.Slot.SlotSignature;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.builtins.objects.type.TypeNodes;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupport.Buffer;
import com.oracle.graal.python.runtime.PosixSupport.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;

@CoreFunctions(extendClasses = PWindowsConsoleIO, os = PLATFORM_WIN32)
public final class WindowsConsoleIOBuiltins extends PythonBuiltins {
    private static final int CONSOLE_WRITE = 'w';
    public static final TpSlots SLOTS = WindowsConsoleIOBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return WindowsConsoleIOBuiltinsFactory.getFactories();
    }

    public static PFileIO create(PythonLanguage language) {
        return PFactory.createFileIO(PWindowsConsoleIO, PWindowsConsoleIO.getInstanceShape(language));
    }

    public static void internalInit(PFileIO self, Object name, int fd, IONodes.IOMode mode, int consoleType) {
        if (mode.reading == mode.writing || mode.text || mode.universal || mode.isInvalid) {
            throw PRaiseNode.raiseStatic(null, ValueError, MUST_HAVE_EXACTLY_ONE_OF_READ_WRITE_MODE);
        }
        if (mode.reading) {
            throw PRaiseNode.raiseStatic(null, ValueError, CONSOLE_INPUT_IS_NOT_SUPPORTED);
        }
        if (consoleType != CONSOLE_WRITE) {
            throw PRaiseNode.raiseStatic(null, ValueError, CANNOT_OPEN_CONSOLE_INPUT_BUFFER_FOR_WRITING);
        }
        self.setCloseFD(false);
        self.setFD(fd, null);
        self.setWritable();
        self.setBlksize(IOModuleBuiltins.DEFAULT_BUFFER_SIZE);
        WriteAttributeToObjectNode.getUncached().execute(self, T_NAME, name);
    }

    @Slot(value = SlotKind.tp_new, isComplex = true)
    @SlotSignature(name = "_WindowsConsoleIO", minNumOfPositionalArgs = 1, takesVarArgs = true, takesVarKeywordArgs = true)
    @GenerateNodeFactory
    abstract static class NewNode extends PythonBuiltinNode {
        @Specialization
        static PFileIO create(Object cls, @SuppressWarnings("unused") Object file,
                        @Cached TypeNodes.GetInstanceShape getInstanceShape) {
            return PFactory.createFileIO(cls, getInstanceShape.execute(cls));
        }
    }

    @Slot(value = SlotKind.tp_init, isComplex = true)
    @SlotSignature(name = "_WindowsConsoleIO", minNumOfPositionalArgs = 2, parameterNames = {"$self", "file", "mode", "closefd", "opener"})
    @ArgumentClinic(name = "file", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "false")
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WindowsConsoleIOBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PNone init(PFileIO self, int file, IONodes.IOMode mode, @SuppressWarnings("unused") boolean closefd, @SuppressWarnings("unused") Object opener,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context) {
            if (file < 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, NEG_FILE_DESC);
            }
            // We don't release the GIL because CPython doesn't do it
            int consoleType = context.getPosixSupport().getWindowsConsoleType(file);
            if (consoleType == 0) {
                throw PRaiseNode.raiseStatic(inliningTarget, ValueError, CANNOT_OPEN_NON_CONSOLE_FILE);
            }
            internalInit(self, file, file, mode, consoleType);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "b"})
    @ArgumentClinic(name = "b", conversion = ArgumentClinic.ClinicConversion.ReadableBuffer)
    @GenerateNodeFactory
    abstract static class WriteNode extends PythonBinaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return WindowsConsoleIOBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(limit = "3")
        static Object write(VirtualFrame frame, PFileIO self, Object buffer,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("buffer") PythonBufferAccessLibrary bufferLib,
                        @Cached GilNode gil,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                if (self.isClosed()) {
                    throw PRaiseNode.raiseStatic(inliningTarget, ValueError, IO_CLOSED);
                }
                if (!self.isWritable()) {
                    throw PRaiseNode.raiseStatic(inliningTarget, IOUnsupportedOperation, CONSOLE_BUFFER_DOES_NOT_SUPPORT_WRITING);
                }
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                int length = bufferLib.getBufferLength(buffer);
                try {
                    gil.release(true);
                    try {
                        return context.getPosixSupport().writeWindowsConsole(self.getFD(), new Buffer(bytes, length));
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer);
            }
        }
    }

    @Builtin(name = J_ISATTY, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsattyNode extends PythonUnaryBuiltinNode {
        @Specialization
        static boolean isatty(PFileIO self,
                        @Bind Node inliningTarget,
                        @Cached PRaiseNode raise) {
            if (self.isClosed()) {
                throw raise.raise(inliningTarget, ValueError, IO_CLOSED);
            }
            return true;
        }
    }

}
