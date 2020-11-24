/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.posix;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__FSPATH__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixExceptionWithOpaquePath;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PNfiDirEntry)
public class NfiDirEntryBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return NfiDirEntryBuiltinsFactory.getFactories();
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.produceBytes")
        PBytes nameAsBytes(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsBytes(getPosixSupport(), posixLib.dirEntryGetName(getPosixSupport(), self.dirEntryData), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = "!self.produceBytes")
        String nameAsString(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.dirEntryGetName(getPosixSupport(), self.dirEntryData));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        String repr(VirtualFrame frame, PNfiDirEntry self,
                        @Cached NameNode nameNode,
                        @Cached("create(__REPR__)") LookupAndCallUnaryNode reprNode,
                        @Cached CastToJavaStringNode castToStringNode) {
            return "<DirEntry " + castToStringNode.execute(reprNode.executeObject(frame, nameNode.call(frame, self))) + ">";
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PathNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "self.produceBytes")
        Object pathAsBytes(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsBytes(getPosixSupport(), posixLib.dirEntryGetPath(getPosixSupport(), self.dirEntryData), factory());
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Specialization(guards = "!self.produceBytes")
        Object pathAsString(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.getPathAsString(getPosixSupport(), posixLib.dirEntryGetPath(getPosixSupport(), self.dirEntryData));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = __FSPATH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object fspath(VirtualFrame frame, PNfiDirEntry self,
                        @Cached PathNode pathNode) {
            return pathNode.call(frame, self);
        }
    }

    @Builtin(name = "inode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        long inode(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryGetInode(getPosixSupport(), self.dirEntryData);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {
                    "follow_symlinks"}, doc = "return stat_result object for the entry; cached per entry")
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class StatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.StatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        Object stat(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile positiveLongProfile) {
            try {
                return PosixModuleBuiltins.createStatResult(factory(), positiveLongProfile, posixLib.dirEntryStat(getPosixSupport(), self.dirEntryData, followSymlinks));
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (PosixExceptionWithOpaquePath e) {
                throw raiseOSErrorFromPosixExceptionWithOpaquePath(frame, self, e, getPosixSupport(), posixLib, factory(), getConstructAndRaiseNode());
            }
        }
    }

    @Builtin(name = "is_symlink", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSymlinkNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean isSymlink(VirtualFrame frame, PNfiDirEntry self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryIsSymlink(getPosixSupport(), self.dirEntryData);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (PosixExceptionWithOpaquePath e) {
                throw raiseOSErrorFromPosixExceptionWithOpaquePath(frame, self, e, getPosixSupport(), posixLib, factory(), getConstructAndRaiseNode());
            }
        }
    }

    @Builtin(name = "is_file", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class IsFileNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.IsFileNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean isFile(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryIsFile(getPosixSupport(), self.dirEntryData, followSymlinks);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (PosixExceptionWithOpaquePath e) {
                throw raiseOSErrorFromPosixExceptionWithOpaquePath(frame, self, e, getPosixSupport(), posixLib, factory(), getConstructAndRaiseNode());
            }
        }
    }

    @Builtin(name = "is_dir", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, varArgsMarker = true, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class IsDirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NfiDirEntryBuiltinsClinicProviders.IsDirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        boolean isDir(VirtualFrame frame, PNfiDirEntry self, boolean followSymlinks,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                return posixLib.dirEntryIsDir(getPosixSupport(), self.dirEntryData, followSymlinks);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (PosixExceptionWithOpaquePath e) {
                throw raiseOSErrorFromPosixExceptionWithOpaquePath(frame, self, e, getPosixSupport(), posixLib, factory(), getConstructAndRaiseNode());
            }
        }
    }

    private static Object convertOpaquePath(PNfiDirEntry self, Object opaquePath, Object posixSupport, PosixSupportLibrary posixLib, PythonObjectFactory factory) {
        if (opaquePath == null) {
            return null;
        }
        if (self.produceBytes) {
            return posixLib.getPathAsBytes(posixSupport, opaquePath, factory);
        } else {
            return posixLib.getPathAsString(posixSupport, opaquePath);
        }
    }

    private static PException raiseOSErrorFromPosixExceptionWithOpaquePath(VirtualFrame frame, PNfiDirEntry self, PosixExceptionWithOpaquePath e, Object posixSupport, PosixSupportLibrary posixLib, PythonObjectFactory factory, PConstructAndRaiseNode raiseNode) {
        Object filename1 = convertOpaquePath(self, e.getFilename1(), posixSupport, posixLib, factory);
        Object filename2 = convertOpaquePath(self, e.getFilename2(), posixSupport, posixLib, factory);
        throw raiseNode.raiseOSError(frame, e.getErrorCode(), e.getMessage(), filename1, filename2);
    }
}
