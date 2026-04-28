# Contributing to GraalPy: getting started

Thanks for considering contributing to GraalPy.
This page aims at helping you through your first contribution to the project. 

For deep technical details and complete command references, see [CONTRIBUTING.md](./CONTRIBUTING.md).

If you want help while getting started, join the [GraalVM community Slack](https://www.graalvm.org/slack-invitation/).

## Quick path for your first contribution

1. [Pick an issue](#1-pick-an-issue)
2. [Set up your environment](#2-set-up-your-environment)
3. [Make a change](#3-make-a-change)
4. [Run focused checks](#4-run-focused-checks)
5. [Open your Pull Request](#5-open-your-pull-request)

---

## 1. Pick an issue

Start with something small and well-scoped, for instance, issues labeled [good first issue](https://github.com/oracle/graalpython/issues?q=is%3Aissue%20state%3Aopen%20label%3A%22good%20first%20issue%22).

If there is no issue yet, create one from the [issue templates](https://github.com/oracle/graalpython/issues/new/choose) so the work is tracked and discussed.

![Issue Form](/docs/contributor/assets/issue_form_selector.png)

If you think you've found a security vulnerability, do not raise a GitHub issue and follow the instructions in our [security policy](https://github.com/oracle/graalpython/blob/master/SECURITY.md).

## 2. Set up your environment

You can contribute from:

- **GitHub Codespaces** (quickest onboarding): see [Using a GitHub codespace](./CONTRIBUTING.md#using-a-github-codespace)
- **Your local machine**: see [Setting up on your machine](./CONTRIBUTING.md#setting-up-on-your-machine)

Then do the minimal git setup:

```bash
git checkout master
git pull
git checkout -b <your-branch-name>
```

Optionally, after setup:

```bash
mx ideinit
```

## 3. Make a change

Keep your first change small and easy to review (one issue, one objective, one PR).
Before editing, quickly identify where the code or tests live by checking the project structure in the
[Development Layout](https://github.com/oracle/graalpython/blob/master/docs/contributor/CONTRIBUTING.md#development-layout).

If you use GitHub Codespaces with GitHub Copilot Pro, you can also run an AI coding agent in an isolated environment with access to Issues, PRs, and CI context.

## 4. Run focused checks

Before opening a PR, run tests affected by your changes.

Common commands:

- `mx clean`: clean build files
- `mx build`: build GraalPy
- `mx python-svm`: build GraalPy native
- `mx gate --tags python-unittest`: Python unit tests
- `mx gate --tags python-junit`: Java/JUnit tests
- `mx graalpytest <path>::<test-name>`: run one targeted Python test

Example:

```bash
mx graalpytest graalpython/lib-python/3/test/test_threading.py::test.test_threading.ExceptHookTests.test_excepthook
```

If you need a complete list of commands, run `mx help` or check [CONTRIBUTING.md](./CONTRIBUTING.md).

## 5. Open your pull request

1. Push your branch to your fork
2. Open a PR against `master`
3. Mark it **Ready for review** when it is ready

Important:

- You must sign the [Oracle Contributor Agreement (OCA)](https://www.graalvm.org/community/contributors/) before merge
- CI unit tests run when the PR is ready for review (not in draft)
- Opening a PR early in your fork is fine if you want CI feedback

---

## What to read next

After your first contribution, continue with:

- [CONTRIBUTING.md](./CONTRIBUTING.md) for full setup, workflows, and CI details
- [IMPLEMENTATION_DETAILS.md](./IMPLEMENTATION_DETAILS.md) for deeper architecture context
