/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package kafka.txn.issue.demo;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.StoreBuilder;
import org.apache.kafka.streams.state.Stores;

public class App {

    public static void main(String[] args) throws Exception {
        createInputTopic();
        Thread.sleep(1000 * 5); // wait for topics to propagate
        run();
    }

    public static void run() {
        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "punctuator-fence-issue");
        props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "foo");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafkaState");
        props.put(StreamsConfig.PROCESSING_GUARANTEE_CONFIG, "exactly_once_v2");
        props.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

        Topology topology = new Topology();
        topology.addSource("source", "input-topic");
        topology.addProcessor(
            "processor",
            () -> {
                return new MyProcessor();
            },
            "source"
        );
        StoreBuilder<KeyValueStore<String, String>> builder = Stores.keyValueStoreBuilder(
            Stores.persistentKeyValueStore("store"),
            Serdes.String(),
            Serdes.String()
        );
        topology.addStateStore(builder, "processor");

        final KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close();
        }));
    }

    private static void createInputTopic() throws Exception {
        Properties adminConf = new Properties();
        adminConf.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        Admin kAdmin = Admin.create(adminConf);

        try {
            kAdmin.createTopics(Collections.singleton(
            new NewTopic("input-topic", 1, (short) 1)
        )).values().get("input-topic").get(); // make it synchronous-ish
        }  catch (Exception e) {
            if (
                e.getCause() != null && e.getCause() instanceof TopicExistsException
            ) {
                System.out.println("input-topic already exists.");
                return;
            } else {
                throw e;
            }
        }
        System.out.println("Created input-topic");
    }
}


class MyProcessor implements Processor<String, String, String, String> {

    private ProcessorContext<String, String> ctx;
    private KeyValueStore<String, String> store;

    public void init(final ProcessorContext<String, String> ctx) {
        ctx.schedule(
            Duration.ofMillis(5000),
            PunctuationType.WALL_CLOCK_TIME,
            this::punctuate
        );
        this.store = ctx.getStateStore("store");
    }

    public void process(final Record<String, String> rec) {
        ctx.forward(new Record<>("hi", "there", rec.timestamp()));
    }

    public void punctuate(long time) {
        System.out.println("\n\nhi from punctuator\n");
        // This seems to cause a hanging transaction when "foo()" doesn't exist.
        store.delete("foo");
    }
}