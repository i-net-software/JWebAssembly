(module
  (type $t0 (func(param i32)))
  (export "abc" (func $de/inetsoftware/jwebassembly/samples/FunctionParameters.singleInt))
  (func $de/inetsoftware/jwebassembly/samples/FunctionParameters.singleInt(param i32)(local i32)
    local.get 0
    i32.const 1
    i32.add
    local.set 1
    return
  )
)