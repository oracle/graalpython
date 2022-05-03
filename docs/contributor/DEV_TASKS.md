# Dev Tasks For Graal Python Developers

### Updating dependencies

We can use the following command to update our CI jsonnet as well as all
dependencies (truffle, sulong, ...) in one go:

    mx python-update-import

This should be run on a clean branch that you'll use for the PR. It will search
adjacent directories for other `graalpython*` repositories (such as external
extensions you might have locally) and check if it needs to update something in
those as well. It will also make sure that the CI jsonnet is in sync with the
Graal updates etc. In the end it will tell you which repositories were updated
and pushed. Make sure you open PRs and merge them at the same time for all of
these.

### Updating lib-python

The following command, run on a clean branch, should guide you through updating
our imported sources from CPython and PyPy:

    mx python-src-import

It prints a fairly long help. Note that you'll need to make sure you also pushed
your `python-import` branch after doing the update, so that any conflicts you
resolved don't have to be resolved again by the next person.

### Updating hpy

Follow these steps to update HPy.


  1. Merge updated hpy sources. To do so, clone hpy somewhere next to
     graalpython. Then run the following command on a new branch of graalpython:

        mx python-update-hpy-import --pull /path/to/clone/of/hpy

     Follow the instructions.
  2. We need to fix compilation. We patch the hpy sources, and the merge may
     have introduced new API or types, and for these we need to apply
     patches. At the time of this writing, we redefine hpy types conditionally
     on `#ifdef GRAALVM_PYTHON_LLVM` (grep for this to find some
     examples). Also, we use macros to convert between the structured hpy types
     and plain pointers for our native interface, see the uses of the `WRAP` and
     `UNWRAP` macros.
  3. Once compilation is working, we try to run the tests and go on fixing
     them. If new API was added, `GraalHPyContext` needs to be adapted with the
     new signatures and/or types. This may include:

      - Updating the HPyContextMember enum
      - Updating the HPyContextSignatureType enum
      - Adding `GraalHPyContextFunction` implementations for the new APIs
      - Updating the `createMembers` method to assign the appropriate
        implementations to the context members
      - Updating hpy.c to assign new context members to their native locations

