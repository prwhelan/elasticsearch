/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.ingest.geoip;

import org.elasticsearch.cluster.metadata.ProjectId;

/**
 * Provides construction and initialization logic for {@link IpDatabase} instances.
 */
public interface IpDatabaseProvider {

    /**
     * Determines if the given database name corresponds to an expired database. Expired databases will not be loaded.
     * <br/><br/>
     * Verifying database expiration is left to each provider implementation to determine. A return value of <code>false</code> does not
     * preclude the possibility of a provider returning <code>true</code> in the future.
     *
     * @param projectId projectId to look for database.
     * @param name the name of the database to provide.
     * @return <code>false</code> IFF the requested database file is expired,
     *         <code>true</code> for all other cases (including unknown file name, file missing, wrong database type, etc).
     */
    Boolean isValid(ProjectId projectId, String name);

    /**
     * @param projectId projectId to look for database.
     * @param name the name of the database to provide.
     * @return a ready-to-use database instance, or <code>null</code> if no database could be loaded.
     */
    IpDatabase getDatabase(ProjectId projectId, String name);
}
