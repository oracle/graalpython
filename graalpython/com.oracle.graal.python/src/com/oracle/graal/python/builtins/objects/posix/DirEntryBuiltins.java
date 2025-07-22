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
package com.oracle.graal.python.builtins.objects.posix;

import static com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.opaquePathToBytes;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___FSPATH__;
import static com.oracle.graal.python.runtime.PosixConstants.AT_FDCWD;
import static com.oracle.graal.python.runtime.PosixConstants.DT_DIR;
import static com.oracle.graal.python.runtime.PosixConstants.DT_LNK;
import static com.oracle.graal.python.runtime.PosixConstants.DT_REG;
import static com.oracle.graal.python.runtime.PosixConstants.DT_UNKNOWN;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFDIR;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFLNK;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFMT;
import static com.oracle.graal.python.runtime.PosixConstants.S_IFREG;

import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.Slot;
import com.oracle.graal.python.annotations.Slot.SlotKind;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixFd;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.PosixPath;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.type.TpSlots;
import com.oracle.graal.python.lib.PyObjectReprAsTruffleStringNode;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.PosixSupport;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.object.PFactory;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PDirEntry)
public final class DirEntryBuiltins extends PythonBuiltins {

    public static final TpSlots SLOTS = DirEntryBuiltinsSlotsGen.SLOTS;

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return DirEntryBuiltinsFactory.getFactories();
    }

    @Builtin(name = "name", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NameNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object nameAsBytes(VirtualFrame frame, PDirEntry self,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile produceBytesProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                if (produceBytesProfile.profile(inliningTarget, self.produceBytes())) {
                    return opaquePathToBytes(posixLib.dirEntryGetName(context.getPosixSupport(), self.dirEntryData), posixLib, context.getPosixSupport(), context.getLanguage(inliningTarget));
                } else {
                    return posixLib.getPathAsString(context.getPosixSupport(), posixLib.dirEntryGetName(context.getPosixSupport(), self.dirEntryData));
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Slot(value = SlotKind.tp_repr, isComplex = true)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        static TruffleString repr(VirtualFrame frame, PDirEntry self,
                        @Bind Node inliningTarget,
                        @Cached NameNode nameNode,
                        @Cached PyObjectReprAsTruffleStringNode repr,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<DirEntry %s>", repr.execute(frame, inliningTarget, nameNode.execute(frame, self)));
        }
    }

    @GenerateInline
    @GenerateCached(false)
    abstract static class CachedPosixPathNode extends Node {

        abstract PosixPath execute(VirtualFrame frame, Node inliningTarget, PDirEntry self);

        @Specialization(guards = "self.pathCache != null")
        static PosixPath cached(PDirEntry self) {
            return self.pathCache;
        }

        @Specialization(guards = "self.pathCache == null")
        static PosixPath createBytes(VirtualFrame frame, Node inliningTarget, PDirEntry self,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached InlinedConditionProfile produceBytesProfile,
                        @Cached InlinedConditionProfile posixPathProfile,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            Object opaquePath;
            try {
                if (posixPathProfile.profile(inliningTarget, self.scandirPath instanceof PosixPath)) {
                    opaquePath = posixLib.dirEntryGetPath(context.getPosixSupport(), self.dirEntryData, ((PosixPath) self.scandirPath).value);
                } else {
                    opaquePath = posixLib.dirEntryGetName(context.getPosixSupport(), self.dirEntryData);
                }
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
            if (produceBytesProfile.profile(inliningTarget, self.produceBytes())) {
                self.pathCache = new PosixPath(opaquePathToBytes(opaquePath, posixLib, context.getPosixSupport(), context.getLanguage(inliningTarget)), opaquePath, true);
            } else {
                self.pathCache = new PosixPath(posixLib.getPathAsString(context.getPosixSupport(), opaquePath), opaquePath, false);
            }
            return self.pathCache;
        }
    }

    @Builtin(name = "path", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class PathNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object path(VirtualFrame frame, PDirEntry self,
                        @Bind Node inliningTarget,
                        @Cached CachedPosixPathNode cachedPosixPathNode) {
            return cachedPosixPathNode.execute(frame, inliningTarget, self).originalObject;
        }
    }

    @Builtin(name = J___FSPATH__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class FspathNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object fspath(VirtualFrame frame, PDirEntry self,
                        @Cached PathNode pathNode) {
            return pathNode.execute(frame, self);
        }
    }

    @Builtin(name = "inode", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class InodeNode extends PythonUnaryBuiltinNode {
        @Specialization
        long inode(VirtualFrame frame, PDirEntry self,
                        @Bind Node inliningTarget,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            try {
                return posixLib.dirEntryGetInode(getPosixSupport(), self.dirEntryData);
            } catch (PosixException e) {
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    @Builtin(name = "stat", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {
                    "follow_symlinks"}, doc = "return stat_result object for the entry; cached per entry")
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class StatNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DirEntryBuiltinsClinicProviders.StatNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static Object stat(VirtualFrame frame, PDirEntry self, boolean followSymlinks,
                        @Cached StatHelperNode statHelperNode) {
            return statHelperNode.execute(frame, self, followSymlinks, false);
        }
    }

    @GenerateInline(false) // Footprint reduction 40 -> 21
    abstract static class StatHelperSimpleNode extends Node {

        abstract PTuple execute(VirtualFrame frame, PDirEntry self, boolean followSymlinks, boolean catchNoent);

        @Specialization(guards = {"followSymlinks", "self.statCache != null"})
        @SuppressWarnings("unused")
        static PTuple cachedStat(PDirEntry self, boolean followSymlinks, boolean catchNoent) {
            return self.statCache;
        }

        @Specialization(guards = {"!followSymlinks", "self.lstatCache != null"})
        @SuppressWarnings("unused")
        static PTuple cachedLStat(PDirEntry self, boolean followSymlinks, boolean catchNoent) {
            return self.lstatCache;
        }

        @Specialization(guards = {"followSymlinks", "self.statCache == null", "isSymlink"}, limit = "1")
        static PTuple uncachedStatWithSymlink(VirtualFrame frame, PDirEntry self, boolean followSymlinks, boolean catchNoent,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @SuppressWarnings("unused") @Cached IsSymlinkNode isSymlinkNode,
                        @SuppressWarnings("unused") @Bind("isSymlinkNode.executeBoolean(frame, self)") boolean isSymlink,
                        @Shared("cachedPosixPathNode") @Cached CachedPosixPathNode cachedPosixPathNode,
                        @Shared("positiveLongProfile") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            // There are two caches - one for `follow_symlinks=True` and the other for
            // 'follow_symlinks=False`. They are different only when the dir entry is a symlink.
            return uncachedLStatWithSymlink(frame, self, followSymlinks, catchNoent, inliningTarget, context, posixLib, cachedPosixPathNode, positiveLongProfile, constructAndRaiseNode);
        }

        @Specialization(guards = {"!followSymlinks", "self.lstatCache == null"})
        static PTuple uncachedLStatWithSymlink(VirtualFrame frame, PDirEntry self, boolean followSymlinks, boolean catchNoent,
                        @Bind Node inliningTarget,
                        @Bind PythonContext context,
                        @CachedLibrary("context.getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("cachedPosixPathNode") @Cached CachedPosixPathNode cachedPosixPathNode,
                        @Shared("positiveLongProfile") @Cached InlinedConditionProfile positiveLongProfile,
                        @Shared @Cached PConstructAndRaiseNode.Lazy constructAndRaiseNode) {
            PTuple res;
            int dirFd = self.scandirPath instanceof PosixFd ? ((PosixFd) self.scandirPath).fd : AT_FDCWD.value;
            PosixPath posixPath = cachedPosixPathNode.execute(frame, inliningTarget, self);
            try {
                long[] rawStat = posixLib.fstatat(context.getPosixSupport(), dirFd, posixPath.value, followSymlinks);
                res = PosixModuleBuiltins.createStatResult(inliningTarget, context.getLanguage(inliningTarget), positiveLongProfile, rawStat);
            } catch (PosixException e) {
                if (catchNoent && e.getErrorCode() == OSErrorEnum.ENOENT.getNumber()) {
                    return null;
                }
                throw constructAndRaiseNode.get(inliningTarget).raiseOSErrorFromPosixException(frame, e, posixPath.originalObject);
            }
            self.setStatCache(followSymlinks, res);
            return res;
        }
    }

    @GenerateInline(false) // Footprint reduction 44 -> 25
    abstract static class StatHelperNode extends StatHelperSimpleNode {
        @Specialization(guards = {"followSymlinks", "self.statCache == null", "!isSymlink"}, limit = "1")
        static PTuple uncachedStatWithSymlink(VirtualFrame frame, PDirEntry self, boolean followSymlinks, boolean catchNoent,
                        @SuppressWarnings("unused") @Cached IsSymlinkNode isSymlinkNode,
                        @SuppressWarnings("unused") @Bind("isSymlinkNode.executeBoolean(frame, self)") boolean isSymlink,
                        @Cached StatHelperSimpleNode recursiveNode) {
            // There are two caches - one for `follow_symlinks=True` and the other for
            // 'follow_symlinks=False`. They are different only when the dir entry is a symlink.
            // If it is not, they need to be the same, so we must make sure that fstatat() gets
            // called only once. The entry is not a symlink, so both stat caches need to have the
            // same value. Also, the `follow_symlinks=False` cache might already be filled
            // in. (In fact, the call to isSymlinkNode in the condition may fill it.)
            // So we call ourselves recursively to either use or fill that cache first, and
            // the `follow_symlinks=True` cache will be filled below.
            PTuple res = recursiveNode.execute(frame, self, false, catchNoent);
            self.setStatCache(followSymlinks, res);
            return res;
        }
    }

    abstract static class TestModeNode extends Node {

        private final long expectedMode;
        private final int expectedDirEntryType;

        protected TestModeNode(long expectedMode, int expectedDirEntryType) {
            this.expectedMode = expectedMode;
            this.expectedDirEntryType = expectedDirEntryType;
        }

        abstract boolean execute(VirtualFrame frame, PDirEntry self, boolean followSymlinks);

        @Specialization(guards = "followSymlinks")
        boolean testModeUsingStat(VirtualFrame frame, PDirEntry self, boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached StatHelperNode statHelperNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode) {
            PTuple statResult = statHelperNode.execute(frame, self, followSymlinks, true);
            if (statResult == null) {
                // file not found
                return false;
            }
            // TODO constants for stat_result indices
            long mode = (long) getItemScalarNode.execute(inliningTarget, statResult.getSequenceStorage(), 0) & S_IFMT.value;
            return mode == expectedMode;
        }

        @Specialization(guards = "!followSymlinks")
        boolean useTypeIfKnown(VirtualFrame frame, PDirEntry self, @SuppressWarnings("unused") boolean followSymlinks,
                        @Bind Node inliningTarget,
                        @Shared @Cached StatHelperNode statHelperNode,
                        @Shared @Cached SequenceStorageNodes.GetItemScalarNode getItemScalarNode,
                        @CachedLibrary(limit = "1") PosixSupportLibrary posixLib) {
            int entryType = posixLib.dirEntryGetType(PosixSupport.get(this), self.dirEntryData);
            if (entryType != DT_UNKNOWN.value) {
                return entryType == expectedDirEntryType;
            }
            return testModeUsingStat(frame, self, false, inliningTarget, statHelperNode, getItemScalarNode);
        }

        @NeverDefault
        static TestModeNode create(long expectedMode, int expectedDirEntryType) {
            return DirEntryBuiltinsFactory.TestModeNodeGen.create(expectedMode, expectedDirEntryType);
        }

        @NeverDefault
        static TestModeNode createLnk() {
            return create(S_IFLNK.value, DT_LNK.value);
        }

        @NeverDefault
        static TestModeNode createReg() {
            return create(S_IFREG.value, DT_REG.value);
        }

        @NeverDefault
        static TestModeNode createDir() {
            return create(S_IFDIR.value, DT_DIR.value);
        }
    }

    @Builtin(name = "is_symlink", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IsSymlinkNode extends PythonUnaryBuiltinNode {

        abstract boolean executeBoolean(VirtualFrame frame, PDirEntry self);

        @Specialization
        static boolean isSymlink(VirtualFrame frame, PDirEntry self,
                        @Cached("createLnk()") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, false);
        }
    }

    @Builtin(name = "is_file", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class IsFileNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DirEntryBuiltinsClinicProviders.IsFileNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean isFile(VirtualFrame frame, PDirEntry self, boolean followSymlinks,
                        @Cached("createReg()") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, followSymlinks);
        }
    }

    @Builtin(name = "is_dir", minNumOfPositionalArgs = 1, parameterNames = {"$self"}, keywordOnlyNames = {"follow_symlinks"})
    @ArgumentClinic(name = "follow_symlinks", conversion = ClinicConversion.Boolean, defaultValue = "true")
    @GenerateNodeFactory
    abstract static class IsDirNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return DirEntryBuiltinsClinicProviders.IsDirNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        static boolean isDir(VirtualFrame frame, PDirEntry self, boolean followSymlinks,
                        @Cached("createDir()") TestModeNode testModeNode) {
            return testModeNode.execute(frame, self, followSymlinks);
        }
    }

    @Builtin(name = "is_junction", minNumOfPositionalArgs = 1, parameterNames = {"$self"})
    @GenerateNodeFactory
    abstract static class IsJunctionNode extends PythonUnaryBuiltinNode {

        @Specialization
        static boolean isJunction(@SuppressWarnings("unused") PDirEntry self) {
            return false;
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object classGetItem(Object cls, Object key,
                        @Bind PythonLanguage language) {
            return PFactory.createGenericAlias(language, cls, key);
        }
    }
}
