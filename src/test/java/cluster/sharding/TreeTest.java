package cluster.sharding;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Assert;
import org.junit.Test;

public class TreeTest {
    @Test
    public void findExistingEntityInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("entity36", "entity"));
    }

    @Test
    public void findExistingShardInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("shard11", "shard"));
    }

    @Test
    public void findExistingNodeInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("node3", "node"));
    }

    @Test
    public void treeNonExistingNodeNotInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNull(tree.find("x", "node"));
    }

    @Test
    public void treeIsValidJson() throws JsonProcessingException {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = ow.writeValueAsString(testTree());

        Assert.assertNotNull(json);
    }

    @Test
    public void removeExistingEntityInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("entity20", "entity"));
        Assert.assertNotNull(tree.remove("entity20", "entity"));
        Assert.assertNull(tree.find("entity20", "entity"));
    }

    @Test
    public void removeExistingShardInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("shard05", "shard"));
        Assert.assertNotNull(tree.remove("shard05", "shard"));
        Assert.assertNull(tree.find("shard05", "shard"));
        Assert.assertNull(tree.find("entity14", "entity"));
    }

    @Test
    public void removeExistingNodeInTree() {
        HttpServerActor.Tree tree = testTree();

        Assert.assertNotNull(tree.find("node3", "node"));
        Assert.assertNotNull(tree.remove("node3", "node"));
        Assert.assertNull(tree.find("node3", "node"));
        Assert.assertNull(tree.find("shard08", "shard"));
        Assert.assertNull(tree.find("entity26", "entity"));
    }

    @Test
    public void addToEmptyTree() {
        HttpServerActor.Tree tree = new HttpServerActor.Tree("cluster", "cluster");

        Assert.assertNull(tree.find("member1", "member"));
        Assert.assertNull(tree.find("shard01", "shard"));
        Assert.assertNull(tree.find("entity01", "entity"));

        tree.add("member1", "shard01", "entity01");

        Assert.assertNotNull(tree.find("member1", "member"));
        Assert.assertNotNull(tree.find("shard01", "shard"));
        Assert.assertNotNull(tree.find("entity01", "entity"));

        tree.add("member1", "shard01", "entity01");

        Assert.assertNotNull(tree.find("member1", "member"));
        Assert.assertNotNull(tree.find("shard01", "shard"));
        Assert.assertNotNull(tree.find("entity01", "entity"));

        tree.add("member1", "shard01", "entity02");

        Assert.assertNotNull(tree.find("member1", "member"));
        Assert.assertNotNull(tree.find("shard01", "shard"));
        Assert.assertNotNull(tree.find("entity02", "entity"));

        tree.add("member2", "shard04", "entity12");

        Assert.assertNotNull(tree.find("member2", "member"));
        Assert.assertNotNull(tree.find("shard04", "shard"));
        Assert.assertNotNull(tree.find("entity12", "entity"));
    }

    @Test
    public void toJson() {
        String json = testTree().toJson();
        Assert.assertNotNull(json);
        System.out.println(json);
    }

    private static HttpServerActor.Tree testTree() {
        return HttpServerActor.Tree.create("cluster", "cluster")
                .children(
                        HttpServerActor.Tree.create("node1", "node")
                                .children(
                                        HttpServerActor.Tree.create("shard01", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity01", "entity"),
                                                        HttpServerActor.Tree.create("entity02", "entity"),
                                                        HttpServerActor.Tree.create("entity03", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard02", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity04", "entity"),
                                                        HttpServerActor.Tree.create("entity05", "entity"),
                                                        HttpServerActor.Tree.create("entity06", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard03", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity07", "entity"),
                                                        HttpServerActor.Tree.create("entity08", "entity"),
                                                        HttpServerActor.Tree.create("entity09", "entity")
                                                )
                                ),
                        HttpServerActor.Tree.create("node2", "node")
                                .children(
                                        HttpServerActor.Tree.create("shard04", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity10", "entity"),
                                                        HttpServerActor.Tree.create("entity11", "entity"),
                                                        HttpServerActor.Tree.create("entity12", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard05", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity13", "entity"),
                                                        HttpServerActor.Tree.create("entity14", "entity"),
                                                        HttpServerActor.Tree.create("entity15", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard06", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity16", "entity"),
                                                        HttpServerActor.Tree.create("entity17", "entity"),
                                                        HttpServerActor.Tree.create("entity18", "entity")
                                                )
                                ),
                        HttpServerActor.Tree.create("node3", "node")
                                .children(
                                        HttpServerActor.Tree.create("shard07", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity19", "entity"),
                                                        HttpServerActor.Tree.create("entity20", "entity"),
                                                        HttpServerActor.Tree.create("entity21", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard08", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity22", "entity"),
                                                        HttpServerActor.Tree.create("entity23", "entity"),
                                                        HttpServerActor.Tree.create("entity24", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard09", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity25", "entity"),
                                                        HttpServerActor.Tree.create("entity26", "entity"),
                                                        HttpServerActor.Tree.create("entity27", "entity")
                                                )
                                ),
                        HttpServerActor.Tree.create("node4", "node")
                                .children(
                                        HttpServerActor.Tree.create("shard10", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity28", "entity"),
                                                        HttpServerActor.Tree.create("entity29", "entity"),
                                                        HttpServerActor.Tree.create("entity30", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard11", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity31", "entity"),
                                                        HttpServerActor.Tree.create("entity32", "entity"),
                                                        HttpServerActor.Tree.create("entity33", "entity")
                                                ),
                                        HttpServerActor.Tree.create("shard12", "shard")
                                                .children(
                                                        HttpServerActor.Tree.create("entity34", "entity"),
                                                        HttpServerActor.Tree.create("entity35", "entity"),
                                                        HttpServerActor.Tree.create("entity36", "entity")
                                                )
                                )
                );

    }
}
