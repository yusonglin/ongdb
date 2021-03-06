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
package org.neo4j.kernel.impl.store.id;

import org.neo4j.graphdb.Resource;
import org.neo4j.kernel.impl.store.NeoStores;
import org.neo4j.kernel.impl.store.RecordStore;
import org.neo4j.kernel.impl.store.StoreType;
import org.neo4j.kernel.impl.store.record.AbstractBaseRecord;

public class RenewableBatchIdSequences implements Resource
{
    private final IdSequence[] types = new IdSequence[StoreType.values().length];

    public RenewableBatchIdSequences( NeoStores stores, int batchSize )
    {
        for ( StoreType type : StoreType.values() )
        {
            if ( type.isRecordStore() )
            {
                RecordStore<AbstractBaseRecord> store = stores.getRecordStore( type );
                if ( type.isLimitedIdStore() || batchSize == 1 )
                {
                    // This is a token store or otherwise meta-data store, so let's not add batching for it
                    types[type.ordinal()] = store;
                }
                else
                {
                    // This is a normal record store where id batching is beneficial
                    types[type.ordinal()] = new RenewableBatchIdSequence( store, batchSize, store::freeId );
                }
            }
        }
    }

    public long nextId( StoreType type )
    {
        return idGenerator( type ).nextId();
    }

    public IdSequence idGenerator( StoreType type )
    {
        return types[type.ordinal()];
    }

    @Override
    public void close()
    {
        for ( StoreType type : StoreType.values() )
        {
            IdSequence generator = idGenerator( type );
            if ( generator instanceof RenewableBatchIdSequence )
            {
                ((RenewableBatchIdSequence)generator).close();
            }
        }
    }
}
