#!/usr/bin/env node

var filename = '{test.wasm}';
var ret = read(filename, "binary");

function instantiate(bytes, imports) {
  return WebAssembly.compile(bytes).then(
    m => new WebAssembly.Instance(m, imports), reason => console.log(reason) );
}

var dependencies = {
    "global": {},
    "env": {}
};
dependencies["global.Math"] = Math;


instantiate( ret, dependencies ).then( 
  instance => console.log( instance.exports[scriptArgs[0]]( scriptArgs.slice(1) ) ),
  reason => console.log(reason)
);
