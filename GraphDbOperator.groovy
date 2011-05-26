package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import com.tinkerpop.blueprints.pgm.util.TransactionalGraphHelper
import com.tinkerpop.blueprints.pgm.util.TransactionalGraphHelper.CommitManager
import com.tinkerpop.gremlin.*
import com.orientechnologies.common.collection.*
import com.orientechnologies.common.*
import com.orientechnologies.orient.core.*
import com.orientechnologies.orient.core.db.graph.*
import com.orientechnologies.orient.core.record.impl.*
import com.orientechnologies.orient.core.sql.query.*
import com.orientechnologies.orient.core.intent.*
import com.orientechnologies.orient.core.config.*
import java.util.concurrent.Semaphore


class GraphDbOperator implements GraphInterface {

	static {
		Gremlin.load()
	}

	public static final String ORIENTDB_STORAGE_MODE = "local"

	private Graph g
	private String graphUrl
	private GraphInterface.GraphProvider graphProvider
	private CommitManager commitManager
	private numMutationsBeforeCommit
	private static Map graphWriteConnectionLocks = [:]
	private boolean readOnly
	
	//TODO:
	//FOAF, with degree as input
	//BFS, DFS (iterators)

	public GraphDbOperator(String url, GraphInterface.GraphProvider graphProvider, boolean readOnly) {
		Semaphore graphWriteConnectionLock
		graphWriteConnectionLock = graphWriteConnectionLocks[url]
		if (!graphWriteConnectionLock) {
			graphWriteConnectionLock = new Semaphore(1, true)
			graphWriteConnectionLocks[url] = graphWriteConnectionLock
		}
		initializeGraph(url, graphProvider, readOnly)
		numMutationsBeforeCommit = GraphInterface.DEFAULT_MUTATIONS_BEFORE_COMMIT
	}

	//change to return status
	//TODO: remove readOnly flag in future when pessimistic locking is supported by underlying db
	void initializeGraph(String url, GraphInterface.GraphProvider graphProvider, boolean readOnly) {
		this.readOnly = readOnly
		if (!readOnly) {
			graphWriteConnectionLocks[url].acquire()
			println "-ACQUIRED- GRAPH WRITE CONNECTION [${url}]"
		}
		graphUrl = url
		this.graphProvider = graphProvider
		if (graphProvider == GraphInterface.GraphProvider.ORIENTDB) {
			initializeGraph_OrientGraph(url)
		}
		println "Graph db initialized."
	}

	void initializeGraph_OrientGraph(String url) {
		OGlobalConfiguration.STORAGE_KEEP_OPEN.setValue(Boolean.TRUE);

		//disable all caches
		//OGlobalConfiguration.STORAGE_CACHE_SIZE.setValue(0);
		//OGlobalConfiguration.DB_USE_CACHE.setValue(false);
		//OGlobalConfiguration.DB_CACHE_SIZE.setValue(0);

		g = new OrientGraph(ORIENTDB_STORAGE_MODE + ":" + url)
	}

	void reinitializeGraph() {
		if (graphUrl && graphProvider) {
			if (!readOnly) {
				graphWriteConnectionLocks[graphUrl].release()
			}
			initializeGraph(graphUrl, graphProvider, readOnly)
		} else {
			println "db URL or provider not found!"
		}
	}

	void shutdown() {
		if (g) {
			if (!readOnly) {
				graphWriteConnectionLocks[graphUrl].release()
				println "-RELEASED- GRAPH WRITE CONNECTION [${graphUrl}]"
			}
			g.shutdown()
			g = null
		}
	}

	Graph getGraph() {
		return g
	}

	String getGraphUrl() {
		return graphUrl
	}

	void beginManagedTransaction() {
		beginManagedTransaction(numMutationsBeforeCommit)
	}

	void beginManagedTransaction(int numMutations) {
		if (commitManager) {
			println "Transaction already in progress!!"
			return
		}
		if (!numMutations) {
			numMutations = numMutationsBeforeCommit
		}
		commitManager = TransactionalGraphHelper.createCommitManager(g, numMutations);
		println "managed transaction begun..."
	}

	void concludeManagedTransaction() {
		commitManager.close()
		commitManager = null
		println "managed transaction concluded."
	}

	CommitManager getCommitManager() {
		if (!commitManager) {
			println "No transaction in progress!!"
		}
		return commitManager
	}

	void declareIntent(GraphInterface.MutationIntent intent) {
		switch (intent) {
			case GraphInterface.MutationIntent.MASSIVEINSERT:
				g.getRawGraph().declareIntent(new OIntentMassiveInsert())
				numMutationsBeforeCommit = GraphInterface.MASSIVEINSERT_MUTATIONS_BEFORE_COMMIT
				break
			case null:
				g.getRawGraph().declareIntent(null)
				numMutationsBeforeCommit = GraphInterface.DEFAULT_MUTATIONS_BEFORE_COMMIT
		}
	}

