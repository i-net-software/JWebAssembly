#!/usr/bin/env node

var fs = require('fs');

const wasmImports = require( "./test.wasm.js" );
var filename = '{test.wasm}';
var wasm = fs.readFileSync(filename);
var testData = JSON.parse( fs.readFileSync( "testdata.json", "utf8" ) );

function callExport(instance) {
    var result = {};
    for (var method in testData) {
        try{
            result[method] = String(instance.exports[method]( ...testData[method] ));
        }catch(err){
            result[method] = err.toString();
        }
    }
    // save the test result
    fs.writeFileSync( "testresult.json", JSON.stringify(result) );
}

WebAssembly.instantiate( wasm, wasmImports ).then(
  obj => callExport(obj.instance),
  reason => console.log(reason)
);
