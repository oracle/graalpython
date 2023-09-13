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

import static com.oracle.graal.python.nodes.BuiltinNames.T_NT;
import static com.oracle.graal.python.nodes.BuiltinNames.T_POSIX;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.Python3Core;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.PythonOS;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PathConversionNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixPath;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyOSFSPathNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "nt", isEager = true)
public final class NtModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return NtModuleBuiltinsFactory.getFactories();
    }

    @Override
    public void initialize(Python3Core core) {
        super.initialize(core);
        if (PythonOS.getPythonOS() == PythonOS.PLATFORM_WIN32) {
            addBuiltinConstant("_LOAD_LIBRARY_SEARCH_DLL_LOAD_DIR", 0x100);
            addBuiltinConstant("_LOAD_LIBRARY_SEARCH_DEFAULT_DIRS", 0x1000);
            core.removeBuiltinModule(T_POSIX);
        } else {
            core.removeBuiltinModule(T_NT);
        }
    }

    @Builtin(name = "_getfullpathname", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @GenerateNodeFactory
    abstract static class GetfullpathnameNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object getfullpathname(Object path,
                        @Bind("this") Node inliningTarget,
                        @Cached PyOSFSPathNode fsPathNode,
                        @Cached CastToJavaStringNode castStr) {
            // TODO should call win api
            try {
                String fspath = castStr.execute(fsPathNode.execute(null, inliningTarget, path));
                return PythonUtils.toTruffleStringUncached(getContext().getEnv().getPublicTruffleFile(fspath).getAbsoluteFile().toString());
            } catch (CannotCastException e) {
                return path;
            }
        }
    }

    @Builtin(name = "_path_splitroot", minNumOfPositionalArgs = 1, parameterNames = {"path"})
    @ArgumentClinic(name = "path", conversionClass = PathConversionNode.class, args = {"false", "false"})
    @GenerateNodeFactory
    abstract static class PathSplitRootNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        Object splitroot(PosixPath path) {
            // TODO should call WINAPI PathCchSkipRoot

            PythonObjectFactory factory = PythonObjectFactory.getUncached();
            TruffleString pathString = PosixSupportLibrary.getUncached().getPathAsString(getPosixSupport(), path.value);
            int len = pathString.codePointLengthUncached(TS_ENCODING);
            int index = pathString.indexOfCodePointUncached(':', 0, len, TS_ENCODING);
            if (index <= 0) {
                return factory.createTuple(new Object[]{T_EMPTY_STRING, pathString});
            } else {
                index++;
                int first = pathString.codePointAtIndexUncached(index, TS_ENCODING);
                if (first == '\\' || first == '/') {
                    index++;
                }
                TruffleString root = pathString.substringUncached(0, index, TS_ENCODING, false);
                TruffleString rest = pathString.substringUncached(index, len - index, TS_ENCODING, false);
                return factory.createTuple(new Object[]{root, rest});
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NtModuleBuiltinsClinicProviders.PathSplitRootNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "device_encoding", minNumOfPositionalArgs = 1, parameterNames = {"fd"})
    @ArgumentClinic(name = "fd", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class DeviceEncodingNode extends PythonUnaryClinicBuiltinNode {
        @Specialization
        @TruffleBoundary
        static Object deviceEncoding(@SuppressWarnings("unused") int fd) {
            // TODO should actually figure this out
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NtModuleBuiltinsClinicProviders.PathSplitRootNodeClinicProviderGen.INSTANCE;
        }
    }
}
