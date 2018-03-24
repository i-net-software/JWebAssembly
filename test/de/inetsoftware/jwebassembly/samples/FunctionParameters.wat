(module
  (export "abc" (func $de/inetsoftware/jwebassembly/samples/FunctionParameters.singleInt))
  (func $de/inetsoftware/jwebassembly/samples/FunctionParameters.singleInt (param i32) (local i32)
    get_local 0
    i32.const 1
    i32.add
    set_local 1
    return
  )
)