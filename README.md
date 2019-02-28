# akka-java-cluster-openshift
An Akka Java cluster OpenShift demo application 


This is an amazing way to visualize Akka Cluster behavior, and demonstrate core reactive systems principles! 

If you are not inclined to spin up OKD, Openshift or the like, here are a few additional steps you need to use your trusty minikube instance: 

Create the target namespace (akka-cluster-1): 

echo the file below to the custer, or create a file called  add-akka-cluster-1.json containing:

{
  "kind": "Namespace",
  "apiVersion": "v1",
  "metadata": {
    "name": "akka-cluster-1",
    "labels": {
      "name": "akka-cluster-1"
    }
  }
}

create the namespace: 

kubectl create -f add-akka-cluster-1.json

By default ingress is not turned on in minikube so you need to enable ingress and use the node_port as its the only supported mode of ingress for minikube:

enable ingress:

minikube addons enable ingress

set the namespace:

kubectl config set-context $(kubectl config current-context) --namespace=akka-cluster-1

verify the deployment name:

kubectl get deployment

NAME                READY   UP-TO-DATE   AVAILABLE   AGE
akka-cluster-demo   3/3     3            3           12m

expose the deployment, create a service: 

expose deployment/akka-cluster-demo --type=NodePort --port 8080

export a NODE_PORT evironment variable: 

export NODE_PORT=$(kubectl get services/akka-cluster-demo -o go-template='{{(index .spec.ports 0).nodePort}}')

Get the minikube ip:

minikube ip

192.168.99.100

echo $NODE_PORT

32219

hit your browser with the combined:

http://192.168.99.100:32219
