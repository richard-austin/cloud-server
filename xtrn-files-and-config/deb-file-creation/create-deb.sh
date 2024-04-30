#!/bin/bash

export VERSION
VERSION=$(< ../../cloudwebapp/grails-app/assets/version/version.txt)

rm -r cloud-server_*_arm64

mkdir -p cloud-server_"${VERSION}"_arm64/etc/cloud-server

cp ../privateKey cloud-server_"${VERSION}"_arm64/etc/cloud-server

mkdir -p cloud-server_"${VERSION}"_arm64/DEBIAN
cp preinst postinst prerm postrm cloud-server_"${VERSION}"_arm64/DEBIAN

mkdir -p cloud-server_"${VERSION}"_arm64/var/cloud-server/db
mkdir -p cloud-server_"${VERSION}"_arm64/var/log/cloud

mkdir -p cloud-server_"${VERSION}"_arm64/tmp

mkdir -p cloud-server_"${VERSION}"_arm64/lib/systemd/system/

cp -r ../nginx.conf cloud-server_"${VERSION}"_arm64/tmp
cp ../apache-tomcat-9.0.46/conf/server.xml ../apache-tomcat-9.0.46/conf/tomcat-users.xml cloud-server_"${VERSION}"_arm64/tmp
cp ../install-cert.sh ../tomcat9 cloud-server_"${VERSION}"_arm64/tmp
cp ../../cloudwebapp/build/libs/cloudwebapp-7.3.war cloud-server_"${VERSION}"_arm64/tmp

cat << EOF > cloud-server_"${VERSION}"_arm64/DEBIAN/control
Package: cloud-server
Version: $VERSION
Architecture: arm64
Maintainer: Richard Austin <richard.david.austin@gmail.com>
Description: Cloud server to provide access to NVRs running the CloudProxy.
Depends: openjdk-19-jre-headless (>=19.0.2), openjdk-19-jre-headless (<< 19.9.9),
 nginx (>=1.24.0), nginx(<=1.24.9),
 tomcat9 (>=9.0.43-1), tomcat9 (<= 10.0.0),
 tomcat9-admin (>=9.0.70-1), tomcat9-admin (<= 10.0.0)
EOF

dpkg-deb --build --root-owner-group cloud-server_"${VERSION}"_arm64
