/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.zipimporter;

import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.CompileNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.builtins.objects.object.PythonObject;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter.ModuleCodeData;
import com.oracle.graal.python.builtins.objects.zipimporter.PZipImporter.ModuleInfo;
import com.oracle.graal.python.nodes.SpecialAttributeNames;
import com.oracle.graal.python.nodes.SpecialMethodNames;
import com.oracle.graal.python.nodes.attributes.LookupAttributeInMRONode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetLazyClassNode;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToPathNode;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PZipImporter)
public class ZipImporterBuiltins extends PythonBuiltins {

    private static final String INIT_WAS_NOT_CALLED = "zipimporter.__init__() wasn't called";

    /**
     * This stream is need to find locations of zip entries in the zip file. The main purpose of
     * this is to find location of the first local file header in the zipfile, which doesn't have to
     * be as ZipInputStream expects. Some of zip files (like .egg files) don't start with location
     * signature `PK\003\004` but with a code, that should be executed.
     *
     * In such case ZipInptuStream doesn't work, it just expects that the stream starts with the
     * location signature.
     *
     * This stream also improve performance of unzipping files in ZipImporter case. A content of
     * file is obtained from the zip, when it's needed (imported). The locations of zip entry
     * positions are cached in the zip directory cache. When content of a file is needed, then
     * previous zip entries are skipped and ZipInputStream is created from the required position.
     *
     * New ZipInputStream from this stream can be created after calling findFirstEntryPostion.
     *
     * It locates all occurrences of LOC signatures, even if a signature is a part of a content of a
     * file. This situation has to be handled separately.
     */
    private static class LOCZipEntryStream extends InputStream {
        // states of the simple lexer
        private static final byte AFTER_P = 1;
        private static final byte AFTER_PK = 2;
        private static final byte AFTER_PK3 = 3;
        private static final byte BEFORE_P = 0;

        private byte state = BEFORE_P;  // the default state
        private static final byte[] LOC_SIG = new byte[]{80, 75, 3, 4}; // zip location signature

        private final InputStream in;
        long pos = 0;                  // position in the input stream
        private boolean readFirstLoc;  // is the first location detected?
        List<Long> positions;          // store the locations

        public LOCZipEntryStream(InputStream in) {
            this.readFirstLoc = false;
            this.positions = new ArrayList<>();
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            if (readFirstLoc) {
                // This expect that the bytes of the first LOC was consumed by this stream
                // (due to calling findFirstEntryPosition) and now the stream
                // has to push back the LOC bytes
                int index = (int) (pos - positions.get(0));
                if (index < LOC_SIG.length) {
                    pos++;
                    return LOC_SIG[index];
                }
                readFirstLoc = false;  // never do it again
            }
            int ch = in.read();
            pos++;
            switch (state) {
                case BEFORE_P:
                    if (ch == LOC_SIG[0]) {
                        state = AFTER_P;
                    }
                    break;
                case AFTER_P:
                    if (ch == LOC_SIG[1]) {
                        state = AFTER_PK;
                    } else {
                        state = ch != LOC_SIG[0] ? BEFORE_P : AFTER_P;
                    }
                    break;
                case AFTER_PK:
                    if (ch == LOC_SIG[2]) {
                        state = AFTER_PK3;
                    } else {
                        state = ch != LOC_SIG[0] ? BEFORE_P : AFTER_P;
                    }
                    break;
                case AFTER_PK3:
                    if (ch == LOC_SIG[3]) {
                        positions.add(pos - 4);  // store the LOC position
                        state = BEFORE_P;
                    } else {
                        state = ch != LOC_SIG[0] ? BEFORE_P : AFTER_P;
                    }
            }
            return ch;
        }

