package com.countvajhula.pilot

import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import com.tinkerpop.gremlin.groovy.*


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

	/** Shuts down the graph instance -- this should always be called at the end of your interaction with the graph */
	void shutdown();

	/** Returns underlying blueprints graph */
	Graph getGraph();

	/** Returns the URL of the graph */
	String getGraphUrl();

	/** Begins a STANDARD managed transaction using a default transaction buffer size */
	void beginManagedTransaction();

	/** Begins a managed transaction and uses the specified number of
	 * mutations as the size of the transaction buffer
	 * @param numMutations size of the transaction buffer in number of mutations to hold before committing to graph
	 */
	void beginManagedTransaction(int numMutations);

	/** Begins a managed transaction of the specified type (currently either
	 * STANDARD or BATCH)
	 * @param transactionType the type of the transaction. Use STANDARDTRANSACTION for general use, BATCHINSERT for a large operation
	 */
	void beginManagedTransaction(MutationIntent transactionType);

	/** Concludes a managed transaction, commits all pending changes in the buffer to the graph */
	void concludeManagedTransaction();

	/** Called internally when an error in committing the transaction is encountered. Marks the current operations
	 * as failed and attempts to continue. */
	void interruptManagedTransaction();

	/** Commits all in-buffer operations to the graph immediately (instead of waiting for it to be automatically
	 * committed when the buffer fills up */
	void flushTransactionBuffer();

	/** Returns true if a managed transaction is in progress (used internally) */
	boolean isTransactionInProgress(); //internal

	/** Returns current size of transaction buffer used (used internally) */
	int getTransactionBufferSize_current(); //internal

	/** Returns size of the full transaction buffer */
	int getTransactionBufferSize_max(); //internal

	/** Clears the graph of all vertices and edges */
	void clear();

	/** Returns a full list of vertices in the graph
	 * @param idProp optional - return only as a list of values of this property, instead of the vertex objects themselves
	 */
	List getAllVertices(String idProp);

	/** Returns a full list of all edges in the graph
	 * @param idProp optional - return only as a list of values of this property, instead of the vertex objects themselves
	 */
	List getAllEdges(String idProp);

	/** Returns the number of vertices in the graph */
	long getVertexCount();

	/** Returns the number of edges in the graph */
	long getEdgeCount();

	/** Returns a list of vertices that are immediate neighbors of the provided vertex. The list by default
	 * will contain the Blueprints Vertex objects corresponding to the neighbor vertices. If a property name
	 * is specified, then the list will contain instead the values of that property on those vertices. If
	 * neighbors along only a certain class of edge are to be returned as opposed to neighbors along all edges,
	 * then that may be specified as well.
	 * @param vv the vertex whose neighbors are to be returned
	 * @param idProp the property on the neighbors to be returned (null if vertex objects themselves are desired
	 * @param alongEdge the label of the edges on which to return neighbors
	 */
	List getNeighbors(Vertex vv, String idProp, String alongEdge);

	/** Returns a list of vertices that are immediate neighbors of the provided vertex.
	 * @param vv the vertex
	*/
	List getNeighbors(Vertex vv);

	/** Given a property name and a value for that property, returns an iterable on matching vertices in the graph
	 * @param property the name of the property
	 * @param value value of that property to match
	*/
	Iterable<Vertex> getVertex(String property, Object value);

	/** Returns the vertex with the given id
	 * @param id id of the vertex
	*/
	Vertex getVertex(long id);

	/** Adds a vertex to the graph */
	Vertex addVertex();

	/** Removes a vertex from the graph
	 * @param vv the vertex to remove
	*/
	void removeVertex(Vertex vv);

	/** Returns a list of edges between two vertices
	 * @param v1 a vertex
	 * @param v2 another vertex
	 * @param edgeLabel optional - return only edges having this label
	 */
	List<Edge> getEdges(Vertex v1, Vertex v2, String edgeLabel);

	/** Returns a list of outgoing edges for a given vertex
	 * @param vv the vertex
	 * @param edgeLabel optional - return only those outgoing edges having this label
	 */
	List<Edge> getOutgoingEdges(Vertex vv, String edgeLabel);

	/** Returns a list of incoming edges for a given vertex
	 * @param vv the vertex
	 * @param edgeLabel optional - return only those incoming edges having this label
	 */
	List<Edge> getIncomingEdges(Vertex vv, String edgeLabel);

	/** Returns the edge with the given id
	 * @param id id of the edge
	 */
	Edge getEdge(long id);

	/** Adds an edge between two vertices
	 * @param v1 a vertex
	 * @param v2 another vertex
	 * @param edgeLabel the label on the edge
	 */
	Edge addEdge(Vertex v1, Vertex v2, String edgeLabel);

	/** Removes a given edge from the graph
	 * @param ee the edge to remove
	 */
	void removeEdge(Edge ee);

	/** Sets a property on an element (vertex or edge)
	 * @param elem the vertex or edge
	 * @param property the name of the property
	 * @param value the value to set the property to
	 */
	void setElementProperty(Element elem, String property, Object value);

	/** Returns a property on an element (vertex or edge)
	 * @param elem the vertex or edge
	 * @param property the name of the property whose value is to be returned
	 */
	Object getElementProperty(Element elem, String property);
	
}
