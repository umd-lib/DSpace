# Use the UMD custom solr:9.8 image with eMudhra fix as the until upstream
#   solr publishes a image without the vulnerability
FROM docker.lib.umd.edu/solr:9.8-umd-0

COPY --chown=solr ./search /opt/solr/server/solr/configsets/search
COPY --chown=solr ./statistics /opt/solr/server/solr/configsets/statistics
COPY --chown=solr ./oai /opt/solr/server/solr/configsets/oai
