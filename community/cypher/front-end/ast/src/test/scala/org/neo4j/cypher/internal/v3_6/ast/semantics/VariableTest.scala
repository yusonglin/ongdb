/*
 * Copyright (c) 2018-2020 "Graph Foundation"
 * Graph Foundation, Inc. [https://graphfoundation.org]
 *
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.neo4j.cypher.internal.v3_6.ast.semantics

import org.neo4j.cypher.internal.v3_6.util.DummyPosition
import org.neo4j.cypher.internal.v3_6.util.symbols._
import org.neo4j.cypher.internal.v3_6.expressions.Variable

class VariableTest extends SemanticFunSuite {

  test("shouldDefineVariableDuringSemanticCheckWhenUndefined") {
    val position = DummyPosition(0)
    val variable = Variable("x")(position)

    val result = SemanticExpressionCheck.simple(variable)(SemanticState.clean)
    result.errors should have size 1
    result.errors.head.position should equal(position)
    result.state.symbol("x").isDefined should equal(true)
    result.state.symbolTypes("x") should equal(CTAny.covariant)
  }
}
