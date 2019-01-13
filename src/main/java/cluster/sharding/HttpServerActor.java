package cluster.sharding;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import akka.http.javadsl.ConnectHttp;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.model.ws.WebSocket;
import akka.japi.JavaPartialFunction;
import akka.stream.ActorMaterializer;
import akka.stream.javadsl.Flow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.*;
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
    private final Cluster cluster = Cluster.get(actorSystem);
    private final Tree tree = new Tree("cluster", "cluster");

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityMessage.Action.class, this::entityMessage)
                .build();
    }

    private void entityMessage(EntityMessage.Action action) {
        log().info("{} <-- {}", action, sender());
        if (action.action.equals("start")) {
            tree.add(action.member, action.shardId, action.entityId);
        } else if (action.action.equals("stop")) {
            tree.remove(action.member, action.shardId, action.entityId);
        }
        forwardActionMessage(action);
    }

    private void forwardActionMessage(EntityMessage.Action action) {
        if (action.forward) {
            cluster.state().getMembers().forEach(member -> {
                if (!cluster.selfMember().equals(member) && member.status().equals(MemberStatus.up())) {
                    forwardActionMessage(action.asNoForward(), member);
                }
            });
        }
    }

    private void forwardActionMessage(EntityMessage.Action action, Member member) {
        String path = member.address().toString() + self().path().toStringWithoutAddress();
        ActorSelection actorSelection = context().actorSelection(path);
        log().debug("{} -> {}", action, actorSelection);
        actorSelection.tell(action, self());
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
        log().info("HTTP request '{}", httpRequest.getUri().path());
        switch (httpRequest.getUri().path()) {
            case "/":
                return getWebPage();
            case "/home":
                return htmlFileResponse("force-collapsible.html");
            case "/d3/d3.js":
                return jsFileResponse("d3/d3.js");
            case "/d3/d3.geom.js":
                return jsFileResponse("d3/d3.geom.js");
            case "/d3/d3.layout.js":
                return jsFileResponse("d3/d3.layout.js");
            case "/events":
                return webSocketHandler(httpRequest);
            default:
                return HttpResponse.create().withStatus(404);
        }
    }

    private HttpResponse htmlFileResponse(String filename) {
        try {
            String fileContents = readFile(filename);
            return HttpResponse.create()
                    .withEntity(ContentTypes.TEXT_HTML_UTF8, fileContents)
                    .withStatus(StatusCodes.ACCEPTED);
        } catch (IOException e) {
            log().error(e, String.format("I/O error on file '%s'", filename));
            return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
        }
    }

    private HttpResponse getWebPage() {
        return HttpResponse.create()
                .withEntity(ContentTypes.TEXT_HTML_UTF8, monitorWebPage())
                .withStatus(StatusCodes.ACCEPTED);
    }

    private HttpResponse jsFileResponse(String filename) {
        try {
            String fileContents = readFile(filename);
            return HttpResponse.create()
                    .withEntity(ContentTypes.create(MediaTypes.APPLICATION_JAVASCRIPT, HttpCharsets.UTF_8), fileContents)
                    .withStatus(StatusCodes.ACCEPTED);
        } catch (IOException e) {
            log().error(e, String.format("I/O error on file '%s'", filename));
            return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
        }
    }

    private String readFile(String filename) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename);
        if (inputStream == null) {
            throw new FileNotFoundException(String.format("Filename '%s'", filename));
        } else {
            StringBuilder fileContents = new StringBuilder();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = br.readLine()) != null) {
                    fileContents.append(String.format("%s%n", line));
                }
            }
            return fileContents.toString();
        }
    }

    /**
     * To change this web page edit the file src/test/resources/force-collapsible.html.
     * Then run the test class JsToJava.
     * Take the output from JsToJava and paste into this method.
     *
     * @return the monitor HTML page
     */
    private String monitorWebPage() {
        StringBuilder html = new StringBuilder();

        line(html, "<!DOCTYPE html>");
        line(html, "<html>");
        line(html, "<head>");
        line(html, "  <meta http-equiv='Content-Type' content='text/html;charset=utf-8'>");
        line(html, "  <style type='text/css'>");
        line(html, "");
        line(html, "circle.node {");
        line(html, "  cursor: pointer;");
        line(html, "  stroke: #000;");
        line(html, "  stroke-width: .5px;");
        line(html, "}");
        line(html, "");
        line(html, "line.link {");
        line(html, "  fill: none;");
        line(html, "  stroke: #9ecae1;");
        line(html, "  stroke-width: 1.5px;");
        line(html, "}");
        line(html, "");
        line(html, "body {");
        line(html, "  font: 300 36px 'Helvetica Neue';");
        line(html, "  height: 900px;");
        line(html, "  margin: 80px 160px 80px 160px;");
        line(html, "  overflow: hidden;");
        line(html, "  position: relative;");
        line(html, "  width: 1400px;");
        line(html, "}");
        line(html, "");
        line(html, "a:link, a:visited {");
        line(html, "  color: #777;");
        line(html, "  text-decoration: none;");
        line(html, "}");
        line(html, "");
        line(html, "a:hover {");
        line(html, "  color: #666;");
        line(html, "}");
        line(html, "");
        line(html, "blockquote {");
        line(html, "  margin: 0;");
        line(html, "}");
        line(html, "");
        line(html, "blockquote:before {");
        line(html, "  content: '“';");
        line(html, "  position: absolute;");
        line(html, "  left: -.4em;");
        line(html, "}");
        line(html, "");
        line(html, "blockquote:after {");
        line(html, "  content: '”';");
        line(html, "  position: absolute;");
        line(html, "}");
        line(html, "");
        line(html, "body > ul {");
        line(html, "  margin: 0;");
        line(html, "  padding: 0;");
        line(html, "}");
        line(html, "");
        line(html, "h1 {");
        line(html, "  font-size: 64px;");
        line(html, "}");
        line(html, "");
        line(html, "h1, h2, h3 {");
        line(html, "  font-weight: inherit;");
        line(html, "  margin: 0;");
        line(html, "}");
        line(html, "");
        line(html, "h2, h3 {");
        line(html, "  text-align: right;");
        line(html, "  font-size: inherit;");
        line(html, "  position: absolute;");
        line(html, "  bottom: 0;");
        line(html, "  right: 0;");
        line(html, "}");
        line(html, "");
        line(html, "h2 {");
        line(html, "  font-size: 24px;");
        line(html, "  position: absolute;");
        line(html, "}");
        line(html, "");
        line(html, "h3 {");
        line(html, "  bottom: -20px;");
        line(html, "  font-size: 18px;");
        line(html, "}");
        line(html, "");
        line(html, ".invert {");
        line(html, "  background: #1f1f1f;");
        line(html, "  color: #dcdccc;");
        line(html, "}");
        line(html, "");
        line(html, ".invert h2, .invert h3 {");
        line(html, "  color: #7f9f7f;");
        line(html, "}");
        line(html, "");
        line(html, ".string, .regexp {");
        line(html, "  color: #f39;");
        line(html, "}");
        line(html, "");
        line(html, ".keyword {");
        line(html, "  color: #00c;");
        line(html, "}");
        line(html, "");
        line(html, ".comment {");
        line(html, "  color: #777;");
        line(html, "  font-style: oblique;");
        line(html, "}");
        line(html, "");
        line(html, ".number {");
        line(html, "  color: #369;");
        line(html, "}");
        line(html, "");
        line(html, ".class, .special {");
        line(html, "  color: #1181B8;");
        line(html, "}");
        line(html, "");
        line(html, "body > svg {");
        line(html, "  position: absolute;");
        line(html, "  top: -80px;");
        line(html, "  left: -160px;");
        line(html, "}");
        line(html, "");
        line(html, "    </style>");
        line(html, "</head>");
        line(html, "<body>");
        line(html, "<h2>");
        line(html, "  Akka OpenShift cluster<br>");
        line(html, "  force-directed graph");
        line(html, "</h2>");
        line(html, "<script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.js'></script>");
        line(html, "<script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.geom.js'></script>");
        line(html, "<script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.layout.js'></script>");
        line(html, "<script type='text/javascript'>");
        line(html, "");
        line(html, "var webSocket = new WebSocket('ws://' + location.host + '/events');");
        line(html, "");
        line(html, "webSocket.onopen = function(event) {");
        line(html, "  webSocket.send('request')");
        line(html, "  console.log('WebSocket connected', event)");
        line(html, "}");
        line(html, "");
        line(html, "webSocket.onmessage = function(event) {");
        line(html, "  console.log(event);");
        line(html, "  root = JSON.parse(event.data);");
        line(html, "  update();");
        line(html, "}");
        line(html, "");
        line(html, "webSocket.onerror = function(error) {");
        line(html, "  console.error('WebSocket error', error);");
        line(html, "}");
        line(html, "");
        line(html, "webSocket.onclose = function(event) {");
        line(html, "  console.log('WebSocket close', event);");
        line(html, "}");
        line(html, "");
        line(html, "setInterval(sendWebSocketRequest, 15000);");
        line(html, "function sendWebSocketRequest() {");
        line(html, "    webSocket.send('request');");
        line(html, "}");
        line(html, "");
        line(html, "var w = 1600,");
        line(html, "    h = 1200,");
        line(html, "    node,");
        line(html, "    link,");
        line(html, "    root;");
        line(html, "");
        line(html, "var force = d3.layout.force()");
        line(html, "    .on('tick', tick)");
        line(html, "    .charge(function(d) {");
        line(html, "        return d._children ? -d.size / 100 : -600;");
        line(html, "    })");
        line(html, "    .linkDistance(function(d) {");
        line(html, "        return d.target._children ? 100 : 75;");
        line(html, "    })");
        line(html, "    .size([w, h - 100]);");
        line(html, "");
        line(html, "var vis = d3.select('body').append('svg:svg')");
        line(html, "    .attr('width', w)");
        line(html, "    .attr('height', h);");
        line(html, "");
        line(html, "root = root();");
        line(html, "root.fixed = true;");
        line(html, "root.x = w / 2;");
        line(html, "root.y = h / 2 - 80;");
        line(html, "update();");
        line(html, "");
        line(html, "function update() {");
        line(html, "  var nodes = flatten(root),");
        line(html, "      links = d3.layout.tree().links(nodes);");
        line(html, "");
        line(html, "  force");
        line(html, "      .nodes(nodes)");
        line(html, "      .links(links)");
        line(html, "      .start();");
        line(html, "");
        line(html, "  link = vis.selectAll('line.link')");
        line(html, "      .data(links, function(d) { return d.target.id; });");
        line(html, "");
        line(html, "  link.enter().insert('svg:line', '.node')");
        line(html, "      .attr('class', 'link')");
        line(html, "      .attr('x1', function(d) { return d.source.x; })");
        line(html, "      .attr('y1', function(d) { return d.source.y; })");
        line(html, "      .attr('x2', function(d) { return d.target.x; })");
        line(html, "      .attr('y2', function(d) { return d.target.y; });");
        line(html, "");
        line(html, "  link.exit().remove();");
        line(html, "");
        line(html, "  node = vis.selectAll('circle.node')");
        line(html, "      .data(nodes, function(d) { return d.id; })");
        line(html, "      .style('fill', color);");
        line(html, "");
        line(html, "  node.transition()");
        line(html, "      .attr('r', radius);");
        line(html, "");
        line(html, "  node.enter().append('svg:circle')");
        line(html, "      .attr('class', function(d) { return d.type ? 'node ' + d.type : 'node'; })");
        line(html, "      .attr('cx', function(d) { return d.x; })");
        line(html, "      .attr('cy', function(d) { return d.y; })");
        line(html, "      .attr('r', radius)");
        line(html, "      .style('fill', color)");
        line(html, "      .on('click', click)");
        line(html, "      .call(force.drag);");
        line(html, "");
        line(html, "  node.exit().remove();");
        line(html, "}");
        line(html, "");
        line(html, "function tick() {");
        line(html, "  link.attr('x1', function(d) { return d.source.x; })");
        line(html, "      .attr('y1', function(d) { return d.source.y; })");
        line(html, "      .attr('x2', function(d) { return d.target.x; })");
        line(html, "      .attr('y2', function(d) { return d.target.y; });");
        line(html, "");
        line(html, "  node.attr('cx', function(d) { return d.x; })");
        line(html, "      .attr('cy', function(d) { return d.y; });");
        line(html, "}");
        line(html, "");
        line(html, "function color(d) {");
        line(html, "    if (d._children) {");
        line(html, "        return '#3182bd';");
        line(html, "    } else if (d.type == 'cluster') {");
        line(html, "        return '#B30000';");
        line(html, "    } else if (d.type == 'member') {");
        line(html, "        return '#F17D00';");
        line(html, "    } else if (d.type == 'shard') {");
        line(html, "        return '#00C000';");
        line(html, "    } else if (d.type == 'entity') {");
        line(html, "        return '#046E97';");
        line(html, "    } else {");
        line(html, "        return '#fd8d3c';");
        line(html, "    }");
        line(html, "}");
        line(html, "");
        line(html, "function radius(d) {");
        line(html, "    if (d._children) {");
        line(html, "        return Math.sqrt(d.size) / 10;");
        line(html, "    } else if (d.type == 'cluster') {");
        line(html, "        return 6;");
        line(html, "    } else if (d.type == 'member') {");
        line(html, "        return 20;");
        line(html, "    } else if (d.type == 'shard') {");
        line(html, "        return 10;");
        line(html, "    } else if (d.type == 'entity') {");
        line(html, "        return 5;");
        line(html, "    } else {");
        line(html, "        return 4.5;");
        line(html, "    }");
        line(html, "}");
        line(html, "");
        line(html, "function click(d) {");
        line(html, "  if (d.children) {");
        line(html, "    d._children = d.children;");
        line(html, "    d.children = null;");
        line(html, "  } else {");
        line(html, "    d.children = d._children;");
        line(html, "    d._children = null;");
        line(html, "  }");
        line(html, "  update();");
        line(html, "}");
        line(html, "");
        line(html, "function flatten(root) {");
        line(html, "  var nodes = [], i = 0;");
        line(html, "");
        line(html, "  function recurse(node) {");
        line(html, "    if (node.children) node.size = node.children.reduce(function(p, v) { return p + recurse(v); }, 0);");
        line(html, "//    if (!node.id) node.id = ++i;");
        line(html, "    if (!node.id) node.id = node.name;");
        line(html, "    nodes.push(node);");
        line(html, "    return node.size;");
        line(html, "  }");
        line(html, "");
        line(html, "  root.size = recurse(root);");
        line(html, "  return nodes;");
        line(html, "}");
        line(html, "    </script>");
        line(html, "</body>");
        line(html, "</html>");

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
                            return getTreeAsJson();
                        } else {
                            return TextMessage.create("");
                        }
                    }
                });

        return WebSocket.handleWebSocketRequestWith(httpRequest, flow);
    }

    private Message getTreeAsJson() {
        return TextMessage.create(tree.toJson());
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

        void add(String memberId, String shardId, String entityId) {
            removeEntity(entityId);
            Tree member = find(memberId, "member");
            if (member == null) {
                member = Tree.create(memberId, "member");
                children.add(member);
            }
            Tree shard = member.find(shardId, "shard");
            if (shard == null) {
                shard = Tree.create(shardId, "shard");
                member.children.add(shard);
            }
            Tree entity = shard.find(entityId, "entity");
            if (entity == null) {
                entity = Tree.create(entityId, "entity");
                shard.children.add(entity);
            }
        }

        void remove(String memberId, String shardId, String entityId) {
            Tree member = find(memberId, "member");
            if (member != null) {
                Tree shard = member.find(shardId, "shard");
                if (shard != null) {
                    Tree entity = shard.find(entityId, "entity");
                    shard.children.remove(entity);

                    if (shard.children.isEmpty()) {
                        member.children.remove(shard);
                    }
                }
                if (member.children.isEmpty()) {
                    children.remove(member);
                }
            }
        }

        void removeEntity(String entityId) {
            for (Tree member : children) {
                for (Tree shard : member.children) {
                    for (Tree entity : shard.children) {
                        if (entity.name.equals(entityId)) {
                            shard.children.remove(entity);
                            break;
                        }
                    }
                }
            }
        }

        Tree find(String memberId, String shardId, String entityId) {
            Tree member = find(memberId, "member");
            if (member != null) {
                Tree shard = member.find(shardId, "shard");
                if (shard != null) {
                    Tree entity = shard.find(entityId, "entity");
                    if (entity != null) {
                        return entity;
                    }
                }
            }
            return null;
        }

        Tree find(String name, String type) {
            if (this.name.equals(name) && this.type.equals(type)) {
                return this;
            } else {
                for (Tree child : children) {
                    Tree found = child.find(name, type);
                    if (found != null) {
                        return found;
                    }
                }
            }
            return null;
        }

        String toJson() {
            ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
            try {
                return ow.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return String.format("{ \"error\" : \"%s\" }", e.getMessage());
            }
        }

        @Override
        public String toString() {
            return String.format("%s[%s, %s]", getClass().getSimpleName(), name, type);
        }
    }
}
