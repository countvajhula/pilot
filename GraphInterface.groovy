package com.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import com.tinkerpop.blueprints.pgm.util.TransactionalGraphHelper.CommitManager
import com.tinkerpop.gremlin.*


/** Captures all the graph operations provided by Pilot
*/
public interface GraphInterface {

	public static enum GraphProvider {
		ORIENTDB; //can add others in future, like neo4j
	}

	public static enum MutationIntent {
		MASSIVEINSERT; //can add other optimizations in future
	}

	public static int DEFAULT_MUTATIONS_BEFORE_COMMIT = 1000
	public static int MASSIVEINSERT_MUTATIONS_BEFORE_COMMIT = 10000

	void initializeGraph(String url, GraphProvider graphProvider, boolean readOnly);
	void reinitializeGraph();
	void shutdown();
	Graph getGraph();
	String getGraphUrl();
	void beginManagedTransaction();
	void beginManagedTransaction(int numMutations);
	void concludeManagedTransaction();
	CommitManager getCommitManager();
	void declareIntent(MutationIntent intent);
	void clear();
	List getVertices(String idProp);
	List getEdges(String idProp);
	long getVertexCount();
	long getEdgeCount();
	List getNeighbors(Vertex v, String idProp, String alongEdge);
	Vertex getVertex(String property, String value);
	Vertex addVertex();
	Edge getEdge(Vertex v1, Vertex v2, String edgeLabel);
	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel);
	void removeVertex(Vertex vv);
	void removeEdge(Edge ee);
	void setElementProperty(Element elem, String property, Object value);
	Object getElementProperty(Element elem, String property);
	
}
