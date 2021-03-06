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
package org.neo4j.causalclustering.messaging.marshalling;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import org.neo4j.storageengine.api.WritableChannel;

public class OutputStreamWritableChannel implements WritableChannel
{
    private final DataOutputStream dataOutputStream;

    public OutputStreamWritableChannel( OutputStream outputStream )
    {
        this.dataOutputStream = new DataOutputStream( outputStream );
    }

    @Override
    public WritableChannel put( byte value ) throws IOException
    {
        dataOutputStream.writeByte( value );
        return this;
    }

    @Override
    public WritableChannel putShort( short value ) throws IOException
    {
        dataOutputStream.writeShort( value );
        return this;
    }

    @Override
    public WritableChannel putInt( int value ) throws IOException
    {
        dataOutputStream.writeInt( value );
        return this;
    }

    @Override
    public WritableChannel putLong( long value ) throws IOException
    {
        dataOutputStream.writeLong( value );
        return this;
    }

    @Override
    public WritableChannel putFloat( float value ) throws IOException
    {
        dataOutputStream.writeFloat( value );
        return this;
    }

    @Override
    public WritableChannel putDouble( double value ) throws IOException
    {
        dataOutputStream.writeDouble( value );
        return this;
    }

    @Override
    public WritableChannel put( byte[] value, int length ) throws IOException
    {
        dataOutputStream.write( value, 0, length );
        return this;
    }
}
