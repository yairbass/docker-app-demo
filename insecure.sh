#!/usr/bin/env bash

CONFIG="DOCKER_OPTS='-p /var/run/docker.pid --iptables=false --ip-masq=false --log-level=warn --bip=169.254.123.1/24 --registry-mirror=https://mirror.gcr.io --log-driver=json-file --log-opt=max-size=10m --log-opt=max-file=5 --insecure-registry 35.184.64.135:80'"

    kubectl get nodes |grep gke-standard-cluster|cut -f1 -d\  | while read node;do
      gcloud compute ssh --zone "us-central1-a" $node --command \
       "echo $CONFIG > /tmp/docker-config ; \
       sudo su -c 'cp /tmp/docker-config /etc/default/docker' root \
       sudo su -c 'sudo systemctl restart docker'
        " </dev/null;
    done