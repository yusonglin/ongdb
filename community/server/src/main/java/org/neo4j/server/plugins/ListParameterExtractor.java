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
package org.neo4j.server.plugins;

import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.server.rest.repr.BadInputException;

abstract class ListParameterExtractor extends ParameterExtractor
{
    ListParameterExtractor( TypeCaster caster, Class<?> type, Parameter param, Description description )
    {
        super( caster, type, param, description );
    }

    @Override
    Object extract( GraphDatabaseAPI graphDb, Object source, ParameterList parameters ) throws BadInputException
    {
        Object[] result = caster.getList( graphDb, parameters, name );
        if ( result != null )
        {
            if ( type.isPrimitive() )
            {
                return caster.convert( result );
            }
            return convert( result );
        }
        if ( optional )
        {
            return null;
        }
        throw new IllegalArgumentException( "Mandatory argument \"" + name + "\" not supplied." );
    }

    abstract Object convert( Object[] result );

    @Override
    void describe( ParameterDescriptionConsumer consumer )
    {
        consumer.describeListParameter( name, type, optional, description );
    }
}
