#!/bin/bash

# build app
sbt clean docker:publishLocal 

# run environment, wait for kafka, restart app
docker-compose down && docker-compose up -d && sleep 90 && docker-compose restart http-requests

# check input
docker exec -ti kafkamessagetohttprequest_kafka_1 bash -c 'kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic test --from-beginning'

# check output
docker logs kafkamessagetohttprequest_mockServer_1
