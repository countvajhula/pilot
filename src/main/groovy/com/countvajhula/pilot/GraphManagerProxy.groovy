package com.countvajhula.pilot

import java.lang.reflect.*
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import java.util.concurrent.Semaphore
import java.io.StringWriter
import java.io.PrintWriter
import GraphInterface.GraphProvider


/** Transparently manages transactions and multithreading by intercepting
 * all calls to the graph.
 */
public class GraphManagerProxy implements java.lang.reflect.InvocationHandler {
	private Object proxiedObj
	private boolean profilingEnabled
	private Map<String, Long> functionProfile
	private String profileName
	private static Map graphWriteLocks = [:]
	private static Map managerProxyForGraphOperator = [:]
	// to support direct (unproxied) method invocation
	private Method handle_isTransactionInProgress
	private Method handle_getTransactionBufferSize_current
	private Method handle_getTransactionBufferSize_max
	private Method handle_getGraphUrl
	private Method handle_interruptManagedTransaction

	private static int MS_IN_NS = 1000000
	
	/** Initializes a graph
	 * @param url the location of the graph
	 * @param provider the graph provider to use (Tinkergraph, Neo4j, and OrientDB are currently supported)
	 */
	public static Object initializeGraph(String url, GraphProvider provider) throws Exception {
		return initializeGraph(url, provider, false)
	}

	/** Initializes a graph
	 * @param url the location of the graph
	 * @param provider the graph provider to use (Tinkergraph, Neo4j, and OrientDB are currently supported)
	 * @param readOnly true if this handle will only be used to read from and perform traversals on the graph (optimizes performance)
	 */
	public static Object initializeGraph(String url, GraphProvider provider, boolean readOnly) throws Exception {
		return initializeGraph(url, provider, readOnly, false)
	}

	/** Initializes a graph
	 * @param url the location of the graph
	 * @param provider the graph provider to use (Tinkergraph, Neo4j, and OrientDB are currently supported)
	 * @param readOnly true if this handle will only be used to read from and perform traversals on the graph (optimizes performance)
	 * @param upgradeIfNecessary upgrades the underlying graph database if it was created with an older version of the library
	 */
	public static Object initializeGraph(String url, GraphProvider provider, boolean readOnly, boolean upgradeIfNecessary) throws Exception {
		Object proxiedObj

		switch (provider) {
			case GraphProvider.TINKERGRAPH:
				proxiedObj = new TinkerGraphOperator(url, readOnly, upgradeIfNecessary)
				break
			case GraphProvider.ORIENTDB:
				proxiedObj = new OrientDbOperator(url, readOnly, upgradeIfNecessary)
				break
			case GraphProvider.NEO4J:
				proxiedObj = new Neo4jOperator(url, readOnly, upgradeIfNecessary)
				break
			default:
				throw new Exception("Graph provider invalid or not supported!")
		}

		GraphManagerProxy managerProxy = new GraphManagerProxy(proxiedObj)
		managerProxyForGraphOperator[proxiedObj] = managerProxy

		return java.lang.reflect.Proxy.newProxyInstance(
				proxiedObj.getClass().getClassLoader(),
				proxiedObj.getClass().getInterfaces(),
				managerProxy)
	}

	private GraphManagerProxy(Object proxiedObj) {
		this.proxiedObj = proxiedObj;
		profilingEnabled = false
		handle_isTransactionInProgress = proxiedObj.getClass().getMethod("isTransactionInProgress")
		handle_getTransactionBufferSize_current = proxiedObj.getClass().getMethod("getTransactionBufferSize_current")
		handle_getTransactionBufferSize_max = proxiedObj.getClass().getMethod("getTransactionBufferSize_max")
		handle_getGraphUrl = proxiedObj.getClass().getMethod("getGraphUrl")
		handle_interruptManagedTransaction = proxiedObj.getClass().getMethod("interruptManagedTransaction")
	}

	//shouldn't have to ever call this
	public static GraphManagerProxy getManagerProxyForOperator(GraphDbOperator operator) {
		return managerProxyForGraphOperator[operator]
	}

