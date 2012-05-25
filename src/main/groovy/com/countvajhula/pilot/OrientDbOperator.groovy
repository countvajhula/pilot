package com.countvajhula.pilot

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
import GraphInterface.MutationIntent


class OrientDbOperator extends GraphDbOperator implements GraphInterface {

	public static final String STORAGE_MODE = "local"

	private int vertexClusterId = 5
	private int edgeClusterId = 6

	public OrientDbOperator(String url, boolean readOnly, boolean upgradeIfNecessary) {
		//upgradeIfNecessary currently not implemented

		super(url, readOnly)

		OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(Boolean.TRUE);

		//disable all caches
		//OGlobalConfiguration.STORAGE_CACHE_SIZE.setValue(0);
		//OGlobalConfiguration.DB_USE_CACHE.setValue(false);
		//OGlobalConfiguration.DB_CACHE_SIZE.setValue(0);

		g = new OrientGraph(STORAGE_MODE + ":" + url)
		if (!g) {
			throw new Exception("Could not create OrientGraph object for URL: ${url}!")
		}
		vertexClusterId = g.getRawGraph().getVertexBaseClass().getDefaultClusterId()
		edgeClusterId = g.getRawGraph().getEdgeBaseClass().getDefaultClusterId()

		println "OrientDB graph initialized."
	}

	Vertex getVertex(long id) {
		return g.getVertex("#" + vertexClusterId + ":" + id) //TODO:test
	}

	Edge getEdge(long id) {
		return g.getEdge("#" + edgeClusterId + ":" + id)
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

	void beginManagedTransaction(MutationIntent transactionType) {
		if (!transactionInProgress) {
			switch (transactionType) {
				case MutationIntent.BATCHINSERT:
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
			if (mutationIntent == MutationIntent.BATCHINSERT) {
				g.getRawGraph().declareIntent(null)
			}
		}
		super.concludeManagedTransaction()
	}

	void shutdown() {
		if (g) {
			super.shutdown()
			g.shutdown()
			g = null
		}
	}

}
