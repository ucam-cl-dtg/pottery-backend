Pottery-backend provides an API for testing submissions to programming exercises. Internally it uses git repositories to store tasks and code submissions which are tested within docker containers. 

It is currently under development and so you should expect things to be broken and for API changes to occur.

Building instructions
=====================

Build with maven. Most dependencies are available directly from
maven central and so will be pulled automatically.

Development dependencies
------------------------

Some dependencies are not in maven central and so should be built
locally and installed into your local maven repository.

 * pottery-container-interface: repository at https://github.com/ucam-cl-dtg/pottery-container-interface.git
 * resteasy-exception-chains: repository at https://github.com/ucam-cl-dtg/resteasy-exception-chains.git
 * resteasy-docker: repository at https://github.com/ucam-cl-dtg/resteasy-docker.git
 * resteasy-cors: repository at https://github.com/ucam-cl-dtg/resteasy-cors.git

For each of the above you need to:

    git clone [repository-url]
    cd [project-name]
    mvn clean compile package install


Compiling pottery-backend
-------------------------

    cd [project-name]
    mvn clean compile package

This will produce a WAR file in /target


Installation
============

Docker
------

You should install docker, and make sure that its listening on the port 2357. At the moment pottery requires only one image exists, this should be called 'template:java'. Create it with something like:

    docker run -i -t ubuntu:16.04
    apt-get update
    apt-get upgrade -u
    apt-get install software-properties-common
    add-apt-repository ppa:webupd8team/java
    apt-get update
    apt-get install oracle-java8-installer git maven vnc4server sudo
    apt-get clean
    [exit]
    docker ps -a
    docker commit [containername] template:java
    docker rm [containername]

Postgres
--------

You need a postgres user called 'pottery' with password 'pottery' and a database called 'pottery'. Configure your server to allow TCP connections as this user to the 'pottery' database (from localhost only).

Create the schema in the database:

    psql -U pottery -h localhost -f docs/pg_schema.sql


Tomcat
------

Pottery has been tested with tomcat 8. Copy the pottery WAR file into the webapps directory of your tomcat installation and restart.


Testing
=======

pottery-backend comes with a simple text interface. This should be accessible at http://localhost:8080/pottery-backend/index.html or similar depending on your installation, replace 'pottery-backend' with whatever the name of the WAR file is that you used.