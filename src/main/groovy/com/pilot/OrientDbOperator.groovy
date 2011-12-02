package com.pilot

import com.orientechnologies.common.collection.*
import com.orientechnologies.common.*
import com.orientechnologies.orient.core.*
import com.orientechnologies.orient.core.db.graph.*
import com.orientechnologies.orient.core.record.impl.*
import com.orientechnologies.orient.core.sql.query.*
import com.orientechnologies.orient.core.intent.*
import com.orientechnologies.orient.core.config.*
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*


class OrientDbOperator extends GraphDbOperator implements GraphInterface {

	public static final String STORAGE_MODE = "local"

	public OrientDbOperator(String url, boolean readOnly) {
		super(url, readOnly)
		initializeGraph (url, readOnly)
	}

	void initializeGraph (String url, boolean readOnly) throws Exception {

		super.initializeGraph(url, readOnly)

		OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(Boolean.TRUE);

		//disable all caches
		//OGlobalConfiguration.STORAGE_CACHE_SIZE.setValue(0);
		//OGlobalConfiguration.DB_USE_CACHE.setValue(false);
		//OGlobalConfiguration.DB_CACHE_SIZE.setValue(0);

		g = new OrientGraph(STORAGE_MODE + ":" + url)
		if (!g) {
			throw new Exception("Could not create OrientGraph object for URL: ${url}!")
		}

		println "OrientDB graph initialized."
	}

	Vertex getVertex(long id) {
		return g.getVertex("#5:"+id) //TODO:test
	}

	Edge getEdge(long id) {
		return g.getEdge("#6:"+id)
	}

	// use raw API to do a faster edge retrieval
	Edge getEdge(Vertex v1, Vertex v2, String edgeLabel) {
		OGraphDatabase ographdb = g.getRawGraph()
		Set<ODocument> edges = ographdb.getEdgesBetweenVertexes(v1.getRawElement(), v2.getRawElement(), (String[])[edgeLabel])
		Edge edge
		if (edges) {
			edge = new OrientEdge(g, edges.iterator().next())
		}
		return edge
	}

	void beginManagedTransaction(GraphInterface.MutationIntent transactionType) {
		if (!transactionInProgress) {
			switch (transactionType) {
				case GraphInterface.MutationIntent.BATCHINSERT:
					g.getRawGraph().declareIntent(new OIntentMassiveInsert())
					break
				default:
					break
			}
		}

		super.beginManagedTransaction(transactionType)
	}

	void concludeManagedTransaction() {
		if (transactionInProgress) {
			if (mutationIntent == GraphInterface.MutationIntent.BATCHINSERT) {
				g.getRawGraph().declareIntent(null)
			}
		}
		super.concludeManagedTransaction()
	}

}
