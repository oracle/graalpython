package com.oracle.graal.python.frozen;

import java.util.HashMap;
import java.util.Map;


public final class FrozenModules {
    public static Map<String, PythonFrozenModule> frozenModules = createFrozenModulesMap();

    private static Map<String, PythonFrozenModule> createFrozenModulesMap() {
        Map<String, PythonFrozenModule> frozenModules = new HashMap<String, PythonFrozenModule>();
        frozenModules.put("io", new PythonFrozenModule("io", FrozenIo.ioByteCode, FrozenIo.ioByteCode.length));
        byte[][] abcByteCode = {FrozenAbc.abcByteCode, FrozenAbc2.abcByteCode, FrozenAbc3.abcByteCode};
        int abcByteCodeSize = FrozenAbc.abcByteCode.length + FrozenAbc2.abcByteCode.length + FrozenAbc3.abcByteCode.length;
        frozenModules.put("abc", new PythonFrozenModule("abc", abcByteCode , abcByteCodeSize));
        return frozenModules;
    }
}
