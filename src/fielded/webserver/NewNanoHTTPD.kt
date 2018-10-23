package fielded.webserver

import org.json.JSONObject
import org.nanohttpd.protocols.http.IHTTPSession
import org.nanohttpd.protocols.http.request.Method
import org.nanohttpd.protocols.http.response.Response
import org.nanohttpd.protocols.http.response.Status.*
import org.nanohttpd.protocols.websockets.CloseCode
import org.nanohttpd.protocols.websockets.NanoWSD
import org.nanohttpd.protocols.websockets.WebSocket
import org.nanohttpd.protocols.websockets.WebSocketFrame
import java.io.*
import java.util.*

// experimenting with new version of nanohttpd (which will serve websockets on the same port)
class NewNanoHTTPD(val port: Int) {

    val openWebsockets = mutableListOf<WebSocket>()
    var handlers = mutableListOf<(String, Method, Map<String, String>, Map<String, List<String>>, Map<String, String>) -> Response?>()
    var messageHandlers = mutableListOf<(WebSocket, String, Any) -> Boolean>()

    fun addDocumentRoot(s: String) {
        handlers.add { uri, _, _, _, _ ->
            val f = File(s + "/" + uri)
            if (f.exists()) {
                serveFile(f)
            } else
                null
        }
    }

    var dynamicRoots = mutableMapOf<String, ()-> String>()

    fun addDynamicRoot(name: String, s: () -> String) {
        dynamicRoots.put(name, s)
    }


    fun serveFile(f: File): Response {
        val inputStream = BufferedInputStream(FileInputStream(f))
        return Response.newFixedLengthResponse(OK, mimeTypeFor(f), inputStream, f.length())
    }

    val knownMimeExtensions = mutableMapOf<String, String>("css" to "text/css",
            "js" to "application/javascript",
            "mov" to "video/quicktime",
            "mp4" to "video/mp4",
            "gif" to "image/gif", "jpg" to "image/jpeg", "png" to "image/png", "html" to "text/html")

    private fun mimeTypeFor(f: File): String? {

        if (f.name.indexOf('.') == -1) return "text/html"

        val suffix = f.name.substring(f.name.lastIndexOf('.') + 1)

        return knownMimeExtensions.getOrDefault(suffix, "text/html")
    }

    val server = object : NanoWSD(port) {
        override fun openWebSocket(p0: IHTTPSession?): WebSocket {

            return object : WebSocket(p0) {
                override fun onOpen() {
                    print("onOpen")
                    openWebsockets.add(this)
                }

                override fun onClose(p0: CloseCode?, p1: String?, p2: Boolean) {
                    print("onClose $p0 $p1 $p2")
                    openWebsockets.remove(this)
                }

                override fun onPong(p0: WebSocketFrame?) {
                    print("pong $p0")
                }

                override fun onMessage(p0: WebSocketFrame?) {
                    print("message $p0")

                    try {
                        val o = JSONObject(p0!!.textPayload)
                        val address = o.getString("address")
                        var payload = o.get("payload")
                        val originalPayload = payload

                        for (v in messageHandlers) {
                            try {
                                if (v(this, address, payload))
                                    return
                            } catch (e: Throwable) {
                                println(" -- exception thrown in message handler code, this is never a good thing --")
                                println(" -- original payload is $payload")
                                e.printStackTrace()
                            }
                        }

                    } catch (e: Throwable) {
                        println(" mallformed message ? $p0")
                        e.printStackTrace()
                    }

                }

                override fun onException(p0: IOException?) {
                    print("exception $p0")
                    p0!!.printStackTrace()
                }

            }
        }

        fun send(message: String) {
            openWebsockets.forEach {
                it.send(message)
            }
        }

        val QUERY_STRING_PARAMETER = "NannoHTTPD.QUERY_STRING_PARAMETER"

        override fun serve(session: IHTTPSession?): Response {

            val files = HashMap<String, String>()
            val method = session!!.getMethod()
            if (Method.PUT == method || Method.POST == method) {
                try {
                    session.parseBody(files)
                } catch (ioe: IOException) {
                    return Response.newFixedLengthResponse(INTERNAL_ERROR, MIME_PLAINTEXT, "SERVER INTERNAL ERROR: IOException: " + ioe.message)
                } catch (re: ResponseException) {
                    return Response.newFixedLengthResponse(re.status, MIME_PLAINTEXT, re.message)
                }

            }

            val parms = session.parameters
            parms.put(QUERY_STRING_PARAMETER, Collections.singletonList(session.getQueryParameterString()))

            val parameters = session.getParameters()

            return if (session.getUri().toString() == "/favicon.ico") Response.newFixedLengthResponse(NOT_FOUND, null, "")
            else serve(session.getUri(), method, session.getHeaders(), parameters, files)

        }


        private fun serve(uri: String, method: Method, headers: Map<String, String>, parms: Map<String, List<String>>, files: Map<String, String>): Response {

            for (h in handlers) {
                val r = h(uri, method, headers, parms, files)
                if (r != null) return r
            }

            return Response.newFixedLengthResponse(NOT_FOUND, null, "Couldn't understand request")
        }

    }

    init {

        server.start()

        handlers.add { uri, _, _, _, _ ->
            dynamicRoots.entries.stream().map { kv ->
                val f = File(kv.value() + "/" + uri)
                if (f.exists()) {
                    serveFile(f)
                } else
                    null
            }.filter { it !=null }.findFirst().orElse(null)
        }

        Thread() {
            while (true) {
                Thread.sleep(2000)
                println("ping")
                openWebsockets.forEach {
                    it.ping(byteArrayOf(0))
                }
            }
        }.start()
    }


}