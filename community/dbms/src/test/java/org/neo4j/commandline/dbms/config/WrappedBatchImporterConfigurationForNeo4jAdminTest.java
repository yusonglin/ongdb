/*
 * Copyright (c) 2018-2020 "Graph Foundation"
 * Graph Foundation, Inc. [https://graphfoundation.org]
 *
 * Copyright (c) 2002-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of ONgDB.
 *
 * ONgDB is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.commandline.dbms.config;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import org.neo4j.unsafe.impl.batchimport.Configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.io.ByteUnit.kibiBytes;
import static org.neo4j.unsafe.impl.batchimport.Configuration.DEFAULT;

class WrappedBatchImporterConfigurationForNeo4jAdminTest
{
    @Test
    void shouldDelegateDenseNodeThreshold()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public int denseNodeThreshold()
            {
                return expected;
            }
        }, Configuration::denseNodeThreshold, 1, 20 );
    }

    @Test
    void shouldDelegateMovingAverageSize()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public int movingAverageSize()
            {
                return expected;
            }
        }, Configuration::movingAverageSize, 100, 200 );
    }

    @Test
    void shouldDelegateSequentialBackgroundFlushing()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public boolean sequentialBackgroundFlushing()
            {
                return expected;
            }
        }, Configuration::sequentialBackgroundFlushing, true, false );
    }

    @Test
    void shouldDelegateBatchSize()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public int batchSize()
            {
                return expected;
            }
        }, Configuration::batchSize, 100, 200 );
    }

    @Test
    void shouldOverrideMaxNumberOfProcessors()
    {
        shouldOverride( expected -> new Configuration()
        {
            @Override
            public int batchSize()
            {
                return expected;
            }
        }, Configuration::maxNumberOfProcessors, DEFAULT.maxNumberOfProcessors() + 1, DEFAULT.maxNumberOfProcessors() + 10 );
    }

    @Test
    void shouldDelegateParallelRecordWrites()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public boolean parallelRecordWrites()
            {
                return expected;
            }
        }, Configuration::parallelRecordWrites, true, false );
    }

    @Test
    void shouldDelegateParallelRecordReads()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public boolean parallelRecordReads()
            {
                return expected;
            }
        }, Configuration::parallelRecordReads, true, false );
    }

    @Test
    void shouldDelegateHighIO()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public boolean highIO()
            {
                return expected;
            }
        }, Configuration::highIO, true, false );
    }

    @Test
    void shouldDelegateMaxMemoryUsage()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public long maxMemoryUsage()
            {
                return expected;
            }
        }, Configuration::maxMemoryUsage, kibiBytes( 10 ), kibiBytes( 20 ) );
    }

    @Test
    void shouldDelegateAllowCacheAllocationOnHeap()
    {
        shouldDelegate( expected -> new Configuration()
        {
            @Override
            public boolean allowCacheAllocationOnHeap()
            {
                return expected;
            }
        }, Configuration::allowCacheAllocationOnHeap, true, false );
    }

    @SafeVarargs
    private final <T> void shouldDelegate( Function<T,Configuration> configFactory, Function<Configuration,T> getter, T... expectedValues )
    {
        for ( T expectedValue : expectedValues )
        {
            // given
            Configuration configuration = configFactory.apply( expectedValue );

            // when
            WrappedBatchImporterConfigurationForNeo4jAdmin wrapped = new WrappedBatchImporterConfigurationForNeo4jAdmin( configuration );

            // then
            assertEquals( expectedValue, getter.apply( wrapped ) );
        }

        // then
        assertEquals( getter.apply( DEFAULT ), getter.apply( new WrappedBatchImporterConfigurationForNeo4jAdmin( DEFAULT ) ) );
    }

    @SafeVarargs
    private final <T> void shouldOverride( Function<T,Configuration> configFactory, Function<Configuration,T> getter, T... values )
    {
        for ( T value : values )
        {
            // given
            Configuration configuration = configFactory.apply( value );
            WrappedBatchImporterConfigurationForNeo4jAdmin vanilla = new WrappedBatchImporterConfigurationForNeo4jAdmin( DEFAULT );

            // when
            WrappedBatchImporterConfigurationForNeo4jAdmin wrapped = new WrappedBatchImporterConfigurationForNeo4jAdmin( configuration );

            // then
            assertEquals( getter.apply( vanilla ), getter.apply( wrapped ) );
        }
    }
}
