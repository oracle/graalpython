# Dev Tasks For Graal Python Developers

### Updating dependencies

We can use the following command to update our CI jsonnet as well as all
dependencies (truffle, ...) in one go:

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

### Updating patch branch
GraalPy's `pip` has an ability to download newer versions of patches from our
GitHub so that we can update patches outside of the release cycle. There should
be a patch branch for every minor release, with the name
`github/patches/$version`. When creating the branch, it should be based on the
release tag commit and then it should change the CI overlay version to point to
the head of branch `graalpy-patch-branch` in order to disable unnecessary gates
on it. The GitHub sync needs to be manually enabled in the mirroring service
configuration.

### Updating hpy

1. Switch to the `hpy-import` branch
2. Delete `graalpython/hpy`
3. Copy the sources from the hpy repo into `graalpython/hpy` (e.g git clone
   them there, then delete the `graalpython/hpy/.git` folder)
4. Go back to your previous branch and merge hpy-import.
5. Go to `graalpython/hpy/build.py` and update the `VERSION` constant to
   whatever you updated to.
