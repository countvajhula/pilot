package com.countvajhula.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.gremlin.groovy.*
import java.util.concurrent.Semaphore
import GraphInterface.MutationIntent


/** Provides a default implementation of the graph operations provided by Pilot in GraphInterface.groovy. Provider-specific classes may extend or override this implementation.
 */
class GraphDbOperator implements GraphInterface {

	protected Graph g
	protected String graphUrl
	protected boolean transactionInProgress
	protected MutationIntent mutationIntent
	protected static EnumMap<MutationIntent, Integer> MutationsBeforeCommit
	protected static Map graphWriteConnectionLocks = [:] //can be private?
	protected boolean readOnly //can be private?

	static {
		// load gremlin for graph traversals
		Gremlin.load()

		//define # mutations before commit for each transaction mode
		MutationsBeforeCommit = new EnumMap(MutationIntent.class)
		MutationsBeforeCommit[MutationIntent.STANDARDTRANSACTION] = 1000
		MutationsBeforeCommit[MutationIntent.BATCHINSERT] = 1
		MutationsBeforeCommit[MutationIntent.NONTRANSACTION] = 1
	}

	//TODO:
	//FOAF, with degree as input
	//BFS, DFS (iterators)
	//change all returned results to iterators, + add convenience list functions

	//TODO: remove readOnly flag in future when pessimistic locking is supported by underlying db
	public GraphDbOperator(String url, boolean readOnly) {
		synchronized (graphWriteConnectionLocks) {
			if (!graphWriteConnectionLocks[url]) {
				graphWriteConnectionLocks[url] = new Semaphore(1, true)
			}
		}
		this.readOnly = readOnly
		if (!readOnly) {
			try {
				graphWriteConnectionLocks[url].acquire()
			} catch (Exception e) {
				throw new RuntimeException("Error in acquiring semaphore! url:${url}")
			}
			println "-ACQUIRED- GRAPH WRITE CONNECTION [${url}]"
		}
		mutationIntent = MutationIntent.STANDARDTRANSACTION
		transactionInProgress = false
		graphUrl = url
	}

	// {@inheritDoc} -- doesn't work in groovydoc :(
	// see {@link com.countvajhula.pilot.GraphInterface#shutdown shutdown() }
	void shutdown() {
		if (g) {
			if (!readOnly) {
				graphWriteConnectionLocks[graphUrl].release()
				println "-RELEASED- GRAPH WRITE CONNECTION [${graphUrl}]"
			}
			// individual graph implementations MUST shutdown the db and set g = null
		}
	}

	Graph getGraph() {
		return g
	}

	String getGraphUrl() {
		return graphUrl
	}

	void beginManagedTransaction() {
		if (!transactionInProgress) {
			mutationIntent = MutationIntent.STANDARDTRANSACTION
		}
		beginManagedTransaction(MutationsBeforeCommit[mutationIntent])
	}

	void beginManagedTransaction(int numMutations) {
		if (transactionInProgress) {
			//TODO: better to throw a custom exception and handle it in GraphManagerProxy
			println "Transaction already in progress!!"
			return
		}
		if (!(g instanceof TransactionalGraph)) {
			println "Cannot begin transaction on non-transactional graph! Continuing without transactions..."
			return
		}
		if (mutationIntent == MutationIntent.NONTRANSACTION) {
			mutationIntent = MutationIntent.STANDARDTRANSACTION
		}
		if (!numMutations) {
			numMutations = MutationsBeforeCommit[mutationIntent]
		}
		g.setMaxBufferSize(numMutations)
		transactionInProgress = true

		println "managed transaction begun..."
	}

	void beginManagedTransaction(MutationIntent transactionType) {
		//implementation will be provider-specific
		if (!transactionInProgress) {
			mutationIntent = transactionType
		}
		beginManagedTransaction(MutationsBeforeCommit[mutationIntent])
	}

