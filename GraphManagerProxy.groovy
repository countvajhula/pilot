
import java.lang.reflect.*
import com.tinkerpop.blueprints.*
import com.tinkerpop.blueprints.pgm.*
import com.tinkerpop.blueprints.pgm.impls.orientdb.*
import com.tinkerpop.blueprints.pgm.util.TransactionalGraphHelper.CommitManager
import java.util.concurrent.Semaphore


public class GraphManagerProxy implements java.lang.reflect.InvocationHandler {
	private Object obj
	private static Map graphWriteLocks = [:]
	
	public static Object newInstance(Object obj) {
		return java.lang.reflect.Proxy.newProxyInstance(
				obj.getClass().getClassLoader(),
				obj.getClass().getInterfaces(),
				new GraphManagerProxy(obj))
	}

	private GraphManagerProxy(Object obj) {
		this.obj = obj;
	}

	public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
		Object result
		try {
			switch (m.getName()) {
				case "clearGraph":
				case "getVertexCount":
				case "getEdgeCount":
				case "getGraphVertex":
				case "addGraphVertex":
				case "removeGraphVertex":
				case "removeGraphEdge":
					if (!proxy.getGraph()) {
						proxy.reinitializeGraph()
					}
					break
			}
			//transaction commit manager
			switch (m.getName()) {
				case "addGraphVertex":
				case "addGraphEdge":
				case "removeGraphVertex":
				case "removeGraphEdge":
				case "setElementProperty":
					CommitManager cm = proxy.getCommitManager()
					cm.incrCounter()
					if (cm.atCommit()) {
						println "committing mutations to graph..."
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
			throw e.getTargetException()
		} catch (Exception e) {
			throw new RuntimeException("unexpected invocation exception: " +
					e.getMessage())
		} finally {

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

}
