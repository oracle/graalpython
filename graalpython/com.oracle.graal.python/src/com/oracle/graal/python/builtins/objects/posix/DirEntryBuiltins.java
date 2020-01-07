/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.LinkOption;
import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.attributes.WriteAttributeToObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDirEntry)
public class DirEntryBuiltins extends PythonBuiltins {

    private static final LinkOption[] NOFOLLOW_LINKS_OPTIONS = new LinkOption[]{LinkOption.NOFOLLOW_LINKS};
    private static final LinkOption[] NO_LINK_OPTIONS = new LinkOption[0];

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DirEntryBuiltinsFactory.getFactories();
    }

    @Builtin(name = SpecialMethodNames.__REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        @TruffleBoundary
        String repr(PDirEntry self) {
            return "<DirEntry '" + self.getFile().getName() + "'>";
        }
    }

    @Builtin(name = SpecialMethodNames.__FSPATH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        String fspath(PDirEntry self) {
            return self.getFile().getPath();
        }
    }

    @Builtin(name = "inode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        PNone inode(@SuppressWarnings("unused") PDirEntry self) {
            return PNone.NONE;
        }
    }

    private static LinkOption[] getLinkOption(boolean followSymlinks) {
        return followSymlinks ? NO_LINK_OPTIONS : NOFOLLOW_LINKS_OPTIONS;
    }

    @Builtin(name = "is_symlink", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSymNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean test(PDirEntry self) {
            return self.getFile().isSymbolicLink();
        }
    }

    @Builtin(name = "is_dir", minNumOfPositionalArgs = 1, keywordOnlyNames = {"follow_symlinks"})
    @GenerateNodeFactory
    abstract static class IsDirNode extends PythonBinaryBuiltinNode {
        @Specialization
        boolean testBool(PDirEntry self, boolean followSymlinks) {
            return self.getFile().isDirectory(getLinkOption(followSymlinks));
        }

        @Specialization
        boolean testNone(PDirEntry self, @SuppressWarnings("unused") PNone followSymlinks) {
            return testBool(self, true);
        }

        @Specialization(limit = "1")
        boolean testAny(VirtualFrame frame, Object self, Object followSymlinks,
                        @CachedLibrary("followSymlinks") PythonObjectLibrary lib) {
            if (self instanceof PDirEntry) {
                return testBool((PDirEntry) self, lib.isTrueWithState(followSymlinks, PArguments.getThreadState(frame)));
            } else {
                throw raise(PythonBuiltinClassType.TypeError, "descriptor 'is_dir' requires a 'posix.DirEntry' object but received a '%p'", self);
            }
        }
    }

    @Builtin(name = "is_file", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsFileNode extends PythonUnaryBuiltinNode {
        @Specialization
        boolean test(PDirEntry self) {
            return self.getFile().isRegularFile();
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, doc = "return stat_result object for the entry; cached per entry")
    @GenerateNodeFactory
    abstract static class StatNode extends PythonUnaryBuiltinNode {
        private static final String STAT_RESULT = "__stat_result__";

        @Specialization
        Object test(VirtualFrame frame, PDirEntry self,
                        @Cached("create()") ReadAttributeFromObjectNode readNode,
                        @Cached("create()") WriteAttributeToObjectNode writeNode,
                        @Cached("create()") PosixModuleBuiltins.StatNode statNode) {
            Object stat_result = readNode.execute(self, STAT_RESULT);
            if (stat_result == PNone.NO_VALUE) {
                stat_result = statNode.execute(frame, self.getFile().getAbsoluteFile().getPath(), PNone.NO_VALUE);
                writeNode.execute(self, STAT_RESULT, stat_result);
            }
            return stat_result;
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PathNode extends PythonUnaryBuiltinNode {
        @Specialization
        String path(PDirEntry self) {
            return self.getFile().getPath();
        }
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        String path(PDirEntry self) {
            return self.getName();
        }
    }
}
