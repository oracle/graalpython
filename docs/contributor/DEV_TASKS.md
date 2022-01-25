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

### Updating pegparser

The Python PEG parser is tracked as a separate repository as well and merged
with git-subtree. This is irrelevant for external contributors, but just for
reference, this is how we merge back and forth:

    git subtree pull --prefix graalpython/com.oracle.graal.python.pegparser [pegparser-git-url] master
    git subtree push --prefix graalpython/com.oracle.graal.python.pegparser [pegparser-git-url] master

Generally this work, it can happen that the two repos become a bit inconsistent
and git-subtree gives up, but then we can fix the trees manually with a `git
subtree pull` and then `git subtree split` and pushing the split commit to the
pegparser repo.
