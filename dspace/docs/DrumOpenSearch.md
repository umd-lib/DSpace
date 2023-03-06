# DRUM OpenSearch

## Introduction

This page describes the DRUM OpenSearch functionality, as of DSpace 7. See

* <https://wiki.lyrasis.org/display/DSDOC7x/Business+Logic+Layer#BusinessLogicLayer-OpenSearchSupport>
* <https://wiki.lyrasis.org/display/DSDOC7x/Configuration+Reference#ConfigurationReference-OpenSearchSupport>
<https://wiki.lyrasis.org/display/DSDOC7x/OAI+2.0+Server>

for the DSpace documentation on the OpenSearch server functionality.

This document focuses on the customizations made for DRUM.

## DRUM OpenSearch Endpoints

The <https://opendata.lib.umd.edu/services.html#drum> website documents the
expected endpoint for the DRUM OpenSearch server, with example searches.

The main endpoint is `<SERVER_URL>/open-search/discover`.

In the interest of stability, "/open-search/discover" URL path should be used as
long as feasible for accessing the DRUM OpenSearch functionality.

## DSpace 7 Defaults

In DSpace 6, the OpenSearch endpoint was the "/open-search/discover" URL path.

In DSpace 7, the front-end and back-end were split into two different components
with the OpenSearch functionality being provided by the back-end component.

The demo DSpace website (see <https://wiki.lyrasis.org/display/DSDOC7x/DSpace+7+Demo+Quick+Start>)
handled this split with two separate URLS:

* <https://demo7.dspace.org/home> for the DSpace Angular front-end
* <https://api7.dspace.org/server/> for the DSpace back-end

In the local development environment, these correspond to the Angular front-end
running on <http://localhost:4000/> and the DSpace back-end running on
<http://localhost:8080/server/>.

In a stock DSpace 7, the OpenSearch functionality is provided on the
"/server/opensearch/search" URL path, with an OpenSearch service description
at the "/server/opensearch/servce" URL path.

## DRUM Server Kubernetes Changes

### Kubernetes Ingress Nginx Rewrites

For DRUM, we use a single URL (<https://drum.lib.umd.edu/> for production) for
both the front-end and back-end. The Kuberenetes Ingress configuration is set
up to send the "/server" URL path to the DSpace back-end, and any other URL path
to the Angular front-end.

This means that for DRUM, the default OpenSearch URL path is
"/server/opensearch/search".

To make available the "/open-search/discover" URL path for OpenSearch queries,
the Nginx configuration in the Ingress configuration was modified to alias the
following URL paths to the DSpace 7 default path:

* /open-search/discover -> /server/opensearch/search
* /open-search -> /server/opensearch

### Kubernetes Configuration Properties

To ensure that the OpenSearch output includes the correct server URL and aliased
URL paths, the following two properties are configurated in the "local.cfg"
file of each Kubernetes overlay:

```text
opensearch.server.url = ${dspace.url}
websvc.opensearch.svccontext = open-search/discover
```

The "opensearch.server.url" property is a UMD custom property (used in the
customized "dspace/modules/additions/src/main/java/org/dspace/app/util/OpenSearchServiceImpl.java"
file), to ensure that that Angular front-end URL, not the DSpace back-end
URL, is used in the OpenSearch output.

The "websvc.opensearch.svccontext" property is configured to ensure that the
the "/open-search/discover" endpoint is be referenced in the
OpenSearch output, instead of the default "/server/opensearch/search" endpoint.

```text
websvc.opensearch.svccontext = open-search/discover
```

**Note:** Based on the DSpace 7 documentation, it seems as if the
"websvc.opensearch.svccontext" property should change the default DSpace 7
OpenSearch endpoint, but does not. The "opensearch/search" endpoint is clearly
hard-coded in the "dspace-server-webapp/src/main/java/org/dspace/app/rest/OpenSearchController.java"
file.

## Local Development Endpoints

For local development, the OpenSearch endpoints remain at the DSpace 7 defaults
of:

* <http://localhost:8080/server/opensearch/search>
* <http://localhost:8080/server/opensearch/service>
