# DRUM Database Restore

**Note:** The following steps describe retrieving a DRUM DSpace database
snapshot from Kubernetes for use with DRUM for local development.

1) Switch to the Kubernetes namespace from which the database snapshot
   should be retrieved (the following example uses the Kubernetes "test"
   namespace):

   ```bash
   $ kubectl config use-context test
   ```

2) Run the following command to run "pg_dump" in the "drum-db-0" Kubernetes pod,
   placing the database dump in the `postgres-init` subdirectory:

    ```bash
    $ kubectl exec drum-db-0 -- pg_dump -O -U drum -d drum > postgres-init/drum.sql
    ```

3) (Optional) This step can be skipped, if you are following the instructions in
   [dspace/docs/DockerDevelopmentEnvironment.md](DockerDevelopmentEnvironment.md).

   Start the "dspacedb" container and wait for the restore to complete.

    ```bash
    $ docker compose -p d8 up -d dspacedb
    ```

    To determine if the restore is complete, run the following command, and wait
    for the "ready to accept connections" message:

    ```bash
    $ docker logs -f dspacedb
    ...
    ...
    ... LOG:  database system is ready to accept connections
    ```

    Hit `<Ctrl-C>` to exit the "docker logs" command and return to the terminal.
