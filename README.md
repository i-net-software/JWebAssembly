JWebAssembly
======

[![Build Status](https://travis-ci.org/i-net-software/JWebAssembly.svg)](https://travis-ci.org/i-net-software/JWebAssembly)
[![License](https://img.shields.io/github/license/i-net-software/jwebassembly.svg)](https://github.com/i-net-software/jwebassembly/blob/master/LICENSE.txt)
[![Coverage Status](https://coveralls.io/repos/github/i-net-software/JWebAssembly/badge.svg?branch=master)](https://coveralls.io/github/i-net-software/JWebAssembly?branch=master)

JWebAssembly is a Java to [WebAssembly](http://webassembly.org/) compiler. It uses Java class files as input. That it can compile any language that compile to Java bytecode.
As output it generates the binary format (.wasm file) or the text format (.wat file).

Status of the project
----

### Finished Components
* Java byte code parser
* test framework
* Public API of the Compiler
* [Gradle Plugin](https://github.com/i-net-software/JWebAssembly-Gradle)

### Partially Finished
* Binary format file writer (145 of 201 byte code instructions)
* Text format file writer (145 of 201 byte code instructions)

### Open Features
* Support for native methods [#2](https://github.com/i-net-software/JWebAssembly/issues/2)
* Exception handling - required the next version of WebAssembly
* Multiple threads - required the next version of WebAssembly
* Memory Management - required the next version of WebAssembly with GC

Required Java Version
----
JWebAssembly requires Java SE 8 or higher. It is tested with Java SE 8 on [travis-ci.org](https://travis-ci.org/i-net-software/jwebassembly).

## Usage

### Exporting functions
To export a Java function to make it accessible from JavaScript you need add the annotation de.inetsoftware.jwebassembly.api.annotation.Export.

```java
import de.inetsoftware.jwebassembly.api.annotation.Export;

@Export
public static int add( int a, int b ) {
    return a + b;
}
```

### importing functions
To import a JavaScript function to make it accessible from Java you need add the annotation de.inetsoftware.jwebassembly.api.annotation.Import.
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

### Alternatives
* [TeaVM](https://github.com/konsoletyper/teavm)

## For Tool Developer

If you want to develop some tools like plugins for a build system or an IDE, then you need
* to include the full contents of the packages [de.inetsoftware.jwebassembly](https://github.com/i-net-software/JWebAssembly/tree/master/src/de/inetsoftware/jwebassembly) and [de.inetsoftware.classparser](https://github.com/i-net-software/JWebAssembly/tree/master/src/de/inetsoftware/classparser) and its subpackages.
* Create an instance of [de.inetsoftware.jwebassembly.JWebAssembly](https://github.com/i-net-software/JWebAssembly/blob/master/src/de/inetsoftware/jwebassembly/JWebAssembly.java) class and use its API.
