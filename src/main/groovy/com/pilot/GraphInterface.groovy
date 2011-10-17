package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import com.tinkerpop.gremlin.*


/** Captures all the graph operations provided by Pilot
 */
public interface GraphInterface {

	public static enum GraphProvider {
		TINKERGRAPH,
		ORIENTDB,
		NEO4J; //can add others in future
	}

	public static enum MutationIntent {
		STANDARDTRANSACTION,
			BATCHINSERT,
			NONTRANSACTION;
	}

	void initializeGraph(String url, boolean readOnly);
	void reinitializeGraph(); //internal
	void shutdown();
	Graph getGraph();
	String getGraphUrl();
	void beginManagedTransaction();
	void beginManagedTransaction(int numMutations);
	void beginManagedTransaction(MutationIntent transactionType);
	void concludeManagedTransaction();
	void interruptManagedTransaction();
	void flushTransactionBuffer();
	boolean isTransactionInProgress(); //internal
	int getTransactionBufferSize_current(); //internal
	int getTransactionBufferSize_max(); //internal
	void clear();
	List getAllVertices(String idProp);
	List getAllEdges(String idProp);
	long getVertexCount();
	long getEdgeCount();
	List getNeighbors(Vertex v, String idProp, String alongEdge);
	List getNeighbors(Vertex v);
	Vertex getVertex(String property, String value);
	Vertex getVertex(long id);
	Vertex addVertex();
	void removeVertex(Vertex vv);
	List<Edge> getEdges(Vertex v1, Vertex v2, String edgeLabel);
	List<Edge> getOutgoingEdges(Vertex vv, String edgeLabel);
	List<Edge> getIncomingEdges(Vertex vv, String edgeLabel);
	Edge getEdge(long id);
	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel);
	void removeEdge(Edge ee);
	void setElementProperty(Element elem, String property, Object value);
	Object getElementProperty(Element elem, String property);
	
}
