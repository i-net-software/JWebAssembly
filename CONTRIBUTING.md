# Contributing to JWebAssembly

If you want contribute to JWebAssembly then you can:

## Write suggestions

Post a [recommendation](https://github.com/i-net-software/JWebAssembly/issues) or comments on existing recommendation. Such recommendations can to the API or to a runtime library.

## Write a third party tool/plugin/library

We need a large infrastructure on tools that based on JWebAssembly. We will happy to support you. For example with specific features. Inform us if you have start and we can link your project.

## Test JWebAssembly

Currently it is a little difficult to test JWebAssembly because the missing infrastructure. If you find problems, we will appreciate it if you post a [bug report](https://github.com/i-net-software/JWebAssembly/issues).

# Pull Requests (PR)

If you want contribute to JWebAssembly via a PR then

* Notify us if you work on some larger changes to prevent conflicts with other.

* Checkin only needed files for the PR.

* Does not reformat existing files. Format new code with the same style as existing code.

* Write API documentation for new code.

* Write tests for your code. Preferred are functional tests that compile Java code to Wasm code.

* Run the test locally before commit to prevent a regression.

* Do the smallest possible PR. If there bugs that block a feature PR then create a separatly PR for the bug. A easer to review a smaller PR.

## Where to start?

* In the issues some issue are marked with "good first issue".

* In the sister project https://github.com/i-net-software/JWebAssembly-API/tree/master/src/de/inetsoftware/jwebassembly/web/dom there are many wrappers to complete for the API on the web.

* replacement for native Java methods https://github.com/i-net-software/JWebAssembly-API/tree/master/src/de/inetsoftware/jwebassembly/api. Write pure Java code for some native Java methods for example java.util.zip.Adler32 or find JavaScript replacements. You can see how you can do this in the examples already implemented.
