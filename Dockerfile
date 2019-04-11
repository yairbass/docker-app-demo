FROM openjdk:8-jdk
MAINTAINER Yair Bass (yairbass@gmail.com)
VOLUME /tmp
ADD spring-petclinic-2.1.0.BUILD*.jar spring-petclinic-2.1.0.jar
ADD src/main/resources/db/mysql/ .
CMD java -Djava.security.egd=file:/dev/./urandom -jar ./spring-petclinic-2.1.0.jar

