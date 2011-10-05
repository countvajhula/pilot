package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.gremlin.*
import java.util.concurrent.Semaphore


/** Implements high-level graph operations that may be used by the application.
 */
class GraphDbOperator implements GraphInterface {

	static {
		Gremlin.load()
	}

	protected Graph g
	protected String graphUrl
	protected boolean transactionInProgress
	protected int numMutationsBeforeCommit
	protected static Map graphWriteConnectionLocks = [:] //can be private?
	protected boolean readOnly //can be private?
	
	//TODO:
	//FOAF, with degree as input
	//BFS, DFS (iterators)

	public GraphDbOperator(String url, boolean readOnly) {
		Semaphore graphWriteConnectionLock
		graphWriteConnectionLock = graphWriteConnectionLocks[url]
		if (!graphWriteConnectionLock) {
			graphWriteConnectionLock = new Semaphore(1, true)
			graphWriteConnectionLocks[url] = graphWriteConnectionLock
		}
		numMutationsBeforeCommit = GraphInterface.DEFAULT_MUTATIONS_BEFORE_COMMIT
		transactionInProgress = false
	}

	//change to return status
	//TODO: remove readOnly flag in future when pessimistic locking is supported by underlying db
	void initializeGraph(String url, boolean readOnly) {
		this.readOnly = readOnly
		if (!readOnly) {
			graphWriteConnectionLocks[url].acquire()
			println "-ACQUIRED- GRAPH WRITE CONNECTION [${url}]"
		}
		graphUrl = url
	}

	void reinitializeGraph() {
		if (graphUrl) {
			if (!readOnly) {
				graphWriteConnectionLocks[graphUrl].release()
			}
			initializeGraph(graphUrl, readOnly)
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
		if (transactionInProgress) {
			println "Transaction already in progress!!"
			return
		}
		if (!(g instanceof TransactionalGraph)) {
			println "Cannot begin transaction on non-transactional graph! Continuing without transactions..."
			return
		}
		if (!numMutations) {
			numMutations = numMutationsBeforeCommit
		}
		g.setMaxBufferSize(numMutations)
		transactionInProgress = true

		println "managed transaction begun..."
	}

	void concludeManagedTransaction() {
		if (transactionInProgress) {
			try {
				g.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
			} catch (Exception e) {
				interruptManagedTransaction()
				g.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
			}
			g.setMaxBufferSize(0)
			transactionInProgress = false
			println "managed transaction concluded."
		} else {
			println "no managed transaction in progress!"
		}
	}

	void interruptManagedTransaction() {
		if (transactionInProgress) {
			println "transaction interrupted. resuming..."
			g.stopTransaction(TransactionalGraph.Conclusion.FAILURE)
			//g.startTransaction()
		} else {
			println "Cannot interrupt managed transaction: No transaction in progress!"
		}
	}

	void flushTransactionBuffer() {
		if (transactionInProgress) {
			g.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
		} else {
			println "Cannot flush transaction buffer: no transaction in progress!"
		}
	}

	boolean isTransactionInProgress() {
		return transactionInProgress
	}

	int getTransactionBufferSize_current() {
		return g.getCurrentBufferSize()
	}

	int getTransactionBufferSize_max() {
		return numMutationsBeforeCommit
	}

	void declareIntent(GraphInterface.MutationIntent intent) {
		//implementation will be provider-specific?
	}

	void clear() {

		boolean activeTransaction = false

		//TODO: this transaction code may not be necessary depending on the behavior
		//of clear() wrt transaction buffer -- check via testcase
		if (transactionInProgress) {
			//transaction in progress -- kill it
			concludeManagedTransaction()
			activeTransaction = true
		}

		g.clear()

		//reinstate vertex and edge indices
		try {
			g.createAutomaticIndex(Index.VERTICES, Vertex.class, null)
			g.createAutomaticIndex(Index.EDGES, Edge.class, null)
		} catch (Exception e) {
			println "warning: encountered Exception: ${e.toString()}; continuing..."
		}

		//resume the transaction if one was in progress prior to call to clear()
		if (activeTransaction) {
			beginManagedTransaction()
		}
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

	List getNeighbors(Vertex v) {
		return getNeighbors(v, null, null)
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

	Vertex getVertex(long id) {
		//stub. provider-specific implementation should be provided
		return null
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
		def edges = []
		if (edgeLabel) {
			if (v1.id.equals(v2.id)) {
				v1.outE(edgeLabel).inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			} else {
				v1.outE(edgeLabel).inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
				v1.inE(edgeLabel).outV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			}
		} else {
			if (v1.id.equals(v2.id)) {
				v1.outE.inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			} else {
				v1.outE.inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
				v1.inE.outV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges) >> -1
			}
		}

		if (edges) {
			return edges[0]
		}
		return null
	}

	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel) {
		return g.addEdge(null, v1, v2, edgeLabel)
	}

	void setElementProperty(Element elem, String property, Object value) {
		elem.setProperty(property, value)
	}

	Object getElementProperty(Element elem, String property) {
		return elem.getProperty(property)
	}

}
