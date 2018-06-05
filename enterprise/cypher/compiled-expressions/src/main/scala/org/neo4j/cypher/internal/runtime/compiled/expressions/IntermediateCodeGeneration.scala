/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) with the
 * Commons Clause, as found in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * Neo4j object code can be licensed independently from the source
 * under separate terms from the AGPL. Inquiries can be directed to:
 * licensing@neo4j.com
 *
 * More information is also available at:
 * https://neo4j.com/licensing/
 */
package org.neo4j.cypher.internal.runtime.compiled.expressions

import org.neo4j.cypher.internal.compatibility.v3_5.runtime.ast._
import org.neo4j.cypher.internal.runtime.DbAccess
import org.neo4j.cypher.internal.runtime.compiled.expressions.IntermediateRepresentation.method
import org.neo4j.cypher.internal.runtime.interpreted.ExecutionContext
import org.neo4j.cypher.operations.{CypherBoolean, CypherFunctions, CypherMath}
import org.neo4j.values.AnyValue
import org.neo4j.values.storable._
import org.neo4j.values.virtual.{MapValue, NodeValue, RelationshipValue}
import org.opencypher.v9_0.expressions
import org.opencypher.v9_0.expressions._
import org.opencypher.v9_0.util.InternalException

/**
  * Produces IntermediateRepresentation from a Cypher Expression
  */
class IntermediateCodeGeneration {

  private var counter: Int = 0

  import IntermediateCodeGeneration._
  import IntermediateRepresentation._

