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
package org.neo4j.backup.impl;

import java.nio.file.Path;
import java.util.Arrays;

import org.neo4j.commandline.admin.AdminCommand;
import org.neo4j.commandline.admin.CommandFailed;
import org.neo4j.commandline.admin.IncorrectUsage;
import org.neo4j.commandline.admin.OutsideWorld;

import static java.lang.String.format;

class OnlineBackupCommand implements AdminCommand
{
    private final OutsideWorld outsideWorld;
    private final OnlineBackupContextFactory contextBuilder;
    private final BackupStrategyCoordinatorFactory backupStrategyCoordinatorFactory;
    private final BackupSupportingClassesFactory backupSupportingClassesFactory;

    /**
     * The entry point for neo4j admin tool's online backup functionality.
     *
     * @param outsideWorld provides a way to interact with the filesystem and output streams
     * @param contextBuilder helper class to validate, process and return a grouped result of processing the command line arguments
     * @param backupSupportingClassesFactory necessary for constructing the strategy for backing up over the causal clustering transaction protocol
     * @param backupStrategyCoordinatorFactory class that actually handles the logic of performing a backup
     */
    OnlineBackupCommand( OutsideWorld outsideWorld, OnlineBackupContextFactory contextBuilder,
                         BackupSupportingClassesFactory backupSupportingClassesFactory,
                         BackupStrategyCoordinatorFactory backupStrategyCoordinatorFactory )
    {
        this.outsideWorld = outsideWorld;
        this.contextBuilder = contextBuilder;
        this.backupSupportingClassesFactory = backupSupportingClassesFactory;
        this.backupStrategyCoordinatorFactory = backupStrategyCoordinatorFactory;
    }

    @Override
    public void execute( String[] args ) throws IncorrectUsage, CommandFailed
    {
        OnlineBackupContext onlineBackupContext = contextBuilder.createContext( args );
        protocolWarn( onlineBackupContext );
        try ( BackupSupportingClasses backupSupportingClasses = backupSupportingClassesFactory.createSupportingClasses( onlineBackupContext.getConfig() ) )
        {
            // Make sure destination exists
            checkDestination( onlineBackupContext.getRequiredArguments().getDirectory() );
            checkDestination( onlineBackupContext.getRequiredArguments().getReportDir() );

            BackupStrategyCoordinator backupStrategyCoordinator =
                    backupStrategyCoordinatorFactory.backupStrategyCoordinator( onlineBackupContext, backupSupportingClasses.getBackupProtocolService(),
                            backupSupportingClasses.getBackupDelegator(), backupSupportingClasses.getPageCache() );

            backupStrategyCoordinator.performBackup( onlineBackupContext );
            outsideWorld.stdOutLine( "Backup complete." );
        }
    }

    private void checkDestination( Path path ) throws CommandFailed
    {
        if ( !outsideWorld.fileSystem().isDirectory( path.toFile() ) )
        {
            throw new CommandFailed( format( "Directory '%s' does not exist.", path ) );
        }
    }

    private void protocolWarn( OnlineBackupContext onlineBackupContext )
    {
        SelectedBackupProtocol selectedBackupProtocol = onlineBackupContext.getRequiredArguments().getSelectedBackupProtocol();
        if ( !SelectedBackupProtocol.ANY.equals( selectedBackupProtocol ) )
        {
            final String compatibleProducts;
            switch ( selectedBackupProtocol )
            {
            case CATCHUP:
                compatibleProducts = "causal clustering";
                break;
            case COMMON:
                compatibleProducts = "HA and single";
                break;
            default:
                throw new IllegalArgumentException( "Unhandled protocol " + selectedBackupProtocol );
            }
            outsideWorld.stdOutLine( format( "The selected protocol `%s` means that it is only compatible with %s instances", selectedBackupProtocol.getName(),
                    compatibleProducts ) );
        }
    }
}
