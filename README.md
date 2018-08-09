# Kafka message to HTTP request
Send HTTP requests from Kafka messages.

###### Features:
- Kafka 1.1.0 client.
- Executes requests only once (successfully).
- Ignores duplicated messages (if `id` is set).
- Supports dynamic method, headers and body (defined on each message).
- Retries requests with exponential backoff.

## Usage
```
docker run -d \
  -e "kafka.topic=test" \
  -e "kafka.application.id=http-requests" \
  -e "kafka.bootstrap.servers=kafka:9092" \
  graphpathai/kafka-message-to-http-request
```

## Description
When a message is sent to the topic with `type` equals to `http`:

- If the message has `id` and Redis URI:
  1. Ignore the message if it exists on Redis.
  1. If not present on Redis, executes the HTTP request (retrying on failure).
  1. If the request was successful (on some retry), insert the id with a TTL.
- If the message doesn't have `id` or Redis is not configured:
  1. Executes the HTTP request (retrying on failure).

#### Deduplication
It will only process once, messages with an `id` and if a Redis service `uri` is configured.

##### Warnings:
- If the HTTP request fails (timeout or status code different than 2XX) it will be sent again according to the retry strategy (potentially duplicating the request).
  - If every retry fails, it won't be commited to kafka nor inserted to Redis.
- If the `PSETEX` command (store the message id) fails after every retry, the container will die and when restarted it won't find the message id on the cache so it will be re-executed.

## Messages

##### Example
```json
{
  "type": "http",
  "url": "https://test/path?query=value",
  "body": { "value": 123 }
}
```

Generates a similar request as:
```bash
curl -v -X POST 'https://test/path?query=value' \
  -H 'Content-Type: application/json' \
  -d '{"value":123}'
```

##### Schema
```json
{
  "type": "object",
  "properties": {
    "type": { "const": "http" },
    "id": { "type": "string" },
    "method": {
      "type": "string",
      "enum": ["GET", "HEAD", "POST", "PUT", "DELETE", "TRACE", "OPTIONS", "PATCH"],
      "default" : "POST"
    },
    "url": { "type": "string" },
    "headers": { 
      "type": "object",
      "default": { "Content-Type": "application/json" }
    },
    "body": {
      "type": ["string", "integer", "number", "boolean", "null", "array", "object"],
      "description": "any type"
    }
  },
  "required": ["type", "url"]
}
```

##### Notes
- Ignores messages with no `type` field or type field value different than `"http"`.
- For http and https request the `type` field must be the same (`http`)
- If not specified, `application/json` content type will be used when `body` is defined.
- If an `id` is present, future messages with the same `id` will be ignored.
  - Request with a successful response (2XX status code) and with an `id` defined, will be stored with a TTL. All messages with the same `id` until that TTL will be ignored.

## Config
Set the following keys on environment variables to override defaults. 

#### HTTP Client
We use Play WS client (https://github.com/playframework/play-ws).
Some defaults are overwritten on `application.conf`.

- `play.ws.ahc.*`: Configuration specific to the Ahc implementation of the WS client (Defaults: https://github.com/playframework/play-ws/blob/1.1.x/play-ahc-ws-standalone/src/main/resources/reference.conf).
- `play.ws.*`: Configuration for Play WS (Defaults: https://github.com/playframework/play-ws/blob/1.1.x/play-ws-standalone/src/main/resources/reference.conf).

#### Kafka
- `kafka.topic`: HTTP request message topic. For each message on this topic a request will be triggered to the url (**Required**).
- `kafka.application.id`: Streams application id (**Required**).
- `kafka.bootstrap.servers`: Kafka servers (**Required**).
- `kafka.*`: Streams config properties as described on the docs (https://kafka.apache.org/11/documentation.html#streamsconfigs).
- `kafka.consumer.*`: Consumer config as describe on the docs (https://kafka.apache.org/11/documentation.html#newconsumerconfigs).

#### Redis / Duplication prevention (Optional)
If you want to avoid duplicated requests (because of a container restart or a message being sent twice to the topic by other system), you must sent a unique `id` on the message and provide a Redis server to store the id once its request returns successfully.

- `redis.uri`: Redis service uri. (uri syntax: https://github.com/lettuce-io/lettuce-core/wiki/Redis-URI-and-connection-details#uri-syntax).
- `redis.ttl`: Maximum time for an id to be found as "already processed" (Default: 7 days).

#### Retries

##### HTTP Requests
- `http.retry.maxExecutions`: Number of retries if the HTTP request fail (status code != 2XX) (Default: 10).
- `http.retry.baseWait`: Time to wait between retries using random exponential backoff (Default: 100 millis).
- `http.retry.maxWait`: Maximum time to wait on each retry (the retry will never wait more than `maxWait`) (Default: 5 seconds).
- `http.retry.timeout`: Maximum time to wait for a successful response since the first request (Default: 10 minutes).

##### Redis Operations
- `redis.retry.maxExecutions`: Number of retries if the operation fail (Default: 10).
- `redis.retry.baseWait`: Time to wait between retries using random exponential backoff (Default: 100 millis).
- `redis.retry.maxWait`: Maximum time to wait on each retry (the retry will never wait more than `maxWait`) (Default: 500 millis).
- `redis.retry.timeout`: Maximum time to wait for a successful response since the first execution (Default: 1 minute).
