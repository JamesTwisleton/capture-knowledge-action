package com.cka.provider.eventbus;

import com.cka.core.event.PipelineEvent;
import com.cka.provider.ProviderHealth;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.AnonymousQueue;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The event bus on RabbitMQ (PRD 8.1): the real broker every handoff in the pipeline travels
 * through, so no stage and no provider holds a reference to the next.
 *
 * <p>Routing keys are {@code cka.event.<EventSimpleName>}. A subscriber to one event binds that
 * key exactly; a subscriber to the sealed {@code PipelineEvent} binds {@code cka.event.*} and
 * receives all six, which is what keeps {@code AuditTrail} working against a broker that has
 * no notion of a type hierarchy.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "cka.provider", name = "eventbus", havingValue = "rabbitmq")
public class RabbitEventBus implements EventBus {

    static final String EXCHANGE = "cka.events";
    private static final String ROUTING_PREFIX = "cka.event.";

    private final RabbitTemplate rabbitTemplate;
    private final AmqpAdmin amqpAdmin;
    private final ConnectionFactory connectionFactory;
    private final MessageConverter messageConverter;
    private final TopicExchange exchange;
    private final List<SimpleMessageListenerContainer> containers = new CopyOnWriteArrayList<>();

    RabbitEventBus(
            RabbitTemplate rabbitTemplate,
            AmqpAdmin amqpAdmin,
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter,
            TopicExchange exchange) {
        this.rabbitTemplate = rabbitTemplate;
        this.amqpAdmin = amqpAdmin;
        this.connectionFactory = connectionFactory;
        this.messageConverter = messageConverter;
        this.exchange = exchange;
    }

    /**
     * Declares the exchange at startup rather than waiting for the first subscriber.
     *
     * <p>Two reasons. An event published before anything has subscribed would otherwise go to an
     * exchange that does not exist yet, and AMQP drops that silently — no error at the publisher,
     * no message. And a broker that is unreachable, or credentials that are wrong, should be
     * visible at boot rather than at whatever hour the first recording lands.
     *
     * <p>Failure is logged, not thrown. The backend stays up and reports the problem through
     * {@link #checkHealth()}; taking the whole application down because one provider is
     * unreachable is the same mistake as letting it fail the container healthcheck.
     */
    @PostConstruct
    void declareExchange() {
        try {
            amqpAdmin.declareExchange(exchange);
        } catch (Exception e) {
            log.warn("Could not declare the {} exchange at startup: {}", EXCHANGE, e.getMessage());
        }
    }

    @Override
    public void publish(PipelineEvent event) {
        rabbitTemplate.convertAndSend(EXCHANGE, routingKey(event.getClass()), event);
    }

    @Override
    public <E extends PipelineEvent> void subscribe(Class<E> type, Consumer<? super E> handler) {
        // Each subscriber gets its own queue, which is what makes this publish/subscribe rather
        // than a work queue: sharing one would have the broker hand each event to whichever
        // subscriber was free, so the knowledge provider and the audit trail would see roughly
        // half the pipeline each.
        //
        // Anonymous, so the interface needs no subscriber identity to name one with, and
        // auto-deleting, so a restart does not leave queues behind filling with events nobody
        // will read. The cost is that an event published while the app is down is lost; see
        // AGENTS.md, which records that and why it is acceptable for now.
        var queue = new AnonymousQueue();
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(BindingBuilder.bind(queue).to(exchange).with(bindingPattern(type)));

        // Declared and bound before the listener starts, deliberately. The binding is what makes
        // the broker keep an event, so anything published from here on is held in the queue even
        // if the consumer is still attaching — without which subscribe() would be racy and every
        // test using it intermittently flaky.
        var container = new SimpleMessageListenerContainer(connectionFactory);
        container.setQueues(queue);
        container.setMessageListener(message -> {
            var event = messageConverter.fromMessage(message);
            if (type.isInstance(event)) {
                handler.accept(type.cast(event));
            } else {
                log.warn("Ignoring {} delivered to a {} subscriber", event.getClass(), type);
            }
        });
        // Built by hand rather than by the container, so its own initialisation has to be
        // invoked explicitly; start() alone leaves it without the plumbing it sets up here.
        container.afterPropertiesSet();
        container.start();
        containers.add(container);
    }

    @Override
    public ProviderHealth checkHealth() {
        try {
            // A channel on the shared connection, rather than opening one of our own: it is the
            // same path publishing uses, so this reports on what the pipeline actually depends on.
            var open = rabbitTemplate.execute(channel -> channel.isOpen());
            return Boolean.TRUE.equals(open)
                    ? ProviderHealth.up("connected to " + describeBroker())
                    : ProviderHealth.down("channel to " + describeBroker() + " is closed");
        } catch (Exception e) {
            return ProviderHealth.down("cannot reach " + describeBroker() + ": " + e.getMessage());
        }
    }

    @PreDestroy
    void stopListening() {
        containers.forEach(SimpleMessageListenerContainer::stop);
    }

    private String describeBroker() {
        return connectionFactory.getHost() + ":" + connectionFactory.getPort();
    }

    /** The key an event is published with. */
    static String routingKey(Class<? extends PipelineEvent> type) {
        return ROUTING_PREFIX + type.getSimpleName();
    }

    /**
     * The pattern a subscriber binds with. {@code *} matches exactly one segment, so the sealed
     * parent matches every event and nothing else on the exchange.
     */
    static String bindingPattern(Class<? extends PipelineEvent> type) {
        return type.equals(PipelineEvent.class) ? ROUTING_PREFIX + "*" : routingKey(type);
    }
}
