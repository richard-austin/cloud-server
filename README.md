<h2 style="text-align: center">Security Cam Cloud Service</h2>
<h2 style="text-align: center">Cloud Service for Hosting Multiple Security Cam Instances</h2>
### Introduction
The <a href="https://github.com/richard-austin/security-cam">Security Cam</a> (NVR, Network Video Recorder) project is primarily 
designed to run without the need for a cloud service, inside a secure LAN, with access from WAN being made available 
with port forwarding. The Cloud Server can be used to provide access to a number of NVR instances
without the port forwarding set up, as the NVR makes a client connection to the Cloud Service through
which the web interactions are multiplexed.

The Cloud Server is intended to be run at a public internet address, to which instances of Security Cam
are configured to make client connections. 
The NVR does not need to have a local user account set up to connect to the Cloud Service. Instead
a user account is created on the Cloud Service for each NVR connected to it. When the user account is created,
the required username, password and email address are entered along with the NVR's unique product ID
by which the Cloud Service identifies the specific NVR, 
When an NVR has no local account, it will attempt to connect to the Cloud Service by default.

#### Cloud Service Features
* Hosts multiple NVRs with each one having its own user account
* Admin Access
  * Change admin account password.
  * Show list of connected NVRs
  * Indicate which NVRs have Cloud accounts
  * Indicate which NVRs with Cloud accounts are connected to the Cloud.
  * For each NVR, show number of users viewing the NVR through the Cloud Service.
  * Change users Cloud account password.
  * Change users Cloud account email address.
  * Enable/Disable users Cloud account.
  * Delete Users Cloud Account.
  * Show only accounts where the NVR is offline (not connected to Cloud)
  * Show only connected NVRs with no Cloud user account set up.
  * NVR list filter for user name/product id
* Client NVR access
  * Most features present through direct access to the NVR are present with client access on the Cloud.
Camera web admin pages are not accessible through the Cloud as they are with direct NVR access. The Admin functions
are not present, though you can add or remove the local NVR account.
  * NVR configuration  
  * Add/Remove local NVR account.
### Run time platform, for Cloud Server
The current build configuration (./gradlew buildDebFile) is for Raspberry pi V4 running headless (server) version of Ubuntu 23.04 (Lunar Lobster).
The application runs on Java on the server side, so it can easily be adapted to other platforms.

