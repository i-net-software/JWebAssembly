/*
 * Copyright 2021 - 2022 Volker Berlin (i-net software)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.inetsoftware.jwebassembly.module;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.junit.Before;
import org.junit.Test;

import de.inetsoftware.classparser.BootstrapMethod;
import de.inetsoftware.classparser.ConstantMethodRef;
import de.inetsoftware.jwebassembly.JWebAssembly;
import de.inetsoftware.jwebassembly.module.TypeManager.LambdaType;
import de.inetsoftware.jwebassembly.module.TypeManager.StructType;
import de.inetsoftware.jwebassembly.module.TypeManager.StructTypeKind;

/**
 * @author Volker Berlin
 */
public class StructTypeTest {

    private TypeManager manager;

    @Before
    public void before() {
        HashMap<String, String> properties = new HashMap<>();
        properties.put( JWebAssembly.WASM_USE_GC, "true" );
        WasmOptions options = new WasmOptions( properties );
        manager = options.types;
        manager.init( new ClassFileLoader( getClass().getClassLoader() ) );
    }

    @Test
    public void isSubTypeOf_SuperClass() throws Throwable {
        StructType typeInteger = manager.valueOf( "java/lang/Integer" );
        StructType typeNumber = manager.valueOf( "java/lang/Number" );
        StructType typeObject = manager.valueOf( "java/lang/Object" );

        assertTrue( typeInteger.isSubTypeOf( typeInteger ) );
        assertTrue( typeInteger.isSubTypeOf( typeNumber ) );
        assertTrue( typeInteger.isSubTypeOf( typeObject ) );

        assertFalse( typeNumber.isSubTypeOf( typeInteger ) );
        assertFalse( typeObject.isSubTypeOf( typeInteger ) );
        assertFalse( typeObject.isSubTypeOf( typeNumber ) );
    }

    @Test
    public void isSubTypeOf_Primitives() throws Throwable {
        StructType typeObject = manager.valueOf( "java/lang/Object" );
        StructType typeBoolean = manager.valueOf( "boolean" );
        StructType typeInt = manager.valueOf( "int" );

        assertEquals( StructTypeKind.primitive, typeBoolean.getKind() );
        assertEquals( StructTypeKind.primitive, typeInt.getKind() );
        assertEquals( StructTypeKind.normal, typeObject.getKind() );

        assertFalse( typeBoolean.isSubTypeOf( typeInt ) );
        assertFalse( typeInt.isSubTypeOf( typeBoolean ) );

        assertFalse( typeBoolean.isSubTypeOf( typeObject ) );
        assertFalse( typeObject.isSubTypeOf( typeBoolean ) );
    }

    @Test
    public void isSubTypeOf_Interfaces() throws Throwable {
        StructType typeInteger = manager.valueOf( "java/lang/Integer" );
        StructType typeComparable = manager.valueOf( "java/lang/Comparable" );

        assertTrue( typeInteger.isSubTypeOf( typeComparable ) );
        assertFalse( typeComparable.isSubTypeOf( typeInteger ) );
    }

    @Test
    public void isSubTypeOf_Arrays() throws Throwable {
        StructType typeObject = manager.valueOf( "java/lang/Object" );
        StructType typeInteger = manager.valueOf( "java/lang/Integer" );
        StructType typeObjArray = manager.arrayType( typeObject );
        StructType typeIntArray = manager.arrayType( typeInteger );

        assertTrue( typeObjArray.isSubTypeOf( typeObject ) );
        assertFalse( typeObjArray.isSubTypeOf( typeInteger ) );

        assertTrue( typeIntArray.isSubTypeOf( typeObjArray ) );
        assertFalse( typeObjArray.isSubTypeOf( typeIntArray ) );
    }

    @Test
    public void isSubTypeOf_Lambda() throws Throwable {
        StructType typeRunnable = manager.valueOf( "java/lang/Runnable" );

        ConstantMethodRef implMethod = mock( ConstantMethodRef.class );
        when( implMethod.getName() ).thenReturn( "" );
        BootstrapMethod method = mock( BootstrapMethod.class );
        when( method.getImplMethod() ).thenReturn( implMethod );
        LambdaType lambda = manager.lambdaType( method, "()Ljava/lang/Runnable;", "run", -1 );

        assertTrue( lambda.isSubTypeOf( typeRunnable ) );
        assertFalse( typeRunnable.isSubTypeOf( lambda ) );
    }
}
