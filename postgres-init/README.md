# Database initialization for docker-compose

Place your database dump file in this directory, and the dspacedb container
will use it to initialize the database.

See [dspace/docs/DrumDBRestore.md](../dspace/docs/DrumDBRestore.md) for
information on retrieving a database dump file from Kubernetes.

## Delete local database data

The postgres container will use the initialization dump only if the
database is not previously initialized. If you would like to reinitialize the
database, stop the dspacedb container and delete the volume.

```zsh
$ docker-compose -p d9 down
$ docker volume rm d9_pgdata
```
