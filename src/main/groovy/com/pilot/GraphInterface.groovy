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
		BATCHINSERT; //can add other optimizations in future
	}

	public static int DEFAULT_MUTATIONS_BEFORE_COMMIT = 1000
	public static int BATCHINSERT_MUTATIONS_BEFORE_COMMIT = 10000

	void initializeGraph(String url, boolean readOnly);
	void reinitializeGraph(); //internal
	void shutdown();
	Graph getGraph();
	String getGraphUrl();
	void beginManagedTransaction();
	void beginManagedTransaction(int numMutations);
	void concludeManagedTransaction();
	void interruptManagedTransaction();
	void flushTransactionBuffer();
	boolean isTransactionInProgress(); //internal
	int getTransactionBufferSize_current(); //internal
	int getTransactionBufferSize_max(); //internal
	void declareIntent(MutationIntent intent);
	void clear();
	List getVertices(String idProp);
	List getEdges(String idProp);
	long getVertexCount();
	long getEdgeCount();
	List getNeighbors(Vertex v, String idProp, String alongEdge);
	List getNeighbors(Vertex v);
	Vertex getVertex(String property, String value);
	Vertex getVertex(long id);
	Vertex addVertex();
	Edge getEdge(Vertex v1, Vertex v2, String edgeLabel);
	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel);
	void removeVertex(Vertex vv);
	void removeEdge(Edge ee);
	void setElementProperty(Element elem, String property, Object value);
	Object getElementProperty(Element elem, String property);
	
}
