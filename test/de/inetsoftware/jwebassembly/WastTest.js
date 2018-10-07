#!/usr/bin/env node

var nodeFS = require('fs');
var wabt = require("wabt")();

var filename = '{test.wast}';
var text = nodeFS['readFileSync'](filename, "utf8");

var features = {'sat_float_to_int':true, 'sign_extension':true};
var ret = wabt.parseWat(filename, text, features);
ret = ret.toBinary({}).buffer;

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
        console.log( instance.exports[process.argv[2]]( ...process.argv.slice(3) ) );
    }catch(err){
        console.log(err)
    }
}

instantiate( ret, dependencies ).then( 
  instance => callExport(instance),
  reason => console.log(reason)
);
