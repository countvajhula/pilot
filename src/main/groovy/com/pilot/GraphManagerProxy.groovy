package com.pilot

import java.lang.reflect.*
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import java.util.concurrent.Semaphore
import java.io.StringWriter
import java.io.PrintWriter


/** Transparently manages transactions and multithreading by intercepting
 * all calls to the graph.
 */
public class GraphManagerProxy implements java.lang.reflect.InvocationHandler {
	private Object obj
	private boolean profilingEnabled
	private Map<String, Long> functionProfile
	private String profileName
	private static Map graphWriteLocks = [:]
	private static Map managerProxyForGraphOperator = [:]

	private static int MS_IN_NS = 1000000
	
	public static Object newInstance(String url, GraphInterface.GraphProvider provider, boolean readOnly) throws Exception {
		Object obj
		switch (provider) {
			case GraphInterface.GraphProvider.ORIENTDB:
				obj = new OrientDbOperator(url, readOnly)
				break
			case GraphInterface.GraphProvider.NEO4J:
				obj = new Neo4jOperator(url, readOnly)
				break
			default:
				throw new Exception("Graph provider invalid or not supported!")
		}

		GraphManagerProxy managerProxy = new GraphManagerProxy(obj)
		managerProxyForGraphOperator[obj] = managerProxy

		return java.lang.reflect.Proxy.newProxyInstance(
				obj.getClass().getClassLoader(),
				obj.getClass().getInterfaces(),
				managerProxy)
	}

	private GraphManagerProxy(Object obj) {
		this.obj = obj;
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

			switch (m.getName()) {
				case "clear":
				case "getVertexCount":
				case "getEdgeCount":
				case "getVertex":
				case "addVertex":
				case "removeVertex":
				case "removeEdge":
					if (!proxy.getGraph()) { //TODO: don't think this block is needed anymore
						proxy.reinitializeGraph()
					}
					break
			}
			//transaction commit manager
			switch (m.getName()) {
				case "addVertex":
				case "addEdge":
				case "removeVertex":
				case "removeEdge":
				case "setElementProperty":
					if (proxy.isTransactionInProgress()) {
						if((proxy.getTransactionBufferSize_current()-1) % proxy.getTransactionBufferSize_max() == 0) {
							println "committing mutations to graph..."
						}
					}

					break
				case "beginManagedTransaction":
					//graph write locking
					String graphUrl = proxy.getGraphUrl()
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
			result = m.invoke(obj, args)

		} catch (InvocationTargetException e) {
			println "encountered Exception: ${e.toString()}"
			//for debugging:
			StringWriter sw = new StringWriter()
			PrintWriter pw = new PrintWriter(sw)
			e.printStackTrace(pw)
			println "encountered Exception: ${sw.toString()}"

			if (proxy.isTransactionInProgress()) {
				proxy.interruptManagedTransaction()
			}

			throw e.getTargetException()

		} catch (Exception e) {
			println "encountered Exception: ${e.toString()}"
			//for debugging:
			StringWriter sw = new StringWriter()
			PrintWriter pw = new PrintWriter(sw)
			e.printStackTrace(pw)
			println "encountered Exception: ${sw.toString()}"

			if (proxy.isTransactionInProgress()) {
				proxy.interruptManagedTransaction()
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
					String graphUrl = proxy.getGraphUrl()
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

	public static void startProfiler(GraphInterface operator, String profileName) {
		GraphManagerProxy managerProxy = managerProxyForGraphOperator[operator]
		managerProxy.startProfiler_(profileName)
	}

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
		Class cls = obj.getClass()
		def methodList = cls.getDeclaredMethods()
		for (method in methodList) {
			functionProfile[method.getName()] = 0
		}
	}

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