	void clear() {
		g.clear()
		//reinstate vertex and edge indices
		g.createAutomaticIndex(Index.VERTICES, Vertex.class, null)
		g.createAutomaticIndex(Index.EDGES, Edge.class, null)
	}

	List getVertices(String idProp) {
		List vertices = []
		if (idProp) {
			g.V[idProp].aggregate(vertices) >> -1
		} else {
			g.V.aggregate(vertices) >> -1
		}

		return vertices
	}

	List getEdges(String idProp) {
		List edges = []
		if (idProp) {
			g.E[idProp].aggregate(edges) >> -1
		} else {
			g.E.aggregate(edges) >> -1
		}

		return edges
	}

	long getVertexCount() {
		return g.V.count()
	}

	long getEdgeCount() {
		return g.E.count()
	}

	List getNeighbors(Vertex v, String idProp, String alongEdge) {
		//idProp is the property of the result vertices that will be returned
		//if null, then the vertices themselves will be returned
		List neighbors = []
		List tempList = []
		if (idProp) {
			if (alongEdge) {
				v.bothE(alongEdge).bothV.filter { node -> !node.id.equals(v.id) }[idProp].aggregate(neighbors) >> -1
				//add current node back if there is a self-connecting edge
				v.outE(alongEdge).inV.filter { node -> node.id.equals(v.id) }[idProp].aggregate(tempList) >> -1
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			} else {
				v.bothE.bothV.filter { node -> !node.id.equals(v.id) }[idProp].aggregate(neighbors) >> -1
				//add current node back if there is a self-connecting edge
				v.outE.inV.filter { node -> node.id.equals(v.id) }[idProp].aggregate(tempList) >> -1
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			}
		} else {
			if (alongEdge) {
				v.bothE(alongEdge).bothV.filter { node -> !node.id.equals(v.id) }.aggregate(neighbors) >> -1
				//add current node back if there is a self-connecting edge
				v.outE(alongEdge).inV.filter { node -> node.id.equals(v.id) }.aggregate(tempList) >> -1
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			} else {
				v.bothE.bothV.filter { node -> !node.id.equals(v.id) }.aggregate(neighbors) >> -1
				//add current node back if there is a self-connecting edge
				v.outE.inV.filter { node -> node.id.equals(v.id) }.aggregate(tempList) >> -1
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			}
		}

		return neighbors
	}

	Vertex getVertex(String property, String value) {
		AutomaticIndex vertexIndex = g.getIndex(Index.VERTICES, Vertex.class)
		Iterable<Vertex> itr = vertexIndex.get(property, value)
		Vertex vertex
		if (itr) {
			vertex = itr.next()
		}

		return vertex
	}
	
	Vertex addVertex() {
		return g.addVertex(null)
	}

	void removeVertex(Vertex vv) {
		g.removeVertex(vv)
	}

	void removeEdge(Edge ee) {
		g.removeEdge(ee)
	}

	Edge getEdge(Vertex v1, Vertex v2, String edgeLabel) {
		//return getEdge_NonIndexed(v1, v2, edgeLabel)
		return getEdge_OrientGraph(v1, v2, edgeLabel)
	}

	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel) {
		return addEdge_NonIndexed(v1, v2, edgeLabel)
	}
	
	Edge getEdge_NonIndexed(Vertex v1, Vertex v2, String edgeLabel) {
		def edges = []
		if (edgeLabel) {
			if (v1.id.equals(v2)) {
				v1.outE(edgeLabel).inV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			} else {
				v1.outE(edgeLabel).inV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
				v1.inE(edgeLabel).outV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			}
		} else {
			if (v1.id.equals(v2)) {
				v1.outE.inV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			} else {
				v1.outE.inV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
				v1.inE.outV{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			}
		}

		if (edges) {
			return edges[0]
		}
		return null
	}

	// use raw API to do a faster edge retrieval
	Edge getEdge_OrientGraph(Vertex v1, Vertex v2, String edgeLabel) {
		OGraphDatabase ographdb = g.getRawGraph()
		Set<ODocument> edges = ographdb.getEdgesBetweenVertexes(v1.getRawElement(), v2.getRawElement(), (String[])[edgeLabel])
		Edge edge
		if (edges) {
			edge = new OrientEdge(g, edges.iterator().next())
		}
		return edge
	}

	Edge addEdge_NonIndexed(Vertex v1, Vertex v2, String edgeLabel) {
		return g.addEdge(null, v1, v2, edgeLabel)
	}

	void setElementProperty(Element elem, String property, Object value) {
		elem.setProperty(property, value)
	}

	Object getElementProperty(Element elem, String property) {
		return elem.getProperty(property)
	}

}
