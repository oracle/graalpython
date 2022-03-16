package com.oracle.graal.python.nodes.bytecode;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.__FILE__;
import static com.oracle.graal.python.nodes.SpecialAttributeNames.__NAME__;

import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectLookupAttr;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.statement.AbstractImportNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaStringNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;

@GenerateUncached
public abstract class ImportFromNode extends PNodeWithContext {
    public abstract Object execute(Frame frame, Object module, String name);

    @Specialization
    Object doImport(VirtualFrame frame, Object module, String name,
                    @Cached PyObjectLookupAttr lookupAttr,
                    @Cached BranchProfile maybeCircularProfile) {
        Object result = lookupAttr.execute(frame, module, name);
        if (result != PNone.NO_VALUE) {
            return result;
        }
        maybeCircularProfile.enter();
        return tryResolveCircularImport(module, name);
    }

    @TruffleBoundary
    private Object tryResolveCircularImport(Object module, String name) {
        Object pkgnameObj;
        Object pkgpathObj = null;
        String pkgname = "<unknown module name>";
        String pkgpath = "unknown location";
        try {
            pkgnameObj = PyObjectGetAttr.getUncached().execute(null, module, __NAME__);
            pkgname = CastToJavaStringNode.getUncached().execute(pkgnameObj);
        } catch (PException | CannotCastException e) {
            pkgnameObj = null;
        }
        if (pkgnameObj != null) {
            try {
                String fullname = pkgname + "." + name;
                return PyObjectGetItem.getUncached().execute(null, getContext().getSysModules(), fullname);
            } catch (PException e) {
                e.expectAttributeError(IsBuiltinClassProfile.getUncached());
            }
            try {
                pkgpathObj = PyObjectGetAttr.getUncached().execute(null, module, __FILE__);
                pkgpath = CastToJavaStringNode.getUncached().execute(pkgpathObj);
            } catch (PException | CannotCastException e) {
                pkgpathObj = null;
            }
        }
        if (pkgnameObj == null) {
            pkgnameObj = PNone.NONE;
        }
        if (pkgpathObj != null && AbstractImportNode.PyModuleIsInitializing.getUncached().execute(null, module)) {
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME_CIRCULAR, name, pkgname, pkgpath);
        } else {
            if (pkgpathObj == null) {
                pkgnameObj = PNone.NONE;
            }
            throw PConstructAndRaiseNode.getUncached().raiseImportError(null, pkgnameObj, pkgpathObj, ErrorMessages.CANNOT_IMPORT_NAME, name, pkgname, pkgpath);
        }
    }

    public static ImportFromNode create() {
        return ImportFromNodeGen.create();
    }

    public static ImportFromNode getUncached() {
        return ImportFromNodeGen.getUncached();
    }
}
