# Prepare Postfix directories and start Postfix
mkdir -p /var/spool/postfix/etc
cp /etc/resolv.conf /var/spool/postfix/etc/
cp /etc/nsswitch.conf /var/spool/postfix/etc/
cp /etc/hosts /var/spool/postfix/etc/
chown -R postfix:postfix /var/lib/postfix/
chmod 755 /var/spool/postfix/ /var/lib/postfix/
postfix start
