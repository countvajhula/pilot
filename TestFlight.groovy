#!/usr/bin/env groovy

import com.pilot.*
import com.pilot.GraphInterface.GraphProvider
import com.pilot.GraphInterface.MutationIntent
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*

/** A demo app for the Pilot graph operator https://github.com/countvajhula/pilot
 * To run: change file permissions to make it executable:
 * 		$ chmod +x TestFlight.groovy
 * Then execute:
 * 		$ ./TestFlight.groovy
 * */
public class TestFlight {

	// configure the graph database provider and location here
	public static final GraphProvider graphProvider = GraphProvider.TINKERGRAPH // NEO4J, ORIENTDB
	public static final String graphUrl = "./my-" + graphProvider.toString() + "-graphdb"

	public static void main(String[] args) {

		println "Welcome to TestFlight -- a demo app for the Pilot graph operator.\nPerforming graph manipulations (2000 iterations)..."

		// initialize the graph
		GraphInterface g = GraphManagerProxy.initializeGraph(graphUrl, graphProvider, false)

		// start the built-in application profiler to visualize performance
		GraphManagerProxy.startProfiler(g, "2000 arbitrary graph operations")

		// transactions can be either standard managed transactions, or "batch" transactions
		// if you want to optimize performance for a large insertion
		g.beginManagedTransaction() //g.beginManagedTransaction(MutationIntent.BATCHINSERT)

		// do some arbitrary graph mutations
		for (int i=1; i<=2000; i++) {
			Vertex v1 = g.addVertex()
			g.setElementProperty(v1, 'name', 'Sid')
			Vertex v2 = g.addVertex()
			g.setElementProperty(v2, 'name', 'Dan')
			Edge ee = g.addEdge(v1, v2, 'friends')
			g.setElementProperty(ee, 'since', 2007)
			if (i%500 == 0) {
				println "${i} iterations completed..."
			}
		}

		// ends the managed transaction
		g.concludeManagedTransaction()

		// displays profiler results for this entire operation
		println GraphManagerProxy.stopProfiler(g)

		// don't forget to shutdown the graph
		g.shutdown()
	}
}
