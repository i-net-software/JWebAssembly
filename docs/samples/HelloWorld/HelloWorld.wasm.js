'use strict';var wasmImports = {
Web:{
get0:(o,p)=>o[p],
win_get:(p)=>window[p],
invoke1:(o,m,p1)=>o[m](p1)
},
NonGC:{
array_set_i16:(a,i,v) => a[i]=v,
get_anyref:(a,i) => a[i],
set_anyref:(a,v,i) => a[i]=v,
new_java_lang_String:() => Object.seal({0:48}),
array_len_i8:(a) => a.length,
new_de_inetsoftware_jwebassembly_web_dom_Node:() => Object.seal({0:48,1:null}),
array_get_i8:(a,i) => a[i],
new_de_inetsoftware_jwebassembly_web_dom_HTMLElement:() => Object.seal({0:24,1:null}),
get_i32:(a,i) => a[i],
new_de_inetsoftware_jwebassembly_web_dom_Document:() => Object.seal({0:0,1:null}),
array_new_i8:(l) => new Uint8Array(l),
array_set_i8:(a,i,v) => a[i]=v,
array_new_i16:(l) => new Int16Array(l),
new_de_inetsoftware_jwebassembly_web_dom_Text:() => Object.seal({0:36,1:null})
},
StringHelper:{
newFromSubChars:(value,off,count)=>{var s='';for(var i=off;i<off+count;i++){s+=String.fromCharCode(value[i]);}return s}
}
};
if (typeof module !== 'undefined') module.exports = wasmImports;