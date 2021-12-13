package com.oracle.graal.python.frozen;

import java.util.HashMap;
import java.util.Map;

public final class FrozenModules {
    public static Map<String, PythonFrozenModule> frozenModules = createFrozenModulesMap();

    private static Map<String, PythonFrozenModule> createFrozenModulesMap() {
        Map<String, PythonFrozenModule> frozenModules = new HashMap<String, PythonFrozenModule>();
        frozenModules.put("abc", new PythonFrozenModule("abc", FrozenAbc.abcByteCode, FrozenAbc.abcByteCodeSize));
        frozenModules.put("io", new PythonFrozenModule("io", FrozenIo.ioByteCode, FrozenIo.ioByteCodeSize));
        return frozenModules;
    }
}