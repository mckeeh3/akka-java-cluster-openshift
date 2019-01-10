package cluster.sharding;

import akka.Done;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.cluster.Cluster;
import akka.cluster.Member;
import akka.cluster.sharding.ClusterSharding;
import akka.cluster.sharding.ClusterShardingSettings;
import akka.management.AkkaManagement;
import akka.management.cluster.bootstrap.ClusterBootstrap;

import java.util.concurrent.CompletableFuture;

public class Runner {
    public static void main(String[] args) {
        startupClusterNode();
    }

    private static void startupClusterNode() {
        ActorSystem actorSystem = ActorSystem.create("akka-cluster-openshift");

        startClusterBootstrap(actorSystem);

        actorSystem.log().info("Started actor system '{}', member {}", actorSystem, actorSystem.provider().getDefaultAddress());
        actorSystem.log().info("akka.discovery.kubernetes-api.pod-label-selector '{}'",
                actorSystem.settings().config().getString("akka.discovery.kubernetes-api.pod-label-selector"));

        actorSystem.actorOf(ClusterListenerActor.props(), "clusterListener");
        ActorRef httpServer = actorSystem.actorOf(HttpServerActor.props(), "httpServer");
        ActorRef shardingRegion = setupClusterSharding(actorSystem, httpServer);

        actorSystem.actorOf(EntityCommandActor.props(shardingRegion), "entityCommand");
        actorSystem.actorOf(EntityQueryActor.props(shardingRegion), "entityQuery");

        addCoordinatedShutdownTask(actorSystem, CoordinatedShutdown.PhaseClusterShutdown());

        registerMemberEvents(actorSystem);
    }

    private static void startClusterBootstrap(ActorSystem actorSystem) {
        AkkaManagement.get(actorSystem).start();
        ClusterBootstrap.get(actorSystem).start();
    }

    private static void registerMemberEvents(ActorSystem actorSystem) {
        Cluster cluster = Cluster.get(actorSystem);
        cluster.registerOnMemberUp(() -> memberUo(actorSystem, cluster.selfMember()));
        cluster.registerOnMemberRemoved(() -> memberRemoved(actorSystem, cluster.selfMember()));
    }

    private static void memberUo(ActorSystem actorSystem, Member member) {
        actorSystem.log().info("Member up {}", member);
    }

    private static void memberRemoved(ActorSystem actorSystem, Member member) {
        actorSystem.log().info("Member removed {}", member);
    }

    private static ActorRef setupClusterSharding(ActorSystem actorSystem, ActorRef httpServer) {
        ClusterShardingSettings settings = ClusterShardingSettings.create(actorSystem);
        return ClusterSharding.get(actorSystem).start(
                "entity",
                EntityActor.props(httpServer),
                settings,
                EntityMessage.messageExtractor()
        );
    }

    private static void addCoordinatedShutdownTask(ActorSystem actorSystem, String coordindateShutdownPhase) {
        CoordinatedShutdown.get(actorSystem).addTask(
                coordindateShutdownPhase,
                coordindateShutdownPhase,
                () -> {
                    actorSystem.log().warning("Coordinated shutdown phase {}", coordindateShutdownPhase);
                    return CompletableFuture.completedFuture(Done.getInstance());
                });
    }
}
