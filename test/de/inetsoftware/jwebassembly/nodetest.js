#!/usr/bin/env node

var fs = require('fs');

var filename = '{test.wasm}';
var wasm = fs.readFileSync(filename);
var testData = JSON.parse( fs.readFileSync( "testdata.json", "utf8" ) );

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
