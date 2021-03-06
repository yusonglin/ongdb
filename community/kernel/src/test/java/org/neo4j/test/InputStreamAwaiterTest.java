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
package org.neo4j.test;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.neo4j.time.Clocks;
import org.neo4j.time.FakeClock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;

class InputStreamAwaiterTest
{
    @Test
    void shouldWaitForALineWithoutBlocking() throws Exception
    {
        // given
        FakeClock clock = getFakeClock();
        InputStream inputStream = spy( new MockInputStream( new Ticker( clock, 5, TimeUnit.MILLISECONDS ),
                                                            lines( "important message" ) ) );
        InputStreamAwaiter awaiter = new InputStreamAwaiter( clock, inputStream );

        // when
        awaiter.awaitLine( "important message", 5, TimeUnit.SECONDS );
    }

    @Test
    void shouldTimeoutWhenDifferentContentProvided()
    {
        // given
        FakeClock clock = getFakeClock();
        InputStream inputStream = spy( new MockInputStream( new Ticker( clock, 1, TimeUnit.SECONDS ),
                                                            lines( "different content" ),
                                                            lines( "different message" ) ) );
        InputStreamAwaiter awaiter = new InputStreamAwaiter( clock, inputStream );

        // when
        assertThrows( TimeoutException.class, () -> awaiter.awaitLine( "important message", 5, TimeUnit.SECONDS ) );
    }

    @Test
    void shouldTimeoutWhenNoContentProvided() throws Exception
    {
        // given
        FakeClock clock = getFakeClock();
        InputStream inputStream = spy( new MockInputStream( new Ticker( clock, 1, TimeUnit.SECONDS ) ) );
        InputStreamAwaiter awaiter = new InputStreamAwaiter( clock, inputStream );

        // when
        assertThrows( TimeoutException.class, () -> awaiter.awaitLine( "important message", 5, TimeUnit.SECONDS ) );
    }

    private static String lines( String... lines )
    {
        StringBuilder result = new StringBuilder();
        for ( String line : lines )
        {
            result.append( line ).append( System.lineSeparator() );
        }
        return result.toString();
    }

    private static FakeClock getFakeClock()
    {
        return Clocks.fakeClock();
    }

    private static class Ticker
    {
        private final FakeClock clock;
        private final long duration;
        private final TimeUnit timeUnit;

        Ticker( FakeClock clock, long duration, TimeUnit timeUnit )
        {
            this.clock = clock;
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        void tick()
        {
            clock.forward( duration, timeUnit );
        }
    }

    private static class MockInputStream extends InputStream
    {
        private final Ticker ticker;
        private final byte[][] chunks;
        private int chunk;

        MockInputStream( Ticker ticker, String... chunks )
        {
            this.ticker = ticker;
            this.chunks = new byte[chunks.length][];
            for ( int i = 0; i < chunks.length; i++ )
            {
                this.chunks[i] = chunks[i].getBytes();
            }
        }

        @Override
        public int available()
        {
            ticker.tick();
            if ( chunk >= chunks.length )
            {
                return 0;
            }
            return chunks[chunk].length;
        }

        @Override
        public int read( byte[] target )
        {
            if ( chunk >= chunks.length )
            {
                return 0;
            }
            byte[] source = chunks[chunk++];
            System.arraycopy( source, 0, target, 0, source.length );
            return source.length;
        }

        @Override
        public int read()
        {
            throw new UnsupportedOperationException();
        }
    }
}
