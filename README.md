# Demonstration of Punctuator Fencing Issue

This is a reproducible example that shows a bug in Kafka Streams.

## Expected behavior

Calling `KeyValueStore::delete(someKey)` in a punctuator should return `null` if `someKey` is not present in the store. It should not cause a hanging transaction.

Furthermore, the punctuator as defined in this minimal example should run every 5s and print out "hi from punctuator."

## Actual behavior

The punctuator throws a `ProducerFencedException` likely due to a timeout error after about 4-5 punctuations (roughly 20-30 seconds).

## How to reproduce

First, set up the kafka container locally.

```
docker-compose up -d
```

Next, run the streams app and wait:

```
gradle run
```

## Cleanup

```
docker-compose down
```