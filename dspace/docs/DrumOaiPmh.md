# DRUM OAI-PMH

## Introduction

This document describes customizations to the DSpace OAI-PMH (dspace-oai)
functionality.

## XOAI OAI Metadata format to excludes unauthorized bitstream

Modified the "[ItemUtils][item-utils-source]" class (used by the "xoai" metadata
format, and by the other OAI-PMH metadata formats), which builds the XOAI
document for each item, so that for bundles other than "ORIGINAL"
 (i.e., "TEXT", "THUMBNAIL", "LICENSE", etc.):

* a bundle is skipped if it cannot be read by Anonymous
* a bitstream is skipped if it cannot be read by Anonymous

This prevents the names (and other metadata) of the extracted text and
thumbnail derivatives of restricted files (such as ".pdf.txt" and ".pdf.jpg")
from being exposed.

Bitstreams in the "ORIGINAL" bundle are still included, including restricted
ones, so that the resource policy information (including embargo dates)
continues to be available via the "resourcePolicies" element, which is used by
aggregators such as OpenAIRE.

The authorization check is evaluated against the Anonymous group, as the OAI
indexer (`/dspace/bin/dspace oai import`) builds the XOAI documents using an
unauthenticated context.

The changes in "ItemUtils" are marked with "UMD Customization" comments, as it
is a stock DSpace file.

[item-utils-source]: ../../dspace-oai/src/main/java/org/dspace/xoai/util/ItemUtils.java
