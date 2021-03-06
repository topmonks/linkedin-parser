FROM iron/java:1.8
MAINTAINER Aleš ROubíček <ales.roubicek@topmonks.com>
ENV PORT 3002
EXPOSE 3002
RUN mkdir -p /usr/src/app
WORKDIR /usr/src/app
COPY app-standalone.jar /usr/src/app/
CMD ["java", "-server", "-jar", "app-standalone.jar"]