#! /bin/bash

if [ ! -d "/var/solr/lib" ]; then
  /scripts/copy-jars.sh
fi

# Create (or update) cores
/scripts/create-cores.sh

/opt/solr/bin/solr start -p 8983 -s /var/solr -f