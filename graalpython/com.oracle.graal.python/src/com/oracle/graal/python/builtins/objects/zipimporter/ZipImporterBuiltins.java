/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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

import static com.oracle.graal.python.nodes.SpecialMethodNames.__STR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__REPR__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.__INIT__;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions.CompileNode;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesUtils;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.code.PCode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
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
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.profiles.ConditionProfile;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PZipImporter)
public class ZipImporterBuiltins extends PythonBuiltins {

    private static final String INIT_WAS_NOT_CALLED = "zipimporter.__init__() wasn't called";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZipImporterBuiltinsFactory.getFactories();
    }

    @Builtin(name = __INIT__, fixedNumOfPositionalArgs = 1)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonBinaryBuiltinNode {
        @Child private GetLazyClassNode getClassNode;
        @Child private LookupAttributeInMRONode findFspathNode;
        @Child private PosixModuleBuiltins.ConvertPathlikeObjectNode convertPathNode;

        @CompilerDirectives.TruffleBoundary
        private void initZipImporter(PZipImporter self, String path) {
            if (path == null || path.isEmpty()) {
                throw raise(PythonErrorType.ZipImportError, "archive path is empty");
            }

            File file = new File(path);
            String prefix = "";
            String archive = "";
            while (true) {
                File fullPathFile = new File(file.getPath());
                try {
                    if (fullPathFile.isFile()) {
                        archive = file.getPath();
                        break;
                    }
                } catch (SecurityException se) {
                    // continue
                }

                // back up one path element
                File parentFile = file.getParentFile();
                if (parentFile == null) {
                    break;
                }

                prefix = file.getName() + PZipImporter.SEPARATOR + prefix;
                file = parentFile;
            }
            ZipFile zipFile = null;

            if (file.exists() && file.isFile()) {
                try {
                    zipFile = new ZipFile(file);
                } catch (IOException e) {
                    throw raise(PythonErrorType.ZipImportError, "not a Zip file");
                }
            }
            if (zipFile == null) {
                throw raise(PythonErrorType.ZipImportError, "not a Zip file");
            }
            Object files = self.getZipDirectoryCache().getItem(path);
            if (files == null) {
                PDict filesDict = factory().createDict();
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    PTuple tuple = factory().createTuple(new Object[]{
                                    zipFile.getName() + PZipImporter.SEPARATOR + entry.getName(),
                                    // for our implementation currently we don't need these
                                    // these properties to store there. Keeping them for
                                    // compatibility.
                                    entry.getMethod(),
                                    entry.getCompressedSize(),
                                    entry.getSize(),
                                    entry.getLastModifiedTime().toMillis(),
                                    entry.getCrc()});
                    filesDict.setItem(entry.getName(), tuple);
                }
                files = filesDict;
                self.getZipDirectoryCache().setItem(path, files);
            }
            self.setArchive(archive);
            self.setPrefix(prefix);
            self.setFiles((PDict) files);
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
        public PNone init(PZipImporter self, PythonObject path) {
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
                convertPathNode = insert(PosixModuleBuiltins.ConvertPathlikeObjectNode.create());
            }
            initZipImporter(self, convertPathNode.execute(path));
            return PNone.NONE;
        }

        @Fallback
        public PNone notPossilbeInit(@SuppressWarnings("unused") Object self, Object path) {
            throw raise(PythonErrorType.TypeError, "expected str, bytes or os.PathLike object, not %p", path);
        }

    }

    @Builtin(name = __STR__, fixedNumOfPositionalArgs = 1)
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
                sb.append(File.pathSeparator);
                sb.append(prefix);
            } else {
                sb.append(archive);
            }
            sb.append("\">");
            return sb.toString();
        }

    }

    @Builtin(name = __REPR__, fixedNumOfPositionalArgs = 1)
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

    @Builtin(name = "get_code", fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetCodeNode extends PythonBinaryBuiltinNode {

        @Child private CompileNode compileNode;

        @Specialization
        public PCode doit(PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            if (compileNode == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                compileNode = insert(CompileNode.create());
            }
            ModuleCodeData md = self.getModuleCode(fullname);
            if (canNotFind.profile(md == null)) {
                throw raise(PythonErrorType.ZipImportError, " can't find module '%s'", fullname);
            }
            PCode code = compileNode.execute(md.code, md.path, "exec", 0, false, -1);
            return code;
        }

        public static GetCodeNode create() {
            return ZipImporterBuiltinsFactory.GetCodeNodeFactory.create();
        }
    }

    @Builtin(name = "get_data", fixedNumOfPositionalArgs = 2)
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
            if (pathname.startsWith(archive) && pathname.charAt(len) == File.separatorChar) {
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
            try {
                ZipFile zip = new ZipFile(archive);
                ZipEntry entry = zip.getEntry(key);
                InputStream in = zip.getInputStream(entry);
                int byteSize = (int) fileSize;
                if (byteSize != fileSize) {
                    throw raise(PythonErrorType.ZipImportError, "zipimport: cannot read archive members large than 2GB");
                }
                byte[] bytes = new byte[byteSize];
                int bytesRead = 0;
                while (bytesRead < byteSize) {
                    bytesRead += in.read(bytes, bytesRead, byteSize - bytesRead);
                }
                in.close();
                return factory().createBytes(bytes);
            } catch (IOException e) {
                throw raise(PythonErrorType.ZipImportError, "zipimport: can't read data");
            }
        }

    }

    @Builtin(name = "get_filename", fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetFileNameNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            ModuleCodeData moduleCodeData = self.getModuleCode(fullname);
            if (canNotFind.profile(moduleCodeData == null)) {
                throw raise(PythonErrorType.ZipImportError, " can't find module '%s'", fullname);
            }
            return moduleCodeData.path;
        }

    }

    @Builtin(name = "get_source", fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class GetSourceNode extends PythonBinaryBuiltinNode {

        @Specialization
        public String doit(PZipImporter self, String fullname,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            if (initWasNotCalled.profile(self.getPrefix() == null)) {
                throw raise(PythonErrorType.ValueError, INIT_WAS_NOT_CALLED);
            }
            ModuleCodeData md = self.getModuleCode(fullname);
            if (canNotFind.profile(md == null)) {
                throw raise(PythonErrorType.ZipImportError, "can't find module '%s'", fullname);
            }
            return md.code;
        }

    }

    @Builtin(name = "is_package", fixedNumOfPositionalArgs = 2)
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

    @Builtin(name = "load_module", fixedNumOfPositionalArgs = 2)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    public abstract static class LoadModuleNode extends PythonBinaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self, String fullname,
                        @Cached("create()") GetCodeNode getCodeNode,
                        @Cached("createBinaryProfile()") ConditionProfile canNotFind,
                        @Cached("createBinaryProfile()") ConditionProfile initWasNotCalled) {
            PCode code = getCodeNode.doit(self, fullname, canNotFind, initWasNotCalled);

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

    @Builtin(name = "archive", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class ArchiveNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getArchive();
        }

    }

    @Builtin(name = "prefix", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class PrefixNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getPrefix();
        }

    }

    @Builtin(name = "_files", fixedNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class FilesNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object doit(PZipImporter self) {
            return self.getFiles();
        }

    }

}
