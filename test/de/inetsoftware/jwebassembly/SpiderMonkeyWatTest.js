#!/usr/bin/env node

var filename = '{test.wat}';
var ret = wasmTextToBinary(read(filename));

function instantiate(bytes, imports) {
  return WebAssembly.compile(bytes).then(
    m => new WebAssembly.Instance(m, imports), reason => console.log(reason) );
}

var dependencies = {
    "global": {},
    "env": {}
};
dependencies["global.Math"] = Math;

function callExport(instance) {
    try{
        console.log( instance.exports[scriptArgs[0]]( ...scriptArgs.slice(1) ) );
    }catch(err){
        console.log(err)
    }
}

instantiate( ret, dependencies ).then( 
  instance => callExport(instance),
  reason => console.log(reason)
);
