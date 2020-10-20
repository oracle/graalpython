package com.oracle.graal.python.builtins.objects.cext.capi;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.objects.cext.capi.CExtNodes.AsPythonObjectNode;
import com.oracle.graal.python.builtins.objects.cext.capi.PGetDynamicTypeNodeGen.GetSulongTypeNodeGen;
import com.oracle.graal.python.builtins.objects.object.PythonObjectLibrary;
import com.oracle.graal.python.builtins.objects.type.PythonAbstractClass;
import com.oracle.graal.python.builtins.objects.type.PythonBuiltinClass;
import com.oracle.graal.python.builtins.objects.type.PythonManagedClass;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetMroNode;
import com.oracle.graal.python.builtins.objects.type.TypeNodes.GetNameNode;
import com.oracle.graal.python.nodes.PNodeWithContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.CachedContext;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.library.CachedLibrary;

@GenerateUncached
abstract class PGetDynamicTypeNode extends PNodeWithContext {

    public abstract Object execute(PythonNativeWrapper obj);

    @Specialization(guards = "obj.isIntLike()")
    Object doIntLike(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getLongobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = "obj.isBool()")
    Object doBool(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getBoolobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization(guards = "obj.isDouble()")
    Object doDouble(@SuppressWarnings("unused") DynamicObjectNativeWrapper.PrimitiveNativeWrapper obj,
                    @Cached(value = "getFloatobjectType()", allowUncached = true) Object cachedSulongType) {
        return cachedSulongType;
    }

    @Specialization
    Object doGeneric(PythonNativeWrapper obj,
                    @Cached GetSulongTypeNode getSulongTypeNode,
                    @Cached AsPythonObjectNode getDelegate,
                    @CachedLibrary(limit = "3") PythonObjectLibrary lib) {
        return getSulongTypeNode.execute(lib.getLazyPythonClass(getDelegate.execute(obj)));
    }

    protected static Object getLongobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.PInt);
    }

    protected static Object getBoolobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.Boolean);
    }

    protected static Object getFloatobjectType() {
        return GetSulongTypeNodeGen.getUncached().execute(PythonBuiltinClassType.PFloat);
    }

    @GenerateUncached
    abstract static class GetSulongTypeNode extends PNodeWithContext {

        public abstract Object execute(Object clazz);

        @Specialization(guards = "clazz == cachedClass", limit = "10")
        Object doBuiltinCached(@SuppressWarnings("unused") PythonBuiltinClassType clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonBuiltinClassType cachedClass,
                        @SuppressWarnings("unused") @CachedContext(PythonLanguage.class) PythonContext context,
                        @Cached("getSulongTypeForBuiltinClass(clazz, context)") Object sulongType) {
            return sulongType;
        }

        @Specialization(replaces = "doBuiltinCached")
        Object doBuiltinGeneric(PythonBuiltinClassType clazz,
                        @CachedContext(PythonLanguage.class) PythonContext context) {
            return getSulongTypeForBuiltinClass(clazz, context);
        }

        @Specialization(assumptions = "singleContextAssumption()", guards = "clazz == cachedClass")
        Object doGeneric(@SuppressWarnings("unused") PythonManagedClass clazz,
                        @Cached("clazz") @SuppressWarnings("unused") PythonManagedClass cachedClass,
                        @Cached(value = "doGeneric(clazz)", allowUncached = true) Object sulongType) {
            return sulongType;
        }

        @Specialization
        Object doGeneric(PythonManagedClass clazz) {
            return getSulongTypeForClass(clazz);
        }

        protected Object getSulongTypeForBuiltinClass(PythonBuiltinClassType clazz, PythonContext context) {
            PythonBuiltinClass pythonClass = context.getCore().lookupType(clazz);
            return getSulongTypeForClass(pythonClass);
        }

        private static Object getSulongTypeForClass(PythonManagedClass pythonClass) {
            Object sulongType = pythonClass.getSulongType();
            if (sulongType == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                sulongType = findBuiltinClass(pythonClass);
                if (sulongType == null) {
                    throw new IllegalStateException("sulong type for " + GetNameNode.getUncached().execute(pythonClass) + " was not registered");
                }
            }
            return sulongType;
        }

        private static Object findBuiltinClass(PythonManagedClass pythonClass) {
            PythonAbstractClass[] mro = GetMroNode.getUncached().execute(pythonClass);
            Object sulongType = null;
            for (PythonAbstractClass superClass : mro) {
                if (superClass instanceof PythonManagedClass) {
                    sulongType = ((PythonManagedClass) superClass).getSulongType();
                    if (sulongType != null) {
                        pythonClass.setSulongType(sulongType);
                        break;
                    }
                }
            }
            return sulongType;
        }
    }
}
