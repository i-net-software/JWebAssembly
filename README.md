JWebAssembly
======

[![Build Status](https://travis-ci.org/i-net-software/JWebAssembly.svg)](https://travis-ci.org/i-net-software/JWebAssembly)
[![License](https://img.shields.io/github/license/i-net-software/jwebassembly.svg)](https://github.com/i-net-software/jwebassembly/blob/master/LICENSE.txt)

JWebAssembly is a Java to [WebAssembly](http://webassembly.org/) compiler. It uses Java class files as input. That it can compile any language that compile to Java bytecode.
As output it generates the binary format (.wasm file) or the text format (.wat file).

Status of the project
----

### Finished Components
* Java byte code parser
* test framework
* Public API of the Compiler

### Partially Finished
* Binary format file writer (134 of 201 byte code instructions)
* Text format file writer (134 of 201 byte code instructions)

### Open Features
* Exception handling - required the next version of WebAssembly
* Multiple threads - required the next version of WebAssembly
* Memory Management - required the next version of WebAssembly
* Gradle plugin to easy integrate it in the build process
* Eclipse build command to see compiler errors in in the IDE. 

Required Java Version
----
JWebAssembly requires Java SE 8 or higher. It is tested with Java SE 8 on [travis-ci.org](https://travis-ci.org/i-net-software/jwebassembly).

## Usage

### Exporting functions
To export a Java function to make it accessible from JavaScript you need add the annotation org.webassembly.annotation.Export

```java
import org.webassembly.annotation.Export;

@Export
public static int add( int a, int b ) {
    return a + b;
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