	public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {

		Object result
		long startTime, finishTime

		try {
			if (profilingEnabled) {
				startTime = System.nanoTime()
			}

			//transaction commit manager
			switch (m.getName()) {
				case "addVertex":
				case "addEdge":
				case "removeVertex":
				case "removeEdge":
				case "setElementProperty":
					if (handle_isTransactionInProgress.invoke(proxiedObj)) {
						if((handle_getTransactionBufferSize_current.invoke(proxiedObj)-1) % handle_getTransactionBufferSize_max.invoke(proxiedObj) == 0) {
							//println "committing mutations to graph..."
						}
					}

					break
				case "beginManagedTransaction":
					//graph write locking
					String graphUrl = handle_getGraphUrl.invoke(proxiedObj)
					Semaphore graphWriteLock
					synchronized (this) {
						graphWriteLock = graphWriteLocks[graphUrl]
						if (!graphWriteLock) {
							graphWriteLock = new Semaphore(1, true)
							graphWriteLocks[graphUrl] = graphWriteLock
						}
					}
					graphWriteLock.acquire()
					println "[ThreadId:${Thread.currentThread().getId()}] acquired graph write semaphore! [${graphUrl}]"
					break
			}
			
			//## invoke the proxied function ##//
			result = m.invoke(proxiedObj, args)

		} catch (InvocationTargetException e) {
			println "encountered Exception: ${e.toString()}"
			//for debugging:
			StringWriter sw = new StringWriter()
			PrintWriter pw = new PrintWriter(sw)
			e.printStackTrace(pw)
			println "encountered Exception: ${sw.toString()}"

			if (handle_isTransactionInProgress.invoke(proxiedObj)) {
				handle_interruptManagedTransaction.invoke(proxiedObj)
			}

			throw e.getTargetException()

		} catch (Exception e) {
			println "encountered Exception: ${e.toString()}"
			//for debugging:
			StringWriter sw = new StringWriter()
			PrintWriter pw = new PrintWriter(sw)
			e.printStackTrace(pw)
			println "encountered Exception: ${sw.toString()}"

			if (handle_isTransactionInProgress.invoke(proxiedObj)) {
				handle_interruptManagedTransaction.invoke(proxiedObj)
			}

			throw new RuntimeException("unexpected invocation exception: " +
					e.getMessage())

		} finally {

			if (profilingEnabled) {
				finishTime = System.nanoTime()
				if (functionProfile[m.getName()]) {
					functionProfile[m.getName()] += (finishTime - startTime)
				} else {
					functionProfile[m.getName()] = (finishTime - startTime)
				}
			}

			switch (m.getName()) {
				case "concludeManagedTransaction":
					String graphUrl = handle_getGraphUrl.invoke(proxiedObj)
					try {
						Semaphore graphWriteLock = graphWriteLocks[graphUrl]
						graphWriteLock.release()
						println "[ThreadId:${Thread.currentThread().getId()}] released graph write semaphore! [${graphUrl}]"
					} catch (Exception e) {
						println "[ThreadId:${Thread.currentThread().getId()}] Unexpected exception in trying to release graph write semaphore! [${graphUrl}]"
					}
					break
			}

		}
		return result
	}

	/** Starts the profiler, which measures the time and proportion of time spent
	 * in different operations, a full report of which is displayed when the profiler
	 * is stopped.
	 * @param operator the graph operator instance to profile
	 * @param profileName the name of the profile - displayed in the final report
	 */
	public static void startProfiler(GraphInterface operator, String profileName) {
		GraphManagerProxy managerProxy = managerProxyForGraphOperator[operator]
		managerProxy.startProfiler_(profileName)
	}

	/** Starts the profiler, which measures the time and proportion of time spent
	 * in different operations, a full report of which is displayed when the profiler
	 * is stopped.
	 * @param operator the graph operator instance to profile
	 */
	public static void startProfiler(GraphInterface operator) {
		GraphManagerProxy.startProfiler(operator, null)
	}

	private void startProfiler_(String profileName) {
		profilingEnabled = true
		this.profileName = profileName
		if (!functionProfile) {
			functionProfile = new HashMap()
		}
		//Class cls = Class.forName("method1");
		Class cls = proxiedObj.getClass()
		def methodList = cls.getDeclaredMethods()
		for (method in methodList) {
			functionProfile[method.getName()] = 0
		}
	}

	/** Stops the profiler and displays a report with a breakdown of time spent and proportion of
	 * time spent in each operation during the profiling period.
	 */
	public static String stopProfiler(GraphInterface operator) {
		GraphManagerProxy managerProxy = managerProxyForGraphOperator[operator]
		return managerProxy.stopProfiler()
	}

	private String stopProfiler() {
		String results
		if (profileName) {
			results = "\n------PROFILER RESULTS (${profileName}) ------\n"
		} else {
			results = "\n------PROFILER RESULTS------\n"
		}

		long totalDuration = functionProfile.values().sum()
		double totalDuration_ms = (double)totalDuration / MS_IN_NS
		def sortedProfile = functionProfile.sort {a, b -> b.value <=> a.value}
		results += "Total Duration = ${totalDuration_ms} ms\n"
		for (fn in sortedProfile.keySet()) {
			long time_ns = functionProfile[fn]
			double percent_time = ((double)time_ns / totalDuration) * 100
			double time_ms = (double)time_ns / MS_IN_NS
			results += ("${fn}:\t" + "${time_ms} ms\t" + "${percent_time} %\n")
			functionProfile[fn] = 0
		}
		if (profileName) {
			results += "\n------xxxxxxxxxxxxxxxx (${profileName}) ------\n"
		} else {
			results += "\n------xxxxxxxxxxxxxxxx------\n"
		}
		profilingEnabled = false
		return results
	}

}
