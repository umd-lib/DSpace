# DRUM OAI-PMH

## Introduction

This page describes the DRUM OAI-PMH functionality, as of DSpace 7. See
<https://wiki.lyrasis.org/display/DSDOC7x/OAI+2.0+Server>
for the DSpace documentation on the OAI-PMG server functionality.

This document focuses on the customizations made for DRUM.

## DRUM OAI-PMH Endpoints

The <https://opendata.lib.umd.edu/services.html#drum> website documents the
expected endpoint for the DRUM OAI-PMH server, with example searches.

The main endpoint is `<SERVER_URL>/oai/request`.

In the interest of stability, "/oai/request" URL path should be used as long
as feasible for accessing the DRUM OAI-PMH functionality.

## DSpace 7 Defaults

In DSpace 6, the OAI-PMH endpoint was the "/oai/request" URL path.

In DSpace 7, the front-end and back-end were split into two different components
with the OAI-PMH functionality being provided by the back-end component.

The demo DSpace website (see <https://wiki.lyrasis.org/display/DSDOC7x/DSpace+7+Demo+Quick+Start>)
handled this split with two separate URLS:

* <https://demo7.dspace.org/home> for the DSpace Angular front-end
* <https://api7.dspace.org/server/> for the DSpace back-end

In the local development environment, these correspond to the Angular front-end
running on <http://localhost:4000/> and the DSpace back-end running on
<http://localhost:8080/server/>.

## DRUM Server Kubernetes Changes

### Kubernetes Ingress Nginx Rewrites

For DRUM, we use a single URL (<https://drum.lib.umd.edu/> for production) for
both the front-end and back-end. The Kuberenetes Ingress configuration is set
up to send the "/server" URL path to the DSpace back-end, and any other URL path
to the Angular front-end.

This means that for DRUM, the default OAI-PMH URL path is "/server/oai/request".

To make available the "/oai/request" URL path for OAI-PHM queries, the
Nginx configuration in the Ingress configuration was modified to alias the
following URL paths to the DSpace 7 default path:

* /oai -> /server/oai
* /webjars -> /server/webjars

The "/webjars" alias is needed because the stock DSpace OAI-PMH HTML files use
relative URL addresses ('../webjars') to retrieve JavaScript and CSS resources
needed for the OAI-PMH functionality.

### Kubernetes Configuration Properties

The following line was added to the "local.cfg" file in each of the Kubernetes
overlays to modify the "oai.url" property, so that the "/oai" endpoint would be
referenced in the OAI-PMH output, instead of the "/server/oai" endpoint:

```text
oai.url = ${dspace.url}/${oai.path}
```

## Local Development Endpoints

For local development, the OAI-PMH endpoint remains at the DSpace 7 default
of <http://localhost:8080/server/oai>.
