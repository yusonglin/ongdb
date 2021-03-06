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
package org.neo4j.causalclustering.routing.procedure;

import java.util.List;
import java.util.stream.Stream;

import org.neo4j.causalclustering.routing.Endpoint;
import org.neo4j.causalclustering.routing.Role;
import org.neo4j.helpers.AdvertisedSocketAddress;

import static java.util.stream.Collectors.toList;

public final class RoutingResultFormatHelper
{

    public static List<Endpoint> parseEndpoints( Object[] addresses, Role role )
    {
        return Stream.of( addresses )
                .map( rawAddress -> parseAddress( (String) rawAddress ) )
                .map( address -> new Endpoint( address, role ) )
                .collect( toList() );
    }

    private static AdvertisedSocketAddress parseAddress( String address )
    {
        String[] split = address.split( ":" );
        return new AdvertisedSocketAddress( split[0], Integer.valueOf( split[1] ) );
    }
}
