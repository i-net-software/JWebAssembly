JWebAssembly
======

[![Build Status](https://travis-ci.org/i-net-software/JWebAssembly.svg)](https://travis-ci.org/i-net-software/JWebAssembly)
[![License](https://img.shields.io/github/license/i-net-software/jwebassembly.svg)](https://github.com/i-net-software/jwebassembly/blob/master/LICENSE.txt)

JWebAssembly is a Java to [WebAssembly](http://webassembly.org/) Compiler. It uses Java class files as input.

Status of the project
----
Currently it is only a proof of concept.

Future Features
----
In the future there should be a Gradle plugin to easy integrate it in the build process and a Eclipse build command to see compiler errors in in the IDE. 

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
