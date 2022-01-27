#!/bin/bash

export VERSION
VERSION=$(< ../../cloudwebapp/grails-app/assets/version/version.txt)

rm -r cloud_*_arm64

mkdir -p cloud_"${VERSION}"_arm64/etc/cloud

cp ../cacert.jks ../client.jks ../cloud.jks \
 cloud_"${VERSION}"_arm64/etc/cloud

mkdir -p cloud_"${VERSION}"_arm64/DEBIAN
cp preinst postinst prerm postrm cloud_"${VERSION}"_arm64/DEBIAN

mkdir -p cloud_"${VERSION}"_arm64/home/cloud/logs

mkdir -p cloud_"${VERSION}"_arm64/tmp

mkdir -p cloud_"${VERSION}"_arm64/lib/systemd/system/

cp -r ../nginx.conf cloud_"${VERSION}"_arm64/tmp
cp ../apache-tomcat-9.0.46/conf/server.xml ../apache-tomcat-9.0.46/conf/tomcat-users.xml cloud_"${VERSION}"_arm64/tmp
cp ../install-cert.sh cloud_"${VERSION}"_arm64/tmp
cp ../../cloudwebapp/build/libs/cloudwebapp-0.1.war cloud_"${VERSION}"_arm64/tmp

cat << EOF > cloud_"${VERSION}"_arm64/DEBIAN/control
Package: cloud
Version: $VERSION
Architecture: arm64
Maintainer: Richard Austin <richard.david.austin@gmail.com>
Description: Cloud server to provide access to NVRs running the CloudProxy.
Depends: openjdk-11-jre-headless (>=11.0.11), openjdk-11-jre-headless (<< 12.0.0),
 nginx (>=1.18.0), nginx(<=1.20.9),
 tomcat9 (>=9.0.43-1), tomcat9 (<= 10.0.0),
 tomcat9-admin (>=9.0.43-1), tomcat9-admin (<= 10.0.0)
EOF

dpkg-deb --build --root-owner-group cloud_"${VERSION}"_arm64
