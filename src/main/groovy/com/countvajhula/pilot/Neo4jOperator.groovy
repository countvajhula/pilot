package com.countvajhula.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.neo4j.*
import com.tinkerpop.blueprints.pgm.impls.neo4jbatch.*
import GraphInterface.MutationIntent


class Neo4jOperator extends GraphDbOperator implements GraphInterface {

	static Map connectionPool = [:] // neo4j requires a single graph handle to be open per graph per JVM instance

	public Neo4jOperator(String url, boolean readOnly, boolean upgradeIfNecessary) {

		super(url, readOnly)

		synchronized(connectionPool) {
			if (!connectionPool[url]) {
				Graph g_global
				if (upgradeIfNecessary) {
					Map<String, String> config = new HashMap<String, String>()
					config.put("allow_store_upgrade", "true")
					g_global = new Neo4jGraph(url, config)
				} else {
					g_global = new Neo4jGraph(url)
				}
				if (!g_global) {
					throw new Exception("Could not create Neo4jGraph object for URL: ${url}!")
				}
				connectionPool[url] = ['graph':g_global, 'nActiveConnections':0]
				println "Neo4j graph initialized."
			}
			g = connectionPool[url]['graph']
			connectionPool[url]['nActiveConnections'] += 1
		}

		println "Neo4j graph connection obtained."
	}

	Vertex getVertex(long id) {
		return g.getVertex(id)
	}

	Edge getEdge(long id) {
		return g.getEdge(id)
	}

	void beginManagedTransaction(MutationIntent transactionType) {
		if (!transactionInProgress) {
			switch (transactionType) {
				case MutationIntent.BATCHINSERT:
					//shutdown standard neo4j handle
					g.shutdown()
					//load it as a batchgraph
					g = new Neo4jBatchGraph(graphUrl)
					println "Neo4j batch inserter activated"
					break
				default: //including 'null'
					break
			}
		}

		super.beginManagedTransaction(transactionType)
	}

	void concludeManagedTransaction() {
		if (transactionInProgress) {
			if (mutationIntent == MutationIntent.BATCHINSERT) {
				g.shutdown()
				g = new Neo4jGraph(graphUrl)
			}
		}
		super.concludeManagedTransaction()
	}

	void shutdown() {
		if (g) {
			super.shutdown()
			synchronized(connectionPool) {
				connectionPool[graphUrl]['nActiveConnections'] -= 1

				if (connectionPool[graphUrl]['nActiveConnections'] <= 0) {
					connectionPool[graphUrl]['graph'].shutdown()
					connectionPool[graphUrl] = null
					println "Neo4j graph shutdown."
				}
			}
			g = null
			println "Neo4j graph connection released."
		}
	}

}
