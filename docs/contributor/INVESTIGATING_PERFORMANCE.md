# Investigating GraalPy Performance

First, make sure to build GraalPy with debug symbols.
`export CFLAGS=-g` before doing a fresh `mx build` adds the debug symbols flags to all our C extension libraries.
When you build a native image, use `find` to get the `.debug` file somewhere from the `mxbuild` directory tree, it's called something like `libpythonvm.so.debug`.
Make sure to get that one and put it next to the `libpythonvm.so` in the Python standalone so that tools can pick it up.

## Peak Performance

[Truffle docs](https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/Optimizing/) under graal/truffle/docs/Optimizing.md are a good starting point.
They describe how to start with the profiler, especially useful is the [flamegraph](https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/Profiling/#creating-a-flame-graph-from-cpu-sampler).
This gives you a high-level idea of where time is spent.
Note that currently (GR-58204) executions with native extensions may be less accurate.

In GraalPy's case the flamegraph is also useful to compare performance to CPython.
[Py-spy](https://pypi.org/project/py-spy/) is pretty good for that, since it generates a flamegraph that is sufficiently comparable.
Note that `py-spy` is a sampling profiler that accesses CPython internals, so it often does not work on the latest CPython, use a bit older one.

```
py-spy record -n -r 100 -o pyspy.svg -- foo.py
```

Once you have identified something that takes way too long on GraalPy as compared to CPython, follow the Truffle guide.

When you use [IGV](https://www.graalvm.org/tools/igv/), an interesting thing about debugging deoptimizations with IGV is that if you trace deopts as per the Truffle document linked above, search for "JVMCI: installed code name=".
If the name ends with "#2" it's a second tier compilation.
You might notice the presence of a `debugId` or `debug_id` in the output of these options.
That id can be searched via `id=NUMBER`, `idx=NUMBER` or `debugId=NUMBER` in IGV's `Search in Nodes` search box, then selecting `Open Search for node NUMBER in Node Searches window`, and then clicking the `Search in following phases` button.
Another useful thing to know is the `compile_id` matches the `compilationId` in IGVs "properties" view of the dumped graph.

[Proftool](https://github.com/graalvm/mx/blob/master/README-proftool.md) can also be helpful.
Note that this is not really prepared for language launchers, if it doesn't work, just get the commandline and build the arguments manually.

## Interpreter Performance

For interpreter performance async profiler is good and also allows for some visualizations.
Backtrace view and flat views are good.
It is only for JVM executions (not native images).
Download async-profiler and make sure you also have debug symbols in your C extensions.
Use these options:

```
--vm.agentpath:/path/to/async-profiler/lib/libasyncProfiler.so=start,event=cpu,file=profile.html' --vm.XX:+UnlockDiagnosticVMOptions --vm.XX:+DebugNonSafepoints
```

Another very useful tool is [gprofng](https://blogs.oracle.com/linux/post/gprofng-the-next-generation-gnu-profiling-tool), it is part of binutils these days.
If you have debug symbols, it works quite well with JVM launchers since it understands Hotspot frames, but also works fine with native images.
You might run into a bug with our language launchers: https://sourceware.org/bugzilla/show_bug.cgi?id=32110 The patch in that bugreport from me (Tim) -- while not entirely correct and not passing their testsuite -- lets you review recorded profiles (the bug only manifests when viewing a recorded profile).
What's nice about gprofng is that it can attribute time spent to Java bytecodes, so you can even profile huge methods like bytecode loops that, for example, the DSL has generated.

For SVM builds it is very useful to look at Truffle's [HostInlining](https://www.graalvm.org/graalvm-as-a-platform/language-implementation-framework/HostOptimization/) docs and check the debugging section there.
This helps ensure that expected code is inlined (or not).
When I identify something that takes long using gprofng, for example, I find it useful to check if that stuff is inlined as expected on SVM during the HostInliningPhase.

Supposedly Intel VTune and Oracle Developer Studio work well, but I haven't tried them.