  def compile(expression: Expression): Option[IntermediateRepresentation] = expression match {

    //functions
    case c: FunctionInvocation => c.function match {
      case functions.Acos =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("acos"), _))
      case functions.Cos =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("cos"), _))
      case functions.Cot =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("cot"), _))
      case functions.Asin =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("asin"), _))
      case functions.Haversin =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("haversin"), _))
      case functions.Sin =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("sin"), _))
      case functions.Atan =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("atan"), _))
      case functions.Atan2 =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("atan2"), _))
      case functions.Tan =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("tan"), _))
      case functions.Round =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("round"), _))
      case functions.Rand =>
        Some(invokeStatic(method[CypherFunctions, DoubleValue]("rand")))
      case functions.Abs =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("abs"), _))
      case functions.Ceil =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("ceil"), _))
      case functions.Floor =>
        compile(c.args.head).map(invokeStatic(method[CypherFunctions, Value, AnyValue]("floor"), _))

      case _ => None
    }

    //math
    case Multiply(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherMath, AnyValue, AnyValue, AnyValue]("multiply"), l, r)

    case Add(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherMath, AnyValue, AnyValue, AnyValue]("add"), l, r)

    case Subtract(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherMath, AnyValue, AnyValue, AnyValue]("subtract"), l, r)

    //literals
    case d: DoubleLiteral => Some(invokeStatic(method[Values, DoubleValue, Double]("doubleValue"), constant(d.value)))
    case i: IntegerLiteral => Some(invokeStatic(method[Values, LongValue, Long]("longValue"), constant(i.value)))
    case s: expressions.StringLiteral => Some(
      invokeStatic(method[Values, TextValue, String]("stringValue"), constant(s.value)))
    case _: Null => Some(noValue)
    case _: True => Some(truthValue)
    case _: False => Some(falseValue)

    //boolean operators
    case Or(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherBoolean, Value, Array[AnyValue]]("or"), arrayOf(l, r))

    case Ors(exprs) =>
      val compiled = exprs.flatMap(compile).toIndexedSeq
      //we bail if some of the expressions weren't compiled
      if (compiled.size < exprs.size) None
      else Some(invokeStatic(method[CypherBoolean, Value, Array[AnyValue]]("or"), arrayOf(compiled: _*)))

    case Xor(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherBoolean, Value, AnyValue, AnyValue]("xor"), l, r)

    case And(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield {
        val returnValue = nextVariableName()
        val seenNull = nextVariableName()
        val error = nextVariableName()
        val exceptionName = nextVariableName()
        /**
          * Ok AND is complicated. There is no order guarantee so `lhs` and `rhs` are interchangeable. At the core
          * AND tries to find a single `FALSE` if it finds one the expression evaluates to `FALSE` and there is no need to look
          * at more predicates. If it doesn't find a `FALSE` it will either return `NULL` if either lhs or rhs evaluates
          * to `NULL` or `TRUE` if neither lhs nor rhs evaluates to `FALSE` or `NULL`.
          *
          * For example:
          * - AND(FALSE, NULL) -> FALSE
          * - AND(NULL, FALSE) -> FALSE
          * - AND(TRUE, NULL) -> NULL
          * - AND(NULL, TRUE) -> NULL
          *
          * Errors are an extra complication here, errors are treated as `NULL` except we will throw an error instead of
          * returning `NULL`, so for example:
          *
          * - AND(FALSE, 42) -> FALSE
          * - AND(42, FALSE) -> FALSE
          * - AND(TRUE, 42) -> throw type error
          * - AND(42, TRUE) -> throw type error
          *
          * The generated code below will look something like;
          *
          * RuntimeException error = null;
          * boolean seenNull = false;
          * Value returnValue = null;
          * try
          * {
          *   returnValue = l;
          * }
          * catch( RuntimeException e)
          * {
          *   error = e;
          * }
          * seenNull = returnValue == NO_VALUE;
          * if ( returnValue != FALSE )
          * {
          *    try
          *    {
          *      returnValue = r;
          *    }
          *    catch( RuntimeException e)
          *    {
          *      error = e;
          *    }
          *    seenValue = returnValue == FALSE ? false : (seenValue ? true : returnValue == NO_VALUE);
          * }
          * if ( error != null && returnValue != FALSE )
          * {
          *   throw error;
          * }
          * return seenNull ? NO_VALUE : returnValue;
          */
        block(
          declare[RuntimeException](error),
          assign(error, constant(null)),
          declare[Boolean](seenNull),
          assign(seenNull, constant(false)),
          declare[Value](returnValue),
          assign(returnValue, constant(null)),
          tryCatch[RuntimeException](exceptionName)(assign(returnValue, invokeStatic(ASSERT_PREDICATE, l)))(
            assign(error, load(exceptionName))),
          assign(seenNull, equal(load(returnValue), noValue)),
          condition(notEqual(load(returnValue), falseValue))(
            block(
              tryCatch[RuntimeException](exceptionName)(assign(returnValue, invokeStatic(ASSERT_PREDICATE, r)))(
                assign(error, load(exceptionName))),
              assign(seenNull,
                     ternary(equal(load(returnValue), falseValue), constant(false),
                             ternary(load(seenNull),
                                     constant(true),
                                     equal(load(returnValue), noValue))))
            )
          ),
          condition(and(notEqual(load(error), constant(null)), notEqual(load(returnValue), falseValue)))(
            fail(load(error))),
          ternary(load(seenNull), noValue, load(returnValue)))
      }

    case Ands(expressions) =>
      val compiled = expressions.flatMap(compile).toIndexedSeq
      //we bail if some of the expressions weren't compiled
      if (compiled.size < expressions.size) None
      else Some(invokeStatic(method[CypherBoolean, Value, Array[AnyValue]]("and"), arrayOf(compiled: _*)))

    case Not(arg) =>
      compile(arg).map(invokeStatic(method[CypherBoolean, Value, AnyValue]("not"), _))

    case Equals(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherBoolean, Value, AnyValue, AnyValue]("equals"), l, r)

    case NotEquals(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield invokeStatic(method[CypherBoolean, Value, AnyValue, AnyValue]("notEquals"), l, r)

    //data access
    case Parameter(name, _) =>
      Some(invoke(load("params"), method[MapValue, AnyValue, String]("get"), constant(name)))

    case NodeProperty(offset, token, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, Value, Long, Int]("nodeProperty"),
                  getLongAt(offset), constant(token)))

    case NodePropertyLate(offset, key, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, Value, Long, String]("nodeProperty"),
                  getLongAt(offset), constant(key)))

    case NodePropertyExists(offset, token, _) =>
      Some(
        ternary(
          invoke(load("dbAccess"), method[DbAccess, Boolean, Long, Int]("nodeHasProperty"),
                 getLongAt(offset), constant(token)), truthValue, falseValue))

    case NodePropertyExistsLate(offset, key, _) =>
      Some(ternary(
        invoke(load("dbAccess"), method[DbAccess, Boolean, Long, String]("nodeHasProperty"),
               getLongAt(offset), constant(key)), truthValue, falseValue))

    case RelationshipProperty(offset, token, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, Value, Long, Int]("relationshipProperty"),
                  getLongAt(offset), constant(token)))

    case RelationshipPropertyLate(offset, key, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, Value, Long, String]("relationshipProperty"),
                  getLongAt(offset), constant(key)))

    case RelationshipPropertyExists(offset, token, _) =>
      Some(ternary(
        invoke(load("dbAccess"), method[DbAccess, Boolean, Long, Int]("relationshipHasProperty"),
               getLongAt(offset), constant(token)),
        truthValue,
        falseValue)
      )

    case RelationshipPropertyExistsLate(offset, key, _) =>
      Some(ternary(
        invoke(load("dbAccess"), method[DbAccess, Boolean, Long, String]("relationshipHasProperty"),
               getLongAt(offset), constant(key)),
        truthValue,
        falseValue)
      )
    case NodeFromSlot(offset, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, NodeValue, Long]("nodeById"),
                  getLongAt(offset)))
    case RelationshipFromSlot(offset, _) =>
      Some(invoke(load("dbAccess"), method[DbAccess, RelationshipValue, Long]("relationshipById"),
                  getLongAt(offset)))

    case GetDegreePrimitive(offset, typ, dir) =>
      val methodName = dir match {
        case SemanticDirection.OUTGOING => "nodeGetOutgoingDegree"
        case SemanticDirection.INCOMING => "nodeGetIncomingDegree"
        case SemanticDirection.BOTH => "nodeGetTotalDegree"
      }
      typ match {
        case None =>
          Some(
            invokeStatic(method[Values, IntValue, Int]("intValue"),
              invoke(load("dbAccess"), method[DbAccess, Int, Long](methodName), getLongAt(offset))))

        case Some(t) =>
          Some(
            invokeStatic(method[Values, IntValue, Int]("intValue"),
                         invoke(load("dbAccess"), method[DbAccess, Int, Long, String](methodName),
                      getLongAt(offset), constant(t))))
      }

    //slotted operations
    case ReferenceFromSlot(offset, _) =>
      Some(getRefAt(offset))
    case IdFromSlot(offset) =>
      Some(invokeStatic(method[Values, LongValue, Long]("longValue"), getLongAt(offset)))

    case PrimitiveEquals(lhs, rhs) =>
      for {l <- compile(lhs)
           r <- compile(rhs)
      } yield
        ternary(invoke(l, method[AnyValue, Boolean, AnyRef]("equals"), r), truthValue, falseValue)

    case NullCheck(offset, inner) =>
      compile(inner).map(ternary(equal(getLongAt(offset), constant(-1L)), noValue, _))

    case NullCheckVariable(offset, inner) =>
      compile(inner).map(ternary(equal(getLongAt(offset), constant(-1L)), noValue, _))

    case NullCheckProperty(offset, inner) =>
      compile(inner).map(ternary(equal(getLongAt(offset), constant(-1L)), noValue, _))

    case IsPrimitiveNull(offset) =>
      Some(ternary(equal(getLongAt(offset), constant(-1L)), truthValue, falseValue))

    case _ => None
  }

  private def getLongAt(offset: Int): IntermediateRepresentation =
    invoke(load("context"), method[ExecutionContext, Long, Int]("getLongAt"),
           constant(offset))

  private def getRefAt(offset: Int): IntermediateRepresentation =
    invoke(load("context"), method[ExecutionContext, AnyValue, Int]("getRefAt"),
           constant(offset))

  private def nextVariableName(): String = {
    val nextName = s"v$counter"
    counter += 1
    nextName
  }

}

object IntermediateCodeGeneration {
  private val ASSERT_PREDICATE = method[CompiledHelpers, Value, AnyValue]("assertBooleanOrNoValue")

}
