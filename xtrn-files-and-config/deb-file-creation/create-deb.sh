#!/bin/bash

export VERSION
VERSION=$(< ../../cloudwebapp/src/main/resources/version.txt)

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
cp ../install-cert.sh cloud-server_"${VERSION}"_arm64/tmp

cp ../../cloudwebapp/build/libs/cloudwebapp-11.0.0.war cloud-server_"${VERSION}"_arm64/tmp
cp ../apache-tomcat-10/conf/server.xml ../apache-tomcat-10/conf/tomcat-users.xml cloud-server_"${VERSION}"_arm64/tmp

cat << EOF > cloud-server_"${VERSION}"_arm64/DEBIAN/control
Package: cloud-server
Version: $VERSION
Architecture: arm64
Maintainer: Richard Austin <richard.david.austin@gmail.com>
Description: Cloud server to provide access to NVRs running the CloudProxy.
Depends: openjdk-21-jre-headless (>=21.0.0), openjdk-21-jre-headless (<< 21.9.9),
 nginx (>=1.24.0), nginx(<=1.40.9),
 tomcat10 (>=10.0.0), tomcat10 (<= 11.99.99),
 tomcat10-admin (>=10.0.0), tomcat10-admin (<= 10.99.99)

EOF

dpkg-deb --build --root-owner-group cloud-server_"${VERSION}"_arm64
