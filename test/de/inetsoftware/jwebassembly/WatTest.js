#!/usr/bin/env node

var fs = require('fs');
var wabt = require("wabt")();

var filename = '{test.wat}';
var text = fs.readFileSync(filename, "utf8");
var testData = JSON.parse( fs.readFileSync( "testdata.json", "utf8" ) );

var features = {'sat_float_to_int':true, 'sign_extension':true, 'exceptions':true, 'reference_types':true};
var wasm = wabt.parseWat(filename, text, features);
wasm = wasm.toBinary({}).buffer;

var dependencies = {
    "global": {},
    "env": {}
};
dependencies["global.Math"] = Math;

function callExport(instance) {
    var result = {};
    for (var method in testData) {
        try{
            result[method] = instance.exports[method]( ...testData[method] ).toString();
        }catch(err){
            result[method] = err.toString();
        }
    }
    // save the test result
    fs.writeFileSync( "testresult.json", JSON.stringify(result) );
}

WebAssembly.instantiate( wasm, dependencies ).then( 
  obj => callExport(obj.instance),
  reason => console.log(reason)
);
