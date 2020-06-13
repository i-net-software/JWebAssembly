#!/usr/bin/env node

var fs = require('fs');

const wasmImports = require( "./{test}.wasm.js" );
var filename = '{test}.wasm';
var wasm = fs.readFileSync(filename);
var testData = JSON.parse( fs.readFileSync( "testdata.json", "utf8" ) );

// save the test result
function saveResults(result) {
    fs.writeFileSync( "testresult.json", JSON.stringify(result) );
}

function callExport( instance, wasmImports ) {
    wasmImports.exports = instance.exports;
    var result = {};
    for (var method in testData) {
        try{
            result[method] = String(instance.exports[method]( ...testData[method] ));
        }catch(err){
            result[method] = err.stack;
        }
    }
    saveResults(result);
}

WebAssembly.instantiate( wasm, wasmImports ).then(
  obj => callExport( obj.instance, wasmImports ),
  reason => console.log(reason)
);
