package com.github.avbox.osc.haikuo

import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

interface HaikuoDialogHandler {
    fun show(params: Map<String, Any?>): Map<String, Any?>
}

fun haikuoDialogDefault(params: Map<String, Any?>): Map<String, Any?> {
    val type = HaikuoJson.str(params["type"])
    val result = LinkedHashMap<String, Any?>()
    when (type) {
        "confirm" -> result["result"] = false
        "input" -> result["result"] = null
        "select" -> result["result"] = null
        else -> result["result"] = true
    }
    return result
}

class HaikuoDialogQueue(private val handler: HaikuoDialogHandler?) {

    private class Request(
        val params: Map<String, Any?>,
        val latch: CountDownLatch = CountDownLatch(1)
    ) {
        var result: Map<String, Any?> = emptyMap()
    }

    private val queue = LinkedBlockingQueue<Request>()

    @Volatile
    private var closed = false

    private val pumpThread: Thread = Thread(
        {
            while (!closed) {
                val request = try {
                    queue.poll(200, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    null
                } ?: continue
                val outcome = try {
                    val current = handler
                    if (current == null) {
                        haikuoDialogDefault(request.params)
                    } else {
                        current.show(request.params)
                    }
                } catch (e: Exception) {
                    haikuoDialogDefault(request.params)
                }
                request.result = outcome
                request.latch.countDown()
            }
        },
        "haikuo-dialog-pump"
    )

    init {
        pumpThread.isDaemon = true
        pumpThread.start()
    }

    fun show(params: Map<String, Any?>): Map<String, Any?> {
        if (closed) return haikuoDialogDefault(params)
        val request = Request(params)
        queue.put(request)
        if (closed) {
            queue.remove(request)
            if (request.latch.count == 0L) return request.result
            return haikuoDialogDefault(params)
        }
        request.latch.await()
        return request.result
    }

    fun close() {
        if (closed) return
        closed = true
        val pending = ArrayList<Request>()
        queue.drainTo(pending)
        for (request in pending) {
            request.result = haikuoDialogDefault(request.params)
            request.latch.countDown()
        }
        pumpThread.interrupt()
    }
}
