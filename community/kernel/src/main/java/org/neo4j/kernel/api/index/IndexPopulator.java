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
package org.neo4j.kernel.api.index;

import java.io.UncheckedIOException;
import java.util.Collection;

import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.kernel.api.exceptions.index.IndexEntryConflictException;
import org.neo4j.kernel.impl.api.index.PhaseTracker;
import org.neo4j.kernel.impl.api.index.UpdateMode;
import org.neo4j.kernel.impl.api.index.updater.SwallowingIndexUpdater;
import org.neo4j.storageengine.api.NodePropertyAccessor;
import org.neo4j.storageengine.api.schema.IndexSample;
import org.neo4j.storageengine.api.schema.PopulationProgress;
import org.neo4j.storageengine.api.schema.StoreIndexDescriptor;

/**
 * Used for initial population of an index.
 */
public interface IndexPopulator extends IndexConfigProvider
{
    /**
     * Remove all data in the index and paves the way for populating an index.
     *
     * @throws UncheckedIOException on I/O error.
     */
    void create();

    /**
     * Closes and deletes this index.
     */
    void drop();

    /**
     * Called when initially populating an index over existing data. Guaranteed to be
     * called by the same thread every time. All data coming in here is guaranteed to not
     * have been added to this index previously, so no checks needs to be performed before applying it.
     * Implementations may verify constraints at this time, or defer them until the first verification
     * of {@link #verifyDeferredConstraints(NodePropertyAccessor)}.
     *
     * @param updates batch of node property updates that needs to be inserted. Node ids will be retrieved using
     * {@link IndexEntryUpdate#getEntityId()} method and property values will be retrieved using
     * {@link IndexEntryUpdate#values()} method.
     * @throws IndexEntryConflictException if this is a uniqueness index and any of the updates are detected
     * to violate that constraint. Implementations may choose to not detect in this call, but instead do one efficient
     * pass over the index in {@link #verifyDeferredConstraints(NodePropertyAccessor)}.
     * @throws UncheckedIOException on I/O error.
     */
    void add( Collection<? extends IndexEntryUpdate<?>> updates ) throws IndexEntryConflictException;

    /**
     * Verifies that each value in this index is unique.
     * This method is called after the index has been fully populated and is guaranteed to not have
     * concurrent changes while executing.
     *
     * @param nodePropertyAccessor {@link NodePropertyAccessor} for accessing properties from database storage
     * in the event of conflicting values.
     * @throws IndexEntryConflictException for first detected uniqueness conflict, if any.
     * @throws UncheckedIOException on error reading from source files.
     */
    void verifyDeferredConstraints( NodePropertyAccessor nodePropertyAccessor ) throws IndexEntryConflictException;

    /**
     * Return an updater for applying a set of changes to this index, generally this will be a set of changes from a
     * transaction.
     *
     * Index population goes through the existing data in the graph and feeds relevant data to this populator.
     * Simultaneously as population progresses there might be incoming updates
     * from committing transactions, which needs to be applied as well. This populator will only receive updates
     * for nodes that it already has seen. Updates coming in here must be applied idempotently as the same data
     * may have been {@link #add(Collection) added previously}.
     * Updates can come in two different {@link IndexEntryUpdate#updateMode()} modes}.
     * <ol>
     *   <li>{@link UpdateMode#ADDED} means that there's an added property to a node already seen by this
     *   populator and so needs to be added. Note that this addition needs to be applied idempotently.
     *   <li>{@link UpdateMode#CHANGED} means that there's a change to a property for a node already seen by
     *   this populator and that this new change needs to be applied. Note that this change needs to be
     *   applied idempotently.</li>
     *   <li>{@link UpdateMode#REMOVED} means that a property already seen by this populator or even the node itself
     *   has been removed and need to be removed from this index as well. Note that this removal needs to be
     *   applied idempotently.</li>
     * </ol>
     *
     * @param accessor accesses property data if implementation needs to be able look up property values while populating.
     * @return an {@link IndexUpdater} which will funnel changes that happen concurrently with index population
     * into the population and incorporating them as part of the index population.
     */
    IndexUpdater newPopulatingUpdater( NodePropertyAccessor accessor );

    /**
     * Close this populator and releases any resources related to it.
     * If {@code populationCompletedSuccessfully} is {@code true} then it must mark this index
     * as {@link InternalIndexState#ONLINE} so that future invocations of its parent
     * {@link IndexProvider#getInitialState(StoreIndexDescriptor)} also returns {@link InternalIndexState#ONLINE}.
     *
     * @param populationCompletedSuccessfully {@code true} if the index population was successful, where the index should
     * be marked as {@link InternalIndexState#ONLINE}. Supplying {@code false} can have two meanings:
     * <ul>
     *     <li>if {@link #markAsFailed(String)} have been called the end state should be {@link InternalIndexState#FAILED}.
     *     This method call should also make sure that the failure message gets stored for retrieval the next open too.</li>
     *     <li>if {@link #markAsFailed(String)} have NOT been called the end state should be {@link InternalIndexState#POPULATING}</li>
     * </ul>
     */
    void close( boolean populationCompletedSuccessfully );

    /**
     * Called then a population failed. The failure string should be stored for future retrieval by
     * {@link IndexProvider#getPopulationFailure(StoreIndexDescriptor)}. Called before {@link #close(boolean)}
     * if there was a failure during population.
     *
     * @param failure the description of the failure.
     * @throws UncheckedIOException if marking failed.
     */
    void markAsFailed( String failure );

    /**
     * Add the given {@link IndexEntryUpdate update} to the sampler for this index.
     *
     * @param update update to include in sample
     */
    void includeSample( IndexEntryUpdate<?> update );

    /**
     * @return {@link IndexSample} from samples collected by {@link #includeSample(IndexEntryUpdate)} calls.
     */
    IndexSample sampleResult();

    /**
     * Returns actual population progress, given the progress of the scan. This is for when a populator needs to do
     * significant work after scan has completed where the scan progress can be seen as only a part of the whole progress.
     * @param scanProgress progress of the scan.
     * @return progress of the population of this index as a whole.
     */
    default PopulationProgress progress( PopulationProgress scanProgress )
    {
        return scanProgress;
    }

    IndexPopulator EMPTY = new Adapter();

    default void scanCompleted( PhaseTracker phaseTracker ) throws IndexEntryConflictException
    {   // no-op by default
    }

    class Adapter implements IndexPopulator
    {
        @Override
        public void create()
        {
        }

        @Override
        public void drop()
        {
        }

        @Override
        public void add( Collection<? extends IndexEntryUpdate<?>> updates )
        {
        }

        @Override
        public IndexUpdater newPopulatingUpdater( NodePropertyAccessor accessor )
        {
            return SwallowingIndexUpdater.INSTANCE;
        }

        @Override
        public void scanCompleted( PhaseTracker phaseTracker )
        {
        }

        @Override
        public void close( boolean populationCompletedSuccessfully )
        {
        }

        @Override
        public void markAsFailed( String failure )
        {
        }

        @Override
        public void includeSample( IndexEntryUpdate<?> update )
        {
        }

        @Override
        public IndexSample sampleResult()
        {
            return new IndexSample();
        }

        @Override
        public void verifyDeferredConstraints( NodePropertyAccessor nodePropertyAccessor ) throws IndexEntryConflictException
        {
        }
    }
}
