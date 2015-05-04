/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.usergrid.corepersistence.results;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.apache.usergrid.corepersistence.pipeline.PipelineResult;
import org.apache.usergrid.corepersistence.pipeline.read.ResultsPage;
import org.apache.usergrid.corepersistence.util.CpEntityMapUtils;
import org.apache.usergrid.persistence.EntityFactory;
import org.apache.usergrid.persistence.Results;
import org.apache.usergrid.persistence.collection.MvccEntity;
import org.apache.usergrid.persistence.model.entity.Entity;
import org.apache.usergrid.persistence.model.entity.Id;

import com.google.common.base.Optional;

import rx.Observable;


/**
 * Our proxy to allow us to subscribe to observable results, then return them as an iterator.  A bridge for 2.0 -> 1.0
 * code.  This should not be used on any new code, and will eventually be deleted
 */
public class ObservableQueryExecutor implements QueryExecutor {

    private final Observable<Results> resultsObservable;

    public Iterator<Results> iterator;


    public ObservableQueryExecutor( final Observable<PipelineResult<ResultsPage>> resultsObservable ) {
       //map to our old results objects, return a default empty if required
        this.resultsObservable = resultsObservable.map( resultsPage -> createResults( resultsPage ) ).defaultIfEmpty( new Results() );
    }



    private org.apache.usergrid.persistence.Entity mapEntity( final Entity cpEntity ) {


        final Id entityId = cpEntity.getId();

        org.apache.usergrid.persistence.Entity entity =
            EntityFactory.newEntity( entityId.getUuid(), entityId.getType() );

        Map<String, Object> entityMap = CpEntityMapUtils.toMap( cpEntity );
        entity.addProperties( entityMap );

        return entity;
    }

    private Results createResults( final PipelineResult<ResultsPage> pipelineResults ){

        final ResultsPage resultsPage = pipelineResults.getResult();
        final List<Entity> entityList = resultsPage.getEntityList();
        final List<org.apache.usergrid.persistence.Entity> resultsEntities = new ArrayList<>( entityList.size() );


        for(final Entity entity: entityList){
            resultsEntities.add( mapEntity( entity ) );
        }

        final Results results = Results.fromEntities( resultsEntities );

        if(pipelineResults.getCursor().isPresent()) {
            results.setCursor( pipelineResults.getCursor().get() );
        }

        return results;


    }


    @Override
    public Iterator<Results> iterator() {
        return this;
    }


    @Override
    public boolean hasNext() {

        if ( iterator == null ) {
            iterator = resultsObservable.toBlocking().getIterator();
        }


        return iterator.hasNext();
    }


    @Override
    public Results next() {
        if ( !hasNext() ) {
            throw new NoSuchElementException( "No more results present" );
        }
        final Results next = iterator.next();

        next.setQueryExecutor( this );

        return next;
    }


}