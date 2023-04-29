# Contributing to this repository

We welcome your contributions! There are multiple ways to contribute.

## Opening issues

For bugs or enhancement requests, please file a GitHub issue unless it's security related.
When filing a bug remember that the better written the bug is, the more likely it is to be fixed.
If you think you've found a security vulnerability, do not raise a GitHub issue and follow the instructions in our [security policy](./SECURITY.md).

## Contributing code

We welcome your code contributions.
Before submitting code via a pull request, you will need to have signed the [Oracle Contributor Agreement][OCA] (OCA) and your commits need to include the following line using the name and e-mail address you used to sign the OCA:

```text
Signed-off-by: Your Name <you@example.org>
```

This can be automatically added to pull requests by committing with `--sign-off` or `-s`, e.g.

```text
git commit --signoff
```

Only pull requests from committers that can be verified as having signed the OCA can be accepted.

To get you started with the technical details, we have [written a bit](docs/contributor/CONTRIBUTING.md) about the structure of this interpreter that should show how to fix things or add features.

## Pull request process

1. Ensure there is an issue created to track and discuss the fix or enhancement you intend to submit.
2. Fork this repository.
3. Create a branch in your fork to implement the changes.
   We recommend using the issue number as part of your branch name, e.g. `GH1234-fixes`.
4. Ensure that any documentation is updated where it makes sense.
5. Submit the pull request.
   Explain what your changes are meant to do and provide simple steps on how to validate your changes.
   Ensure that you reference the issue you created as well.
7. We will assign the pull request to at least 1 person for review before it is merged.

## Code of conduct

Follow the [Golden Rule](https://en.wikipedia.org/wiki/Golden_Rule).
If you'd like more specific guidelines, see the [Contributor Covenant Code of Conduct][COC].

[OCA]: https://oca.opensource.oracle.com
[COC]: https://www.contributor-covenant.org/version/1/4/code-of-conduct/
