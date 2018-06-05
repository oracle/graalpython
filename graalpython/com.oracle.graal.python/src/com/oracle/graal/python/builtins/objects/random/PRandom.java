package com.oracle.graal.python.builtins.objects.random;

import java.util.Random;

import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.type.PythonClass;

public class PRandom extends PythonBuiltinObject {
    private static class PythonRandom extends Random {
        private static final long serialVersionUID = 1L;

        long getSeed() {
            int nextseed = this.next(48); // 48 magic number of bits shifted away in superclass
            this.setSeed(nextseed);
            return nextseed;
        }
    }

    private PythonRandom javaRandom = new PythonRandom();

    public PRandom(PythonClass cls) {
        super(cls);
    }

    public void setSeed(long seed) {
        javaRandom.setSeed(seed);
    }

    public long getSeed() {
        return javaRandom.getSeed();
    }

    public long nextLong() {
        return javaRandom.nextLong();
    }

    public double nextDouble() {
        return javaRandom.nextDouble();
    }

    public Random getJavaRandom() {
        return javaRandom;
    }

    public void resetJavaRandom() {
        javaRandom = new PythonRandom();
    }
}
