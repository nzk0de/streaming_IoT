# Streaming Helm Chart

## Prerequisites

You need to have a working kubernetes cluster (minikube would work)
For minikube give it enough resources when starting:
minikube start --cpus=12 --memory=20000
You need to have kubectl and helm binaries available

We need to build the images beforehand
For minikube

eval $(minikube docker-env)
docker build -t flink-app-java -f flink-app/Dockerfile .
docker build -t mqtt-bridge -f mqtt-bridge/Dockerfile ./mqtt-bridge/
docker build -t kafka-connect-simple -f dockers/kafka-connect-simple/Dockerfile ./kafka-connect-simple/

Need to install once the Strimzi and Flink operators

```
kubectl create namespace kafka
kubectl create -f 'https://strimzi.io/install/latest?namespace=kafka' -n kafka
kubectl create -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml
```

Make sure cert-manager is running

```
helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.12.1
helm install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator -n kafka
```

export AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY
helm install streaming-poc ./k8s-helm/ \
--set aws.accessKeyId="${AWS_ACCESS_KEY}" \
--set aws.secretAccessKey="${AWS_SECRET_ACCESS_KEY}" \
-n kafka

kubectl port-forward svc/streaming-poc-mosquitto 1884:1883 --address 0.0.0.0 &
kubectl port-forward svc/streaming-poc-kafka-ui 9080:8080 --address 0.0.0.0 &

ssh -L 9080:localhost:9080 evo_test_server


export MQTT_BROKER=localhost
export MQTT_PORT=1884
python sensor_mqtt_simulator.py
