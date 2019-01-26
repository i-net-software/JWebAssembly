JWebAssembly
======

[![Build Status](https://travis-ci.org/i-net-software/JWebAssembly.svg)](https://travis-ci.org/i-net-software/JWebAssembly)
[![License](https://img.shields.io/github/license/i-net-software/jwebassembly.svg)](https://github.com/i-net-software/jwebassembly/blob/master/LICENSE.txt)
[![Coverage Status](https://coveralls.io/repos/github/i-net-software/JWebAssembly/badge.svg?branch=master)](https://coveralls.io/github/i-net-software/JWebAssembly?branch=master)

JWebAssembly is a Java bytecode to [WebAssembly](http://webassembly.org/) compiler. It uses Java class files as input. That it can compile any language that compile to Java bytecode.
As output it generates the binary format (.wasm file) or the text format (.wat file).

The difference to similar projects is that not a complete VM with GC and memory management should be ported. It's more like a 1: 1 conversion. The generated WebAssembly code is similar in size to the original Java class files.

Status of the project
----

The project is currently not production ready but you can run already some tests.

### Finished Components
* Java byte code parser
* test framework
* Public API of the Compiler
* [Gradle Plugin](https://github.com/i-net-software/JWebAssembly-Gradle)

### Partially Finished
* Binary format file writer and Text format file writer (183 of 201 Java byte code instructions)
* Support for native methods [#2](https://github.com/i-net-software/JWebAssembly/issues/2)
* Exception handling - required the next version of WebAssembly
* Multiple threads - required the next version of WebAssembly
* Memory Management - required the next version of WebAssembly with GC

### Open Features
* Optimizer - Optimize the WASM output of a single method after transpiling before writing to output
* Library for accessing the DOM

### Status of Required WebAssembly Features
The following table shows the status of future WebAssembly features required by JWebAssembly in nightly builds in various implementations. These features are already used by the trunk version of JWebAssembly.

| Feature                 | V8     | SpiderMonkey | WABT   |
| ----------------------- | ------ | ------------ | ------ |
| [floar-to-int][1]       | yes    | yes          | yes    |
| [Sign-extension][2]     | yes    | yes          | yes    |
| [Multi-value][3]        | yes    | -            | yes    |
| [Reference Types][4]    | yes    | yes          | -      |
| [Garbage collection][5] | -      | partly       | -      |
| [Exceptions][6]         | partly | -            | partly |

- For V8 it based on the [V8 - node.js integrations builds](https://ci.chromium.org/p/v8/builders/luci.v8.ci/V8%20Linux64%20-%20node.js%20integration).
- For SpiderMonkey it based on the nightly build of [jsshell](https://archive.mozilla.org/pub/firefox/nightly/latest-mozilla-central/).
- For WABT it based on [libwabt.js](https://github.com/WebAssembly/wabt/blob/master/demo/libwabt.js) via node module wabt@nightly.

To use it also some flags and switches are needed.

Required Java Version
----
The JWebAssembly compiler requires Java SE 8 or higher. It is tested with Java SE 8 on [travis-ci.org](https://travis-ci.org/i-net-software/jwebassembly).

## Usage

### Exporting functions
To export a Java function to make it accessible from JavaScript, you must add the annotation de.inetsoftware.jwebassembly.api.annotation.Export.

```java
import de.inetsoftware.jwebassembly.api.annotation.Export;

@Export
public static int add( int a, int b ) {
    return a + b;
}
```

### importing functions
To import a JavaScript function to make it accessible from Java, you must add the annotation de.inetsoftware.jwebassembly.api.annotation.Import.
The method can be declared native or can have a Java implementation which will be ignored on compiling.

```java
import de.inetsoftware.jwebassembly.api.annotation.Import;

@Import( module = "global.Math", name = "max" )
static int max( int a, int b) {
    return Math.max( a, b );
}
```


### Java Limits
In version 1 of WebAssembly you can only compile:
* static methods
* use the data types int, long float and double

This is state of JWebAssembly 0.1.

### Alternatives
* [TeaVM](https://github.com/konsoletyper/teavm)

## For Tool Developer

If you want to develop some tools like plugins for a build system or an IDE, then you need
* to include the full contents of the packages [de.inetsoftware.jwebassembly](https://github.com/i-net-software/JWebAssembly/tree/master/src/de/inetsoftware/jwebassembly) and [de.inetsoftware.classparser](https://github.com/i-net-software/JWebAssembly/tree/master/src/de/inetsoftware/classparser) and its subpackages.
* Create an instance of [de.inetsoftware.jwebassembly.JWebAssembly](https://github.com/i-net-software/JWebAssembly/blob/master/src/de/inetsoftware/jwebassembly/JWebAssembly.java) class and use its API.

[1]: https://github.com/WebAssembly/nontrapping-float-to-int-conversions
[2]: https://github.com/WebAssembly/sign-extension-ops
[3]: https://github.com/WebAssembly/multi-value
[4]: https://github.com/WebAssembly/reference-types
[5]: https://github.com/webassembly/gc
[6]: https://github.com/WebAssembly/exception-handling