        void findFirstEntryPosition() throws IOException {
            while (positions.isEmpty() && read() != -1) {
                // do nothing here, just read until the first LOC is found
            }
            if (!positions.isEmpty()) {
                pos -= 4;
                readFirstLoc = true;
            }
        }
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZipImporterBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, minNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {
        @Child private GetLazyClassNode getClassNode;
        @Child private LookupAttributeInMRONode findFspathNode;
        @Child private CastToPathNode convertPathNode;

        @CompilerDirectives.TruffleBoundary
        private void initZipImporter(PZipImporter self, String path) {
            if (path == null || path.isEmpty()) {
                throw raise(PythonErrorType.ZipImportError, "archive path is empty");
            }

            TruffleFile tfile = getContext().getEnv().getPublicTruffleFile(path);
            String prefix = "";
            String archive = "";
            while (true) {
                boolean isRegularFile;
                try {
                    isRegularFile = tfile.isRegularFile();
                } catch (SecurityException e) {
                    isRegularFile = false;
                }
                if (isRegularFile) {
                    // we don't have to store absolute path
                    archive = tfile.getPath();
                    break;
                }
                TruffleFile parentFile = tfile.getParent();
                if (parentFile == null) {
                    break;
                }
                prefix = tfile.getName() + getContext().getEnv().getFileNameSeparator() + prefix;
                tfile = parentFile;
            }

            boolean existsAndIsRegular;
            try {
                existsAndIsRegular = tfile.exists() && tfile.isRegularFile();
            } catch (SecurityException e) {
                existsAndIsRegular = false;
            }

            if (existsAndIsRegular) {
                Object files = self.getZipDirectoryCache().getItem(path);
                if (files == null) {
                    // fill the cache
                    PDict filesDict = factory().createDict();
                    ZipInputStream zis = null;
                    LOCZipEntryStream locis = null;
                    try {
                        locis = new LOCZipEntryStream(tfile.newInputStream(StandardOpenOption.READ));
                        locis.findFirstEntryPosition(); // find location of the first zip entry
                        if (locis.positions.isEmpty()) {
                            // no PK\003\004 found -> not a correct zip file
                            throw raise(PythonErrorType.ZipImportError, "not a Zip file: '%s'", archive);
                        }
                        zis = new ZipInputStream(locis); // and create new ZipInput stream from this
                                                         // location
                        ZipEntry entry;

                        // help variable to handle case when there LOC is in content of a file
                        long lastZipEntryCSize = 0;
                        long lastZipEntryPos = 0;
                        int lastZipLocFileHeaderSize = 0;
                        long zipEntryPos = 0;

                        byte[] extraField;
                        while ((entry = zis.getNextEntry()) != null) {
                            if (!locis.positions.isEmpty()) {
                                zipEntryPos = locis.positions.remove(0);
                                // handles situation when the local file signature is
                                // in the content of a file
                                while (lastZipEntryPos + lastZipEntryCSize + lastZipLocFileHeaderSize > zipEntryPos) {
                                    zipEntryPos = locis.positions.remove(0);
                                }
                            } else {
                                throw raise(PythonErrorType.ZipImportError, "cannot handle Zip file: '%s'", archive);
                            }

                            PTuple tuple = factory().createTuple(new Object[]{
                                            tfile.getPath() + getContext().getEnv().getFileNameSeparator() + entry.getName(),
                                            // for our implementation currently we don't need these
                                            // these properties to store there. Keeping them for
                                            // compatibility.
                                            entry.getMethod(),
                                            lastZipEntryCSize = entry.getCompressedSize(),
                                            entry.getSize(),
                                            entry.getLastModifiedTime().toMillis(),
                                            entry.getCrc(),
                                            // store the entry position for faster reading content
                                            lastZipEntryPos = zipEntryPos
                            });
                            filesDict.setItem(entry.getName(), tuple);
                            // count local file header from the last zipentry
                            lastZipLocFileHeaderSize = 30 + entry.getName().length();
                            extraField = entry.getExtra();
                            if (extraField != null) {
                                lastZipLocFileHeaderSize += extraField.length;
                            }
                        }
                    } catch (IOException ex) {
                        throw raise(PythonErrorType.ZipImportError, "not a Zip file: '%s'", archive);
                    } catch (SecurityException ex) {
                        throw raise(PythonErrorType.ZipImportError, "security exception while reading: '%s'", archive);
                    } finally {
                        if (zis != null) {
                            try {
                                zis.close();
                            } catch (IOException e) {
                                // just ignore it.
                            }
                        } else {
                            if (locis != null) {
                                try {
                                    locis.close();
                                } catch (IOException e) {
                                    // just ignore it.
                                }
                            }
                        }
                    }
                    files = filesDict;
                    self.getZipDirectoryCache().setItem(path, files);
                }
                self.setArchive(archive);
                self.setPrefix(prefix);
                self.setFiles((PDict) files);

            } else {
                throw raise(PythonErrorType.ZipImportError, "not a Zip file: '%s'", archive);
            }

        }

        @Specialization
        public PNone init(PZipImporter self, String path) {
            initZipImporter(self, path);
            return PNone.NONE;
        }

        @Specialization
        @CompilerDirectives.TruffleBoundary
        public PNone init(PZipImporter self, PBytes path,
                        @Cached("create()") SequenceStorageNodes.GetItemNode getItemNode) {
            SequenceStorage store = path.getSequenceStorage();
            int len = store.length();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                BytesUtils.byteRepr(sb, (byte) getItemNode.executeInt(store, i));
            }
            initZipImporter(self, sb.toString());
            return PNone.NONE;
        }

