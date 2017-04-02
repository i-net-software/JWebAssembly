#!/usr/bin/env node

var nodeFS = require('fs');

var filename = '{test.wasm}';
var ret = nodeFS['readFileSync'](filename);

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
  instance => console.log( instance.exports[process.argv[2]]( process.argv.slice(3) ) ),
  reason => console.log(reason)
);
