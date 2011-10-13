package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.neo4j.*
import com.tinkerpop.blueprints.pgm.impls.neo4jbatch.*


class Neo4jOperator extends GraphDbOperator implements GraphInterface {

	public Neo4jOperator(String url, boolean readOnly) {
		super(url, readOnly)
		initializeGraph (url, readOnly)
	}

	void initializeGraph (String url, boolean readOnly) {

		super.initializeGraph(url, readOnly)

		g = new Neo4jGraph(url)

		println "Neo4j graph initialized."
	}

	Vertex getVertex(long id) {
		return g.getVertex(id)
	}

	Edge getEdge(long id) {
		return g.getEdge(id)
	}

	void declareIntent(GraphInterface.MutationIntent intent) {
		if (transactionInProgress) {
			println "Mutation Intent cannot be changed inside a transaction! Ignoring..."
			//TODO: better to throw a custom exception and handle it in GraphManagerProxy
			return
		}
		switch (intent) {
			case GraphInterface.MutationIntent.BATCHINSERT:
				//shutdown standard neo4j handle
				g.shutdown()
				//load it as a batchgraph
				g = new Neo4jBatchGraph(graphUrl)
				println "Neo4j batch inserter activated"
				mutationIntent = GraphInterface.MutationIntent.BATCHINSERT
				break
			default: //including 'null'
				g.shutdown()
				g = new Neo4jGraph(graphUrl)
				mutationIntent = GraphInterface.MutationIntent.STANDARDTRANSACTION
		}
	}
}
