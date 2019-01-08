package cluster.sharding;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocket;
import akka.japi.JavaPartialFunction;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class HttpServerActor extends AbstractLoggingActor {
    private ActorSystem actorSystem = context().system();
    private ActorMaterializer actorMaterializer = ActorMaterializer.create(actorSystem);

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .build();
    }

    @Override
    public void preStart() {
        log().info("Start");
        startHttpServer();
    }

    private void startHttpServer() {
        int serverPort = 8080;

        try {
            CompletionStage<ServerBinding> serverBindingCompletionStage = Http.get(actorSystem)
                    .bindAndHandleSync(this::handleHttpRequest, ConnectHttp.toHost(InetAddress.getLocalHost().getHostName(), serverPort), actorMaterializer);

            serverBindingCompletionStage.toCompletableFuture().get(15, TimeUnit.SECONDS);
        } catch (UnknownHostException e) {
            log().error(e, "Unable to access hostname");
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            log().error(e, "Monitor HTTP server error");
        } finally {
            log().info("HTTP server started on port {}", serverPort);
        }
    }

    private HttpResponse handleHttpRequest(HttpRequest httpRequest) {
        switch (httpRequest.getUri().path()) {
            case "/":
                return getWebPage();
            case "/monitor":
                return getWebPage();
            case "/events":
                return webSocketHandler(httpRequest);
            default:
                return HttpResponse.create().withStatus(404);
        }
    }

    private HttpResponse getWebPage() {
        return HttpResponse.create()
                .withEntity(ContentTypes.TEXT_HTML_UTF8, monitorWebPage())
                .withStatus(StatusCodes.ACCEPTED);
    }

    private String monitorWebPage() {
        StringBuilder html = new StringBuilder();
        line(html, "<!DOCTYPE html>");
        line(html, "<html>");
        line(html, "  <head>");
        line(html, "    <script src=\"https://d3js.org/d3.v4.min.js\"></script>\n");
        line(html, "    <script>");
        line(html, "      var webSocket = new WebSocket('ws://' + location.host + '/events');");
        line(html, "");
        line(html, "      webSocket.onopen = function(event) {");
        line(html, "        webSocket.send('request')");
        line(html, "        console.log('WebSocket connected', event)");
        line(html, "      }");
        line(html, "");
        line(html, "      webSocket.onmessage = function(event) {");
        line(html, "        console.log(event);");
        //line(html, "        webSocket.send('request')");
        line(html, "      }");
        line(html, "");
        line(html, "      webSocket.onerror = function(error) {");
        line(html, "        console.error('WebSocket error', error);");
        line(html, "      }");
        line(html, "");
        line(html, "      webSocket.onclose = function(event) {");
        line(html, "        console.log('WebSocket close', event);");
        line(html, "      }");
        line(html, "");
        line(html, "      var canvas = document.querySelector(\"canvas\"),");
        line(html, "          context = canvas.getContext(\"2d\"),");
        line(html, "          width = canvas.width,");
        line(html, "          height = canvas.height;");
        line(html, "");
        line(html, "    </script>");
        line(html, "  </head>");
        line(html, "  <body>");
        line(html, "    <h3>Hello, World!</h3>");
        line(html, "    <canvas width=\"960\" height=\"960\"></canvas>");
        line(html, "  </body>");
        line(html, "</html>");
        line(html, "");

        return html.toString();
    }

    private static void line(StringBuilder html, String line) {
        html.append(String.format("%s%n", line));
    }

    private HttpResponse webSocketHandler(HttpRequest httpRequest) {
        Flow<Message, Message, NotUsed> flow = Flow.<Message>create()
                .collect(new JavaPartialFunction<Message, Message>() {
                    @Override
                    public Message apply(Message message, boolean isCheck) {
                        if (isCheck && message.isText()) {
                            return null;
                        } else if (isCheck && !message.isText()) {
                            throw noMatch();
                        } else if (message.asTextMessage().isStrict()) {
                            return TextMessage.create(generateRandomEventMessage());
                        } else {
                            return TextMessage.create("");
                        }
                    }
                });

        return WebSocket.handleWebSocketRequestWith(httpRequest, flow);
    }

    private String generateRandomEventMessage() {
        return testTree();
    }

    @Override
    public void postStop() {
        log().info("Stop");
    }

    static Props props() {
        return Props.create(HttpServerActor.class);
    }

    public static class Tree implements Serializable {
        public final String name;
        public final String type;
        public final List<Tree> children = new ArrayList<>();

        public Tree(String name, String type) {
            this.name = name;
            this.type = type;
        }

        static Tree create(String name, String type) {
            return new Tree(name, type);
        }

        Tree children(Tree... children) {
            this.children.addAll(Arrays.asList(children));
            return this;
        }

        @Override
        public String toString() {
            return String.format("%s[%s, %s]", getClass().getSimpleName(), name, type);
        }
    }

    private static String testTree() {
        Tree root = Tree.create("cluster", "cluster")
                .children(
                        Tree.create("node1", "node")
                                .children(
                                        Tree.create("shard01", "shard")
                                                .children(
                                                        Tree.create("entity01", "entity"),
                                                        Tree.create("entity02", "entity"),
                                                        Tree.create("entity03", "entity")
                                                ),
                                        Tree.create("shard02", "shard")
                                                .children(
                                                        Tree.create("entity04", "entity"),
                                                        Tree.create("entity05", "entity"),
                                                        Tree.create("entity06", "entity")
                                                ),
                                        Tree.create("shard03", "shard")
                                                .children(
                                                        Tree.create("entity07", "entity"),
                                                        Tree.create("entity08", "entity"),
                                                        Tree.create("entity09", "entity")
                                                )
                                ),
                        Tree.create("node2", "node")
                                .children(
                                        Tree.create("shard04", "shard")
                                                .children(
                                                        Tree.create("entity10", "entity"),
                                                        Tree.create("entity11", "entity"),
                                                        Tree.create("entity12", "entity")
                                                ),
                                        Tree.create("shard05", "shard")
                                                .children(
                                                        Tree.create("entity13", "entity"),
                                                        Tree.create("entity14", "entity"),
                                                        Tree.create("entity15", "entity")
                                                ),
                                        Tree.create("shard06", "shard")
                                                .children(
                                                        Tree.create("entity16", "entity"),
                                                        Tree.create("entity17", "entity"),
                                                        Tree.create("entity18", "entity")
                                                )
                                ),
                        Tree.create("node3", "node")
                                .children(
                                        Tree.create("shard07", "shard")
                                                .children(
                                                        Tree.create("entity19", "entity"),
                                                        Tree.create("entity20", "entity"),
                                                        Tree.create("entity21", "entity")
                                                ),
                                        Tree.create("shard08", "shard")
                                                .children(
                                                        Tree.create("entity22", "entity"),
                                                        Tree.create("entity23", "entity"),
                                                        Tree.create("entity24", "entity")
                                                ),
                                        Tree.create("shard09", "shard")
                                                .children(
                                                        Tree.create("entity25", "entity"),
                                                        Tree.create("entity26", "entity"),
                                                        Tree.create("entity27", "entity")
                                                )
                                ),
                        Tree.create("node4", "node")
                                .children(
                                        Tree.create("shard10", "shard")
                                                .children(
                                                        Tree.create("entity28", "entity"),
                                                        Tree.create("entity29", "entity"),
                                                        Tree.create("entity30", "entity")
                                                ),
                                        Tree.create("shard11", "shard")
                                                .children(
                                                        Tree.create("entity31", "entity"),
                                                        Tree.create("entity32", "entity"),
                                                        Tree.create("entity33", "entity")
                                                ),
                                        Tree.create("shard12", "shard")
                                                .children(
                                                        Tree.create("entity34", "entity"),
                                                        Tree.create("entity35", "entity"),
                                                        Tree.create("entity36", "entity")
                                                )
                                )
                );

        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        try {
            return ow.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            return String.format("{ \"error\" : \"%s\" }", e.getMessage());
        }
    }
}
