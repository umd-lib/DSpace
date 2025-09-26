#!/bin/bash
#
# The contents of this file are subject to the license and copyright
# detailed in the LICENSE and NOTICE files at the root of the source
# tree and available online at
#
# http://www.dspace.org/license/
#

# Prepare Postfix directories and start Postfix
mkdir -p /var/spool/postfix/etc
cp /etc/resolv.conf /var/spool/postfix/etc/
cp /etc/nsswitch.conf /var/spool/postfix/etc/
cp /etc/hosts /var/spool/postfix/etc/
chown -R postfix:postfix /var/lib/postfix/
chmod 755 /var/spool/postfix/ /var/lib/postfix/
postfix start
