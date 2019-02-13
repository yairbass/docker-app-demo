CERT=`cat docker.crt`
    echo $1
    echo $2
    echo $CERT

kubectl get node | cut -f1 -d\  | while read node;do
    echo $node
    gcloud compute ssh --zone "$1" $node --command sudo su -c ' mkdir -p /etc/docker/certs.d/docker.artifactory.$2.jfrog.com' root
done