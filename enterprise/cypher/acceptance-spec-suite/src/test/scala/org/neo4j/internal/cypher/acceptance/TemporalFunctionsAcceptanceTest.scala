/*
 * Copyright (c) 2018-2020 "Graph Foundation"
 * Graph Foundation, Inc. [https://graphfoundation.org]
 *
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of ONgDB Enterprise Edition. The included source
 * code can be redistributed and/or modified under the terms of the
 * GNU AFFERO GENERAL PUBLIC LICENSE Version 3
 * (http://www.fsf.org/licensing/licenses/agpl-3.0.html) as found
 * in the associated LICENSE.txt file.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 */
package org.neo4j.internal.cypher.acceptance

import java.time._

import org.neo4j.cypher.ExecutionEngineFunSuite
import org.neo4j.cypher.FakeClock
import org.neo4j.internal.cypher.acceptance.comparisonsupport.Configs
import org.neo4j.internal.cypher.acceptance.comparisonsupport.CypherComparisonSupport

class TemporalFunctionsAcceptanceTest extends ExecutionEngineFunSuite with CypherComparisonSupport with FakeClock {

  val supported = (Configs.Version3_5 + Configs.Version3_4 + Configs.Version3_1) - Configs.Compiled

  test("should get current default datetime") {
    val result = executeWith(supported, "RETURN datetime() as now")

    val now = single(result.columnAs[ZonedDateTime]("now"))

    now shouldBe a[ZonedDateTime]
  }

  test("should get current 'realtime' datetime") {
    val result = executeWith(supported, "RETURN datetime.realtime() as now")

    val now = single(result.columnAs[ZonedDateTime]("now"))

    now shouldBe a[ZonedDateTime]
  }

  test("should get current default localdatetime") {
    val result = executeWith(supported, "RETURN localdatetime() as now")

    val now = single(result.columnAs[LocalDateTime]("now"))

    now shouldBe a[LocalDateTime]
  }

  test("should get current default date") {
    val result = executeWith(supported, "RETURN date() as now")

    val now = single(result.columnAs[LocalDate]("now"))

    now shouldBe a[LocalDate]
  }

  test("should get current default time") {
    val result = executeWith(supported, "RETURN time() as now")

    val now = single(result.columnAs[OffsetTime]("now"))

    now shouldBe a[OffsetTime]
  }

  test("should get current default localtime") {
    val result = executeWith(supported, "RETURN localtime() as now")

    val now = single(result.columnAs[LocalTime]("now"))

    now shouldBe a[LocalTime]
  }

  test("timstamp should be query local") {
    //older versions don't use the clock which we fake in this test
    val result = executeSingle("UNWIND range(1, 1000) AS ignore RETURN timestamp() AS t").toList

    result.map(m => m("t")).distinct should have size 1
  }

  def single[T](values: Iterator[T]):T = {
    val value = values.next()
    values.hasNext shouldBe false
    value
  }
}
