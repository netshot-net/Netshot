# Netshot Kubernetes Deployment V0.1
Deploy Custom Netshot Image to Kubernetes

## How to start

* Gather the name of your Kubernetes storage
    * Replace storage variables in the postgres and postgresVolumeClaim yaml files
* Deploy postgresVolumeClaim.yaml first
* Deploy postgres.yaml second 
    * This is a stateful set deployment for Postgres offering some redundancy.
    * Verify Postgres is running by using kubectl get pods
* Create the secrets (in the netshot namespace), used as files by the containers:
    ```bash
    kubectl -n netshot create secret generic pgpassword --from-literal=POSTGRES_PASSWORD='<db password>'
    kubectl -n netshot create secret generic postgresuser --from-literal=POSTGRES_USER=netshot
    kubectl -n netshot create secret generic dbpass --from-literal=SECRET_KEY='<db password>'
    kubectl -n netshot create secret generic dbuser --from-literal=SECRET_KEY=netshot
    kubectl -n netshot create secret generic dbencryption --from-literal=SECRET_KEY='<encryption password>'
    ```
    * `dbencryption` is the key used to encrypt the device credentials in the database: keep a backup of it.
      It is required (Netshot won't start without it). If Netshot was previously deployed without it, the key
      built into the image (`netshot`) was used: set it to `netshot` to keep the stored credentials readable.
* Deploy netshot.yaml
    * You must run the docker build netshot commands to build the netshot image
    * After you build the image you must store it in dockerhub or internal image repo.
* This deploys to a kubernetes namespace with the name netshot
* We could create a docker image and host it on dockerhub that could be used for this
