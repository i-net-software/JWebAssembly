#!/usr/bin/env node

var wasm = wasmTextToBinary( read( "spiderMonkey.wat" ) );
var testData = JSON.parse( read( "testdata.json" ) );

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
    const original = redirect( "testresult.json" );
    putstr( JSON.stringify(result) );
    redirect( original );
}

WebAssembly.instantiate( wasm, dependencies ).then(
  obj => callExport(obj.instance),
  reason => console.log(reason)
);
