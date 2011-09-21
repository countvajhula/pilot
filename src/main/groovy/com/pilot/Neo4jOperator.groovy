package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.neo4j.*


class Neo4jOperator extends GraphDbOperator implements GraphInterface {

	public static final String STORAGE_MODE = "local"

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
		return g.getVertex(id) //TODO:test
	}

}
