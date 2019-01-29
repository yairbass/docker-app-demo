#!/usr/bin/env bash

CERT=`cat ./ca.crt`

kubectl get nodes |grep demo |cut -f1 -d\  | while read node;do
  echo $node
  gcloud compute ssh --zone "us-central1-a" $node --command \
        " sudo su -c ' mkdir -p /etc/docker/certs.d/docker.artifactory.jfrog.com' root &&
          echo '$CERT' > /tmp/ca.crt &&
          sudo su -c ' mv /tmp/ca.crt /etc/docker/certs.d/docker.artifactory.jfrog.com/' root"  </dev/null;
done