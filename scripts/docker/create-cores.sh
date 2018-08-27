#! /bin/bash

cd /var/solr/cores

mkdir -p drum-search
mkdir -p drum-statistics
mkdir -p drum-oai
mkdir -p drum-authority

cp -r /config/search/* drum-search/
cp -r /config/statistics/* drum-statistics/
cp -r /config/oai/* drum-oai/
cp -r /config/authority/* drum-authority/

echo name=drum-search >  drum-search/core.properties
echo name=drum-statistics > drum-statistics/core.properties
echo name=drum-oai > drum-oai/core.properties
echo name=drum-authority > drum-authority/core.properties