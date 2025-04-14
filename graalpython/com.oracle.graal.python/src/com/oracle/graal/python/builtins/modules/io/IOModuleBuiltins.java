/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.BlockingIOError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.EncodingWarning;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.IOUnsupportedOperation;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PWindowsConsoleIO;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins.T_WARN;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_MODE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_LOCALE;
import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_POSIX;
import static com.oracle.graal.python.nodes.BuiltinNames.T__WARNINGS;
import static com.oracle.graal.python.nodes.ErrorMessages.BINARY_MODE_DOESN_T_TAKE_AN_S_ARGUMENT;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_HAVE_TEXT_AND_BINARY_MODE_AT_ONCE;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_HAVE_UNBUFFERED_TEXT_IO;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_BUFFERING_SIZE;
import static com.oracle.graal.python.nodes.ErrorMessages.LINE_BUFFERING_ISNT_SUPPORTED;
import static com.oracle.graal.python.nodes.ErrorMessages.MODE_U_CANNOT_BE_COMBINED_WITH_X_W_A_OR;
import static com.oracle.graal.python.nodes.ErrorMessages.MUST_HAVE_EXACTLY_ONE_OF_CREATE_READ_WRITE_APPEND_MODE;
import static com.oracle.graal.python.nodes.ErrorMessages.UNKNOWN_MODE_S;
import static com.oracle.graal.python.nodes.StringLiterals.T_STRICT;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeWarning;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.WarningsModuleBuiltins;
import com.oracle.graal.python.builtins.modules.io.IONodes.IOMode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectSetAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_io")
public final class IOModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IOModuleBuiltinsFactory.getFactories();
    }

    public static final int DEFAULT_BUFFER_SIZE = 8 * 1024;

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        addBuiltinConstant("SEEK_SET", SEEK_SET);
        addBuiltinConstant("SEEK_CUR", SEEK_CUR);
        addBuiltinConstant("SEEK_END", SEEK_END);
        addBuiltinConstant("DEFAULT_BUFFER_SIZE", DEFAULT_BUFFER_SIZE);
        PythonBuiltinClass unsupportedOpExcType = core.lookupType(IOUnsupportedOperation);
        PythonBuiltinClass osError = core.lookupType(OSError);
        unsupportedOpExcType.setBases(null, osError, new PythonAbstractClass[]{osError, core.lookupType(ValueError)});
        addBuiltinConstant(IOUnsupportedOperation.getName(), unsupportedOpExcType);
        addBuiltinConstant(BlockingIOError.getName(), core.lookupType(BlockingIOError));

        addBuiltinConstant("_warn", core.lookupBuiltinModule(T__WARNINGS).getAttribute(T_WARN));
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            addBuiltinConstant("_os", core.lookupBuiltinModule(T_NT));
            addBuiltinConstant("_WindowsConsoleIO", PWindowsConsoleIO);
        } else {
            addBuiltinConstant("_os", core.lookupBuiltinModule(T_POSIX));
        }
    }

    private static PFileIO createFileIO(VirtualFrame frame, Node inliningTarget, Object file, IONodes.IOMode mode, boolean closefd, Object opener,
                    FileIOBuiltins.FileIOInit initFileIO) {
        /* Create the Raw file stream */
        mode.text = mode.universal = false; // FileIO doesn't recognize those.
        PFileIO fileIO = PFactory.createFileIO(PythonLanguage.get(inliningTarget));
        initFileIO.execute(frame, inliningTarget, fileIO, file, mode, closefd, opener);
        return fileIO;
    }

    // PEP 578 stub
    @Builtin(name = "open_code", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    public abstract static class IOOpenCodeNode extends PythonUnaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOModuleBuiltinsClinicProviders.IOOpenCodeNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static PFileIO openCode(VirtualFrame frame, TruffleString path,
                        @Bind("this") Node inliningTarget,
                        @Cached FileIOBuiltins.FileIOInit initFileIO) {
            return createFileIO(frame, inliningTarget, path, IOMode.RB, true, PNone.NONE, initFileIO);
        }
    }

    @Builtin(name = "open", minNumOfPositionalArgs = 1, parameterNames = {"file", "mode", "buffering", "encoding", "errors", "newline", "closefd", "opener"})
    @ArgumentClinic(name = "mode", conversionClass = IONodes.CreateIOModeNode.class, args = "true")
    @ArgumentClinic(name = "buffering", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @ArgumentClinic(name = "encoding", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "errors", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "newline", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "PNone.NONE", useDefaultForNone = true)
    @ArgumentClinic(name = "closefd", conversion = ArgumentClinic.ClinicConversion.Boolean, defaultValue = "true", useDefaultForNone = true)
    @ImportStatic({IONodes.class, IONodes.IOMode.class})
    @GenerateNodeFactory
    public abstract static class IOOpenNode extends PythonClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOModuleBuiltinsClinicProviders.IOOpenNodeClinicProviderGen.INSTANCE;
        }

        // NOTE: specializations in this class must have at least protected visibility, otherwise
        // the DSL processor does not see them in BuiltinFunctions.OpenNode and silently does not
        // generate the node factory, leading to "NameError: name 'open' is not defined" at runtime

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "!isBinary(mode)", "bufferingValue != 0"})
        protected static Object openText(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Exclusive @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @Cached TextIOWrapperNodes.TextIOWrapperInitNode initTextIO,
                        @Cached PyObjectSetAttr setAttrNode,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callClose,
                        @Bind PythonLanguage language,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            PFileIO fileIO = createFileIO(frame, inliningTarget, file, mode, closefd, opener, initFileIO);
            Object result = fileIO;
            try {
                /* buffering */
                boolean isatty = false;
                int buffering = bufferingValue;
                if (buffering < 0) {
                    // copied from PFileIOBuiltins.IsAttyNode
                    isatty = posixLib.isatty(PosixSupport.get(inliningTarget), fileIO.getFD());
                    /*-
                        // CPython way is slow in our case.
                        Object res = libFileIO.lookupAndCallRegularMethod(fileIO, frame, ISATTY);
                        isatty = libIsTrue.isTrue(res, frame);
                    */
                }

                boolean line_buffering;
                if (buffering == 1 || isatty) {
                    buffering = -1;
                    line_buffering = true;
                } else {
                    line_buffering = false;
                }

                if (buffering < 0) {
                    buffering = fileIO.getBlksize();
                }
                if (buffering < 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, INVALID_BUFFERING_SIZE);
                }

                /* if not buffering, returns the raw file object */
                if (buffering == 0) {
                    invalidunbuf(file, mode, bufferingValue, encoding, errors, newline, closefd, opener, raiseNode);
                }

                /* wraps into a buffered file */

                PBuffered buffer = createBufferedIO.execute(frame, inliningTarget, fileIO, buffering, mode);
                result = buffer;

                /* wraps into a TextIOWrapper */
                PTextIO wrapper = PFactory.createTextIO(language);
                initTextIO.execute(frame, inliningTarget, wrapper, buffer, encoding,
                                errors == PNone.NONE ? T_STRICT : (TruffleString) errors,
                                newline, line_buffering, false);

                result = wrapper;

                setAttrNode.execute(frame, inliningTarget, wrapper, T_MODE, mode.mode);
                return result;
            } catch (PException e) {
                callClose.execute(frame, inliningTarget, result, T_CLOSE);
                throw e;
            }
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue == 0"})
        protected static PFileIO openBinaryNoBuf(VirtualFrame frame, Object file, IONodes.IOMode mode, @SuppressWarnings("unused") int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached FileIOBuiltins.FileIOInit initFileIO) {
            return createFileIO(frame, inliningTarget, file, mode, closefd, opener, initFileIO);
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue == 1"})
        protected static Object openBinaryB1(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode,
                        @Exclusive @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Exclusive @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callClose,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            warnNode.warnEx(frame, RuntimeWarning, LINE_BUFFERING_ISNT_SUPPORTED, 1);
            return openBinary(frame, file, mode, bufferingValue, encoding, errors, newline, closefd, opener, inliningTarget, initFileIO, createBufferedIO, posixLib, callClose, raiseNode);
        }

        @Specialization(guards = {"!isXRWA(mode)", "!isUnknown(mode)", "!isTB(mode)", "isValidUniveral(mode)", "isBinary(mode)", "bufferingValue != 1", "bufferingValue != 0"})
        protected static Object openBinary(VirtualFrame frame, Object file, IONodes.IOMode mode, int bufferingValue,
                        @SuppressWarnings("unused") PNone encoding,
                        @SuppressWarnings("unused") PNone errors,
                        @SuppressWarnings("unused") PNone newline,
                        boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget,
                        @Exclusive @Cached FileIOBuiltins.FileIOInit initFileIO,
                        @Exclusive @Cached IONodes.CreateBufferedIONode createBufferedIO,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Exclusive @Cached PyObjectCallMethodObjArgs callClose,
                        @Exclusive @Cached PRaiseNode raiseNode) {
            PFileIO fileIO = createFileIO(frame, inliningTarget, file, mode, closefd, opener, initFileIO);
            try {
                /* buffering */
                boolean isatty = false;
                int buffering = bufferingValue;
                if (buffering < 0) {
                    // copied from PFileIOBuiltins.IsAttyNode
                    isatty = posixLib.isatty(PosixSupport.get(inliningTarget), fileIO.getFD());
                    /*-
                        // CPython way is slow in our case.
                        Object res = libFileIO.lookupAndCallRegularMethod(fileIO, frame, ISATTY);
                        isatty = libIsTrue.isTrue(res, frame);
                    */
                }

                if (buffering == 1 || isatty) {
                    buffering = -1;
                }

                if (buffering < 0) {
                    buffering = fileIO.getBlksize();
                }
                if (buffering < 0) {
                    throw raiseNode.raise(inliningTarget, ValueError, INVALID_BUFFERING_SIZE);
                }

                /* if not buffering, returns the raw file object */
                if (buffering == 0) {
                    return fileIO;
                }

                /* wraps into a buffered file */

                /* if binary, returns the buffered file */
                return createBufferedIO.execute(frame, inliningTarget, fileIO, buffering, mode);
            } catch (PException e) {
                callClose.execute(frame, inliningTarget, fileIO, T_CLOSE);
                throw e;
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isUnknown(mode)")
        protected static Object unknownMode(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, UNKNOWN_MODE_S, mode.mode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isTB(mode)")
        protected static Object invalidTB(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, CAN_T_HAVE_TEXT_AND_BINARY_MODE_AT_ONCE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "!isValidUniveral(mode)")
        protected static Object invalidUniversal(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, MODE_U_CANNOT_BE_COMBINED_WITH_X_W_A_OR);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isXRWA(mode)")
        protected static Object invalidxrwa(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, MUST_HAVE_EXACTLY_ONE_OF_CREATE_READ_WRITE_APPEND_MODE);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isBinary(mode)", "isAnyNotNone(encoding, errors, newline)"})
        protected static Object invalidBinary(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            String s;
            if (encoding != PNone.NONE) {
                s = "encoding";
            } else if (errors != PNone.NONE) {
                s = "errors";
            } else {
                s = "newline";
            }
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, BINARY_MODE_DOESN_T_TAKE_AN_S_ARGUMENT, s);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"!isBinary(mode)", "bufferingValue == 0"})
        protected static Object invalidunbuf(Object file, IONodes.IOMode mode, int bufferingValue, Object encoding, Object errors, Object newline, boolean closefd, Object opener,
                        @Bind("this") Node inliningTarget) {
            throw PRaiseNode.raiseStatic(inliningTarget, ValueError, CAN_T_HAVE_UNBUFFERED_TEXT_IO);
        }

        public static boolean isAnyNotNone(Object encoding, Object errors, Object newline) {
            return encoding != PNone.NONE || errors != PNone.NONE || newline != PNone.NONE;
        }
    }

    @Builtin(name = "text_encoding", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"encoding", "stacklevel"})
    @ArgumentClinic(name = "stacklevel", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "2")
    @GenerateNodeFactory
    abstract static class TextEncodingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object textEncoding(VirtualFrame frame, @SuppressWarnings("unused") PNone encoding, int stacklevel,
                        @Cached WarningsModuleBuiltins.WarnNode warnNode) {
            if (PythonContext.get(this).getOption(PythonOptions.WarnDefaultEncodingFlag)) {
                warnNode.warnEx(frame, EncodingWarning, ErrorMessages.WARN_ENCODING_ARGUMENT_NOT_SPECIFIED, stacklevel);
            }
            return T_LOCALE;
        }

        @Fallback
        static Object textEncoding(Object encoding, @SuppressWarnings("unused") Object stacklevel) {
            return encoding;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return IOModuleBuiltinsClinicProviders.TextEncodingNodeClinicProviderGen.INSTANCE;
        }
    }
}
