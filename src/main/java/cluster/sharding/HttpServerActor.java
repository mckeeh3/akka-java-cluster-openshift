package cluster.sharding;

import akka.NotUsed;
import akka.actor.AbstractLoggingActor;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.cluster.Cluster;
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
    private final Cluster cluster = Cluster.get(actorSystem);
    private final Tree tree = new Tree("cluster", "cluster");

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(EntityMessage.Action.class, this::entityMessage)
                .build();
    }

    private void entityMessage(EntityMessage.Action action) {
        if (action.action.equals("start")) {
            tree.add(action.member, action.shardId, action.entityId);
        } else if (action.action.equals("stop")) {
            tree.remove(action.entityId + "", "entity");
        }
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
        line(html, "  <head>");
        line(html, "    <meta http-equiv='Content-Type' content='text/html;charset=utf-8'>");
        line(html, "    <style type='text/css'>");
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
        line(html, "  height: 640px;");
        line(html, "  margin: 80px 160px 80px 160px;");
        line(html, "  overflow: hidden;");
        line(html, "  position: relative;");
        line(html, "  width: 960px;");
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
        line(html, "  </head>");
        line(html, "  <body>");
        line(html, "    <h2>");
        line(html, "      Akka OpenShift cluster<br>");
        line(html, "      force-directed graph");
        line(html, "    </h2>");
        line(html, "    <script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.js'></script>");
        line(html, "    <script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.geom.js'></script>");
        line(html, "    <script type='text/javascript' src='http://mbostock.github.io/d3/talk/20111116/d3/d3.layout.js'></script>");
        line(html, "    <script type='text/javascript'>");
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
        line(html, "var w = 1280,");
        line(html, "    h = 800,");
        line(html, "    node,");
        line(html, "    link,");
        line(html, "    root;");
        line(html, "");
        line(html, "var force = d3.layout.force()");
        line(html, "    .on('tick', tick)");
        line(html, "    .charge(function(d) {");
        line(html, "        return d._children ? -d.size / 100 : -300;");
        line(html, "    })");
        line(html, "    .linkDistance(function(d) {");
        line(html, "        return d.target._children ? 80 : 50;");
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
        line(html, "    if (!node.id) node.id = ++i;");
        line(html, "    nodes.push(node);");
        line(html, "    return node.size;");
        line(html, "  }");
        line(html, "");
        line(html, "  root.size = recurse(root);");
        line(html, "  return nodes;");
        line(html, "}");
        line(html, "");
        line(html, "function root() {");
        line(html, "    return {");
        line(html, "        'name' : 'cluster',");
        line(html, "        'type' : 'cluster',");
        line(html, "        'children' : [");
        line(html, "            {");
        line(html, "                'name' : 'member1',");
        line(html, "                'type' : 'member',");
        line(html, "                'children' : [");
        line(html, "                    {");
        line(html, "                        'name' : 'shard1',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                    {");
        line(html, "                        'name' : 'shard2',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                ]");
        line(html, "            },");
        line(html, "            {");
        line(html, "                'name' : 'member1',");
        line(html, "                'type' : 'member',");
        line(html, "                'children' : [");
        line(html, "                    {");
        line(html, "                        'name' : 'shard1',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                    {");
        line(html, "                        'name' : 'shard2',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                ]");
        line(html, "            },");
        line(html, "            {");
        line(html, "                'name' : 'member1',");
        line(html, "                'type' : 'member',");
        line(html, "                'children' : [");
        line(html, "                    {");
        line(html, "                        'name' : 'shard1',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                    {");
        line(html, "                        'name' : 'shard2',");
        line(html, "                        'type' : 'shard',");
        line(html, "                        'children' : [");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 },");
        line(html, "                            { 'name' : 'entity1', 'type' : 'entity', 'size' : 1000 }");
        line(html, "                        ]");
        line(html, "                    },");
        line(html, "                ]");
        line(html, "            },");
        line(html, "        ]");
        line(html, "    }");
        line(html, "}");
        line(html, "    </script>");
        line(html, "  </body>");
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
        //return TextMessage.create(tree.toJson());
        return TextMessage.create(testTree().toJson());
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

        void add(String memberId, int shardId, int entityId) {
            add(memberId, shardId + "", entityId + "");
        }

        void add(String memberId, String shardId, String entityId) {
            Tree member = find(memberId, "member");
            if (member == null) {
                member = Tree.create(memberId, "member");
                children.add(member);
            }
            Tree shard = find(shardId, "shard");
            if (shard == null) {
                shard = Tree.create(shardId, "shard");
                member.children.add(shard);
            }
            Tree entity = find(entityId, "entity");
            if (entity == null) {
                entity = Tree.create(entityId, "entity");
                shard.children.add(entity);
            }
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

        Tree remove(String name, String type) {
            for (Tree child : children) {
                if (child.name.equals(name) && child.type.equals(type)) {
                    children.remove(child);
                    return child;
                } else {
                    Tree found = child.remove(name, type);
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

    private static Tree testTree() {
        return Tree.create("cluster", "cluster")
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
    }
}
