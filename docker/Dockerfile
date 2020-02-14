FROM tomcat:9-jdk11-openjdk-slim

RUN mkdir -p $CATALINA_HOME/webapps/ROOT/WEB-INF/
WORKDIR $CATALINA_HOME/webapps/ROOT

EXPOSE 8080/tcp

ONBUILD COPY maven/lib/* $CATALINA_HOME/lib/
ONBUILD COPY maven/ROOT.war $CATALINA_HOME/webapps/ROOT.war
ONBUILD RUN jar xf ../ROOT.war; rm -f ../ROOT.war