        @Specialization
        public PNone init(VirtualFrame frame, PZipImporter self, PythonObject path) {
            // at first we need to find out, whether path object has __fspath__ method
            if (getClassNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                getClassNode = insert(GetLazyClassNode.create());
                CompilerDirectives.transferToInterpreterAndInvalidate();
                findFspathNode = insert(LookupAttributeInMRONode.create(SpecialMethodNames.__FSPATH__));
            }
            Object result = findFspathNode.execute(getClassNode.execute(path));
            if (result == PNone.NO_VALUE) {
                // there is no __fspath__ method -> raise the exception
                notPossilbeInit(self, path);
            }
            // use the value of __fspath__ method
            if (convertPathNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                convertPathNode = insert(CastToPathNode.create());
            }
            initZipImporter(self, convertPathNode.execute(frame, path));
            return PNone.NONE;
        }

        @Fallback
        public PNone notPossilbeInit(@SuppressWarnings("unused") Object self, Object path) {
            throw raise(PythonErrorType.TypeError, "expected str, bytes or os.PathLike object, not %p", path);
        }

    }

    @Builtin(name = __STR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class StrNode extends PythonUnaryBuiltinNode {

        @Specialization
        @TruffleBoundary
        public String doit(PZipImporter self) {
            String archive = self.getArchive();
            String prefix = self.getPrefix();
            StringBuilder sb = new StringBuilder("<zipimporter object \"");
            if (archive == null) {
                sb.append("???");
            } else if (prefix != null && !prefix.isEmpty()) {
                sb.append(archive);
                sb.append(getContext().getEnv().getPathSeparator());
                sb.append(prefix);
            } else {
                sb.append(archive);
            }
            sb.append("\">");
            return sb.toString();
        }

    }

    @Builtin(name = __REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReprNode extends StrNode {

    }

    @Builtin(name = "find_module", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class FindModuleNode extends PythonTernaryBuiltinNode {
        /**
         *
         * @param self
         * @param fullname
         * @param path This optional argument is ignored. It’s there for compatibility with the
         *            importer protocol.
         * @return the zipimporter or none, if the module is not found
         */
        @Specialization
        public Object doit(PZipImporter self, String fullname, @SuppressWarnings("unused") Object path,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            return self.findModule(fullname) == null ? PNone.NONE : self;
        }

    }

    @Builtin(name = "get_code", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBinaryBuiltinNode {

        @Child private CompileNode compileNode;

        @Specialization
        public PCode doit(VirtualFrame frame, PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            if (compileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                compileNode = insert(CompileNode.create(false));
            }
            ModuleCodeData md;
            try {
                md = self.getModuleCode(fullname);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
            if (canNotFind.profile(md == null)) {
                throw raise(PythonErrorType.ZipImportError, " can't find module '%s'", fullname);
            }
            PCode code = compileNode.execute(frame, md.code, md.path, "exec", 0, false, -1);
            return code;
        }

        public static GetCodeNode create() {
            return ZipImporterBuiltinsFactory.GetCodeNodeFactory.create();
        }
    }

    @Builtin(name = "get_data", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetDataNode extends PythonBinaryBuiltinNode {

        @Specialization
        @CompilerDirectives.TruffleBoundary
        public PBytes doit(PZipImporter self, String pathname) {
            if (self.getPrefix() == null) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            String archive = self.getArchive();
            int len = archive.length();
            int index = 0;
            if (pathname.startsWith(archive) && pathname.substring(len).startsWith(getContext().getEnv().getFileNameSeparator())) {
                index = len + 1;
            }
            String key = pathname.substring(index, pathname.length());
            if (key.isEmpty()) {
                throw raise(PythonErrorType.OSError, "%s", pathname);
            }
            PTuple tocEntry = (PTuple) self.getFiles().getItem(key);
            if (tocEntry == null) {
                throw raise(PythonErrorType.OSError, "%s", pathname);
            }
            long fileSize = (long) tocEntry.getArray()[3];
            if (fileSize < 0) {
                throw raise(PythonErrorType.ZipImportError, "negative data size");
            }
            long streamPosition = (long) tocEntry.getArray()[6];
            ZipInputStream zis = null;
            TruffleFile tfile = getContext().getEnv().getPublicTruffleFile(archive);
            try (InputStream in = tfile.newInputStream(StandardOpenOption.READ)) {
                in.skip(streamPosition); // we can fast skip bytes, because there is cached position
                                         // of the zip entry
                zis = new ZipInputStream(in);
                ZipEntry entry = zis.getNextEntry();
                if (entry == null || !entry.getName().equals(key)) {
                    throw raise(PythonErrorType.ZipImportError, "zipimport: wrong cached file position");
                }
                int byteSize = (int) fileSize;
                if (byteSize != fileSize) {
                    throw raise(PythonErrorType.ZipImportError, "zipimport: cannot read archive members large than 2GB");
                }
                byte[] bytes = new byte[byteSize];
                int bytesRead = 0;
                while (bytesRead < byteSize) {
                    bytesRead += zis.read(bytes, bytesRead, byteSize - bytesRead);
                }
                zis.close();
                return factory().createBytes(bytes);
            } catch (IOException e) {
                throw raise(PythonErrorType.ZipImportError, "zipimport: can't read data");
            } finally {
                if (zis != null) {
                    try {
                        zis.close();
                    } catch (IOException e) {
                        // just ignore it.
                    }
                }
            }
        }
    }

    @Builtin(name = "get_filename", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetFileNameNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doit(VirtualFrame frame, PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            ModuleCodeData moduleCodeData;
            try {
                moduleCodeData = self.getModuleCode(fullname);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
            if (canNotFind.profile(moduleCodeData == null)) {
                throw raise(PythonErrorType.ZipImportError, " can't find module '%s'", fullname);
            }
            return moduleCodeData.path;
        }

    }

    @Builtin(name = "get_source", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetSourceNode extends PythonBinaryBuiltinNode {

        @Specialization
        public String doit(VirtualFrame frame, PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            ModuleCodeData md;
            try {
                md = self.getModuleCode(fullname);
            } catch (IOException e) {
                throw raiseOSError(frame, OSErrorEnum.EIO, e);
            }
            if (canNotFind.profile(md == null)) {
                throw raise(PythonErrorType.ZipImportError, "can't find module '%s'", fullname);
            }
            return md.code;
        }

    }

    @Builtin(name = "is_package", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class IsPackageNode extends PythonBinaryBuiltinNode {

        @Specialization
        public boolean doit(PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            ModuleInfo moduleInfo = self.getModuleInfo(fullname);
            if (canNotFind.profile(moduleInfo == ModuleInfo.NOT_FOUND)) {
                throw raise(PythonErrorType.ZipImportError, "can't find module '%s'", fullname);
            }
            return moduleInfo == ModuleInfo.PACKAGE;
        }

    }

    @Builtin(name = "load_module", minNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class LoadModuleNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doit(VirtualFrame frame, PZipImporter self, String fullname,
                        @Cached("create()") GetCodeNode getCodeNode,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            PCode code = getCodeNode.doit(frame, self, fullname, canNotFind, initWasNotCalled);

            PythonModule sysModule = getCore().lookupBuiltinModule("sys");
            PDict sysModules = (PDict) sysModule.getAttribute("modules");
            PythonModule module = (PythonModule) sysModules.getItem(fullname);
            if (module == null) {
                module = factory().createPythonModule(fullname);
                sysModules.setItem(fullname, module);
            }

            module.setAttribute(SpecialAttributeNames.__LOADER__, self);
            module.setAttribute(SpecialAttributeNames.__FILE__, code.getFilename());
            if (ModuleInfo.PACKAGE == self.getModuleInfo(fullname)) {
                PList list = factory().createList(new Object[]{self.makePackagePath(fullname)});
                module.setAttribute(SpecialAttributeNames.__PATH__, list);
            }

            code.getRootCallTarget().call(PArguments.withGlobals(module));
            return module;
        }

    }

    @Builtin(name = "archive", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ArchiveNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getArchive();
        }

    }

    @Builtin(name = "prefix", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class PrefixNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getPrefix();
        }

    }

    @Builtin(name = "_files", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FilesNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getFiles();
        }

    }

}
