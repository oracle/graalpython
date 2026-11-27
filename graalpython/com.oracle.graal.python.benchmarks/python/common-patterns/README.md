# Common-pattern benchmarks

These microbenchmarks compare how GraalPy and GraalJS perform the same task using each
language’s usual APIs. Each `<name>.py` here pairs with `common-patterns/micro-<name>.js`
in the `js-benchmarks` repository. Compare the two timings for the same workload.

Controls measure work shared with a benchmark. For example, `sort-string-control`
copies and retains the same 4,096 strings as both string sorts and stops there.
Use its time as a baseline. Subtracting it gives only an estimate because the
compiler can optimize the two workloads differently.

At the configured input size, each invocation processes 4,096 elements. The suite covers arithmetic, branches,
sequence and object access, allocation, maps, function calls, strings, and sorting.
In `function-dispatch-4-args2`, for example, one call site cycles through four
functions, each taking two positional arguments.

| Benchmark | Timed workload |
| --- | --- |
| [`numeric-int`](numeric-int.py) | Apply eight rounds of `x*x + 3*x + 7` to each integer, masking to 15 bits after each round. |
| [`numeric-int-control`](numeric-int-control.py) | Read each integer by index and consume it. This also provides the indexed traversal baseline. |
| [`numeric-float`](numeric-float.py) | Apply eight rounds of `3.75 * x * (1.0 - x)` to each floating-point input. |
| [`numeric-float-control`](numeric-float-control.py) | Read each floating-point input by index and consume it. |
| [`branch-predictable`](branch-predictable.py) | Choose between `3*x + 1` and `5*x + 7` on ordered integers. The first half take one branch and the second half take the other. |
| [`branch-predictable-control`](branch-predictable-control.py) | Read and consume the ordered integers used by `branch-predictable`. |
| [`branch-mixed`](branch-mixed.py) | Apply the same conditional to shuffled integers. The two outcomes occur equally often in mixed order. |
| [`branch-mixed-control`](branch-mixed-control.py) | Read and consume the shuffled integers used by `branch-mixed`. |
| [`sequence-iterate`](sequence-iterate.py) | Traverse the integers directly with Python’s `for x in values` or JavaScript’s `for (const x of values)`. Compare with `numeric-int-control`. |
| [`indexed-update`](indexed-update.py) | Increment each integer modulo 32,768 and write it back by index. The updates persist between invocations. |
| [`field-access`](field-access.py) | Read, increment modulo 32,768, and write back two integer fields on each existing object. |
| [`field-access-control`](field-access-control.py) | Read and consume the same two fields on each object. |
| [`object-allocation`](object-allocation.py) | Create a two-field record for each pair of prepared values and store it in a reused output buffer. |
| [`object-allocation-control`](object-allocation-control.py) | Copy references to prebuilt records into the same kind of output buffer. |
| [`map-lookup`](map-lookup.py) | Look up existing entries in a Python `dict` or JavaScript `Map` using separately prepared strings with matching text. |
| [`map-lookup-control`](map-lookup-control.py) | Read and consume the prepared query strings. |
| [`map-update`](map-update.py) | Look up each integer value, increment it modulo 32,768, and store it under the same key. Compare with `map-lookup`. |
| [`map-insert`](map-insert.py) | Create a fresh dictionary or map and insert all prepared key/value pairs, including the cost of growing the container. |
| [`map-insert-control`](map-insert-control.py) | Write the same key/value pairs into an already populated dictionary or map. |
| [`function-dispatch-1`](function-dispatch-1.py) | Call one function through prepared references with zero arguments and consume the returned integer. |
| [`function-dispatch-1-control`](function-dispatch-1-control.py) | Read and consume the prepared references to one function. |
| [`function-dispatch-4`](function-dispatch-4.py) | Call four functions in rotation through prepared references with zero arguments and consume the returned integer. |
| [`function-dispatch-4-control`](function-dispatch-4-control.py) | Read and consume the prepared references to four functions in rotation. |
| [`function-dispatch-8`](function-dispatch-8.py) | Call eight functions in rotation through prepared references with zero arguments and consume the returned integer. |
| [`function-dispatch-8-control`](function-dispatch-8-control.py) | Read and consume the prepared references to eight functions in rotation. |
| [`function-dispatch-1-args1`](function-dispatch-1-args1.py) | Pass one index-derived argument to one function. The target returns its argument. |
| [`function-dispatch-4-args1`](function-dispatch-4-args1.py) | Pass one index-derived argument to four functions in rotation. The target returns its argument. |
| [`function-dispatch-8-args1`](function-dispatch-8-args1.py) | Pass one index-derived argument to eight functions in rotation. The target returns its argument. |
| [`function-dispatch-args1-control`](function-dispatch-args1-control.py) | Load a function reference and compute the one-argument result directly in the loop. Shared by all three target counts. |
| [`function-dispatch-1-args2`](function-dispatch-1-args2.py) | Pass two consecutive integers to one function. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-4-args2`](function-dispatch-4-args2.py) | Pass two consecutive integers to four functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-8-args2`](function-dispatch-8-args2.py) | Pass two consecutive integers to eight functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-args2-control`](function-dispatch-args2-control.py) | Load a function reference and compute the two-argument weighted sum directly in the loop. Shared by all three target counts. |
| [`function-dispatch-1-args3`](function-dispatch-1-args3.py) | Pass three consecutive integers to one function. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-4-args3`](function-dispatch-4-args3.py) | Pass three consecutive integers to four functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-8-args3`](function-dispatch-8-args3.py) | Pass three consecutive integers to eight functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-args3-control`](function-dispatch-args3-control.py) | Load a function reference and compute the three-argument weighted sum directly in the loop. Shared by all three target counts. |
| [`function-dispatch-1-args4`](function-dispatch-1-args4.py) | Pass four consecutive integers to one function. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-4-args4`](function-dispatch-4-args4.py) | Pass four consecutive integers to four functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-8-args4`](function-dispatch-8-args4.py) | Pass four consecutive integers to eight functions in rotation. Each call returns a weighted sum of its arguments. |
| [`function-dispatch-args4-control`](function-dispatch-args4-control.py) | Load a function reference and compute the four-argument weighted sum directly in the loop. Shared by all three target counts. |
| [`string-tokenize`](string-tokenize.py) | Split each ASCII string on literal spaces, retaining empty tokens from leading, repeated, or trailing spaces. |
| [`string-tokenize-control`](string-tokenize-control.py) | Copy the original string references into a reused output buffer. |
| [`string-build`](string-build.py) | Join each group of ASCII fragments with commas and retain the resulting string. |
| [`string-build-control`](string-build-control.py) | Copy references to the prepared fragment groups into a reused output buffer. |
| [`sort-int`](sort-int.py) | Copy and sort integers numerically. Python uses its default ordering and JavaScript supplies a subtraction comparator. |
| [`sort-int-control`](sort-int-control.py) | Copy and retain the unsorted integers. Copy-only control for `sort-int`. |
| [`sort-object-int-field`](sort-object-int-field.py) | Copy and stably sort records by an integer field. Python defines `Record.__lt__` and JavaScript compares `a.key - b.key`. |
| [`sort-string`](sort-string.py) | Copy and sort ASCII strings lexicographically using each language’s default ordering. |
| [`sort-string-by-length`](sort-string-by-length.py) | Copy and stably sort strings by length. Python’s `sort(key=len)` evaluates the key once per string. JavaScript’s `(a, b) => a.length - b.length` compares lengths. |
| [`sort-string-control`](sort-string-control.py) | Copy and retain the unsorted string references. Shared by both string sorts. |