	void concludeManagedTransaction() {
		if (transactionInProgress) {
			try {
				g.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
			} catch (Exception e) {
				interruptManagedTransaction()
				g.stopTransaction(TransactionalGraph.Conclusion.SUCCESS)
			}
			mutationIntent = MutationIntent.NONTRANSACTION
			g.setMaxBufferSize(MutationsBeforeCommit[MutationIntent.NONTRANSACTION])
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
		return g.getMaxBufferSize()
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

	List getAllVertices(String idProp) {
		List vertices = []
		if (idProp) {
			g.V[idProp].aggregate(vertices).iterate()
		} else {
			g.V.aggregate(vertices).iterate()
		}

		return vertices
	}

	List<Edge> getAllEdges(String idProp) {
		List edges = []
		if (idProp) {
			g.E[idProp].aggregate(edges).iterate()
		} else {
			g.E.aggregate(edges).iterate()
		}

		return edges
	}

	long getVertexCount() {
		return g.V.count()
	}

	long getEdgeCount() {
		return g.E.count()
	}

	//TODO: return an iterator over neighbors. current behavior can be captured
	//in a separate function getNeighborList, which calls aggregate on the returned pipe
	List getNeighbors(Vertex vv, String idProp, String alongEdge) {
		//idProp is the property of the result vertices that will be returned
		//if null, then the vertices themselves will be returned
		List neighbors = []
		List tempList = []
		if (idProp) {
			if (alongEdge) {
				vv.bothE(alongEdge).bothV.filter { node -> !node.id.equals(vv.id) }[idProp].aggregate(neighbors).iterate()
				//add current node back if there is a self-connecting edge
				vv.outE(alongEdge).inV.filter { node -> node.id.equals(vv.id) }[idProp].aggregate(tempList).iterate()
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			} else {
				vv.bothE.bothV.filter { node -> !node.id.equals(vv.id) }[idProp].aggregate(neighbors).iterate()
				//add current node back if there is a self-connecting edge
				vv.outE.inV.filter { node -> node.id.equals(vv.id) }[idProp].aggregate(tempList).iterate()
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			}
		} else {
			if (alongEdge) {
				vv.bothE(alongEdge).bothV.filter { node -> !node.id.equals(vv.id) }.aggregate(neighbors).iterate()
				//add current node back if there is a self-connecting edge
				vv.outE(alongEdge).inV.filter { node -> node.id.equals(vv.id) }.aggregate(tempList).iterate()
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			} else {
				vv.bothE.bothV.filter { node -> !node.id.equals(vv.id) }.aggregate(neighbors).iterate()
				//add current node back if there is a self-connecting edge
				vv.outE.inV.filter { node -> node.id.equals(vv.id) }.aggregate(tempList).iterate()
				if (tempList.size() > 0) {
					neighbors += tempList
				}
			}
		}

		return neighbors
	}

	List getNeighbors(Vertex vv) {
		return getNeighbors(vv, null, null)
	}

	Iterable<Vertex> getVertex(String property, Object value) {
		AutomaticIndex vertexIndex = g.getIndex(Index.VERTICES, Vertex.class)
		Iterable<Vertex> itr = vertexIndex.get(property, value)

		return itr
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

	//TODO: return an iterator instead, and don't aggregate results
	List<Edge> getEdges(Vertex v1, Vertex v2, String edgeLabel) {
		def edges = []
		if (edgeLabel) {
			if (v1.id.equals(v2.id)) {
				v1.outE(edgeLabel).inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
			} else {
				v1.outE(edgeLabel).inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
				v1.inE(edgeLabel).outV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
			}
		} else {
			if (v1.id.equals(v2.id)) {
				v1.outE.inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
			} else {
				v1.outE.inV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
				v1.inE.outV.filter{it.id.equals(v2.id)}.back(2).aggregate(edges).iterate()
			}
		}

		return edges
	}

	List<Edge> getOutgoingEdges(Vertex vv, String edgeLabel) {
		def edges = []
		if (edgeLabel) {
			vv.outE(edgeLabel).aggregate(edges).iterate()
		} else {
			vv.outE.aggregate(edges).iterate()
		}

		return edges
	}

	List<Edge> getIncomingEdges(Vertex vv, String edgeLabel) {
		def edges = []
		if (edgeLabel) {
			vv.inE(edgeLabel).aggregate(edges).iterate()
		} else {
			vv.inE.aggregate(edges).iterate()
		}

		return edges
	}

	Edge getEdge(long id) {
		//stub. provider-specific implementation should be provided
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
