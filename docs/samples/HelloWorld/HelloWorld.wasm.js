'use strict';var wasmImports = {
Web:{
win_get0:(p)=>window[p],
get0:(o,p)=>o[p],
fromChars:(v)=>{v=v[2];var s='';for(var i=0;i<v.length;i++){s+=String.fromCharCode(v[i]);}return s},
invoke0:(o,m,p1)=>o[m](p1)
},
NonGC:{
array_new_u16:(l)=>Object.seal({0:648,1:0,2:Object.seal(new Array(l).fill(null))}),
new_java_lang_StringBuilder:() => Object.seal({0:976,1:0,2:null,3:0}),
new_java_util_Collections$EmptySet:() => Object.seal({0:2548,1:0}),
new_java_util_Collections$UnmodifiableList:() => Object.seal({0:2272,1:0,2:null,3:null}),
new_de_inetsoftware_jwebassembly_web_dom_HTMLElement:() => Object.seal({0:324,1:0,2:null}),
new_java_lang_OutOfMemoryError:() => Object.seal({0:1448,1:0,2:null,3:null,4:null,5:null}),
new_java_lang_ThreadLocal:() => Object.seal({0:1740,1:0,2:0}),
new_java_util_concurrent_atomic_AtomicInteger:() => Object.seal({0:2316,1:0,2:0}),
get_i32:(a,i) => a[i],
new_de_inetsoftware_jwebassembly_web_dom_Document:() => Object.seal({0:252,1:0,2:null}),
array_new_i32:(l)=>Object.seal({0:1196,1:0,2:new Int32Array(l)}),
new_java_lang_StringIndexOutOfBoundsException:() => Object.seal({0:924,1:0,2:null,3:null,4:null,5:null}),
new_java_lang_NullPointerException:() => Object.seal({0:2464,1:0,2:null,3:null,4:null,5:null}),
ref_eq:(a,b) => a === b,
new_java_lang_IllegalArgumentException:() => Object.seal({0:1228,1:0,2:null,3:null,4:null,5:null}),
get_anyref:(a,i) => a[i],
array_new_java_lang_StackTraceElement:(l)=>Object.seal({0:1384,1:0,2:Object.seal(new Array(l).fill(null))}),
set_anyref:(a,v,i) => a[i]=v,
array_get_i32:(a,i)=>a[2][i],
new_java_lang_String:() => Object.seal({0:452,1:0,2:null,3:0,4:null}),
get_externref:(a,i) => a[i],
new_java_util_Collections$UnmodifiableRandomAccessList:() => Object.seal({0:2220,1:0,2:null,3:null}),
new_de_inetsoftware_jwebassembly_web_dom_Node:() => Object.seal({0:532,1:0,2:null}),
array_new_java_lang_Throwable:(l)=>Object.seal({0:2052,1:0,2:Object.seal(new Array(l).fill(null))}),
array_set_i32:(a,i,v)=>a[2][i]=v,
new_java_lang_AssertionError:() => Object.seal({0:2116,1:0,2:null,3:null,4:null,5:null}),
array_new_java_io_ObjectStreamField:(l)=>Object.seal({0:1644,1:0,2:Object.seal(new Array(l).fill(null))}),
new_java_lang_String$CaseInsensitiveComparator:() => Object.seal({0:1676,1:0}),
new_java_util_Collections$EmptyMap:() => Object.seal({0:2712,1:0}),
array_new_i8:(l)=>Object.seal({0:588,1:0,2:new Uint8Array(l)}),
array_len:(a)=>a[2].length,
new_java_util_ArrayList:() => Object.seal({0:2000,1:0,2:0,3:null}),
new_java_lang_Class:() => Object.seal({0:1804,1:0,2:0}),
new_de_inetsoftware_jwebassembly_web_dom_Text:() => Object.seal({0:388,1:0,2:null}),
new_java_util_Collections$EmptyList:() => Object.seal({0:2628,1:0,2:0}),
set_i32:(a,v,i) => a[i]=v,
array_new_java_lang_Object:(l)=>Object.seal({0:2160,1:0,2:Object.seal(new Array(l).fill(null))})
},
System:{
arraycopy:(src,srcPos,dest,destPos,length)=>{src=src[2];dest=dest[2];if(destPos<srcPos){for (var i=0;i<length;i++)dest[i+destPos]=src[i+srcPos];}else{for (var i=length-1;i>=0;i--)dest[i+destPos]=src[i+srcPos];}}
}
};
if (typeof module !== 'undefined') module.exports = wasmImports;