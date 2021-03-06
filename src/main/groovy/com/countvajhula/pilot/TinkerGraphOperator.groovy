package com.countvajhula.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.tg.*


class TinkerGraphOperator extends GraphDbOperator implements GraphInterface {

	public TinkerGraphOperator(String url, boolean readOnly, boolean upgradeIfNecessary) {
		//upgradeIfNecessary currently not implemented
		super(url, readOnly)

		if (url) {
			g = new TinkerGraph(url) //will be serialized to disk upon shutdown()
		} else {
			g = new TinkerGraph()
		}
		if (!g) {
			throw new Exception("Could not create TinkerGraph object for URL: ${url}!")
		}

		println "TinkerGraph graph initialized."
	}

	Vertex getVertex(long id) {
		return g.getVertex(id) //TODO:test
	}

	Edge getEdge(long id) {
		return g.getEdge(id)
	}

	void shutdown() {
		if (g) {
			super.shutdown()
			g.shutdown()
			g = null
		}
	}

}
