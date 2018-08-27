#! /bin/bash

cd /var/solr/

mkdir -p /var/solr/lib
cp /opt/solr/contrib/analysis-extras/lib/icu4j*.jar /var/solr/lib
cp /opt/solr/contrib/analysis-extras/lucene-libs/lucene-analyzers-icu*.jar /var/solr/lib