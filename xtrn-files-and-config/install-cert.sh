#!/bin/bash

# Create and install the security certificate and key for the site
openssl req -x509 -newkey rsa:4096 -keyout cloud.key -out cloud.crt -nodes -days 2000
chown root:root cloud.key
chown root:root cloud.crt
mv cloud.key /etc/nginx
mv cloud.crt /etc/nginx
