package com.cka.provider.eventbus;

import com.cka.core.event.PipelineEvent;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.DefaultClassMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The AMQP-specific beans behind {@link RabbitEventBus}: the exchange events are published to,
 * and the converter that turns them into JSON and back.
 *
 * <p>Both live here rather than in {@code com.cka.config} because neither is a choice about
 * <em>which</em> provider is in use — that is the {@code cka.provider.eventbus} property — but
 * about how this one works internally.
 */
@Configuration
@ConditionalOnProperty(prefix = "cka.provider", name = "eventbus", havingValue = "rabbitmq")
class RabbitEventBusConfiguration {

    /**
     * Topic, not direct or fanout, and that is the load-bearing choice. A subscriber to one
     * event binds its exact routing key; {@code AuditTrail}, which subscribes to the sealed
     * parent to catch all six, binds a wildcard. A direct exchange would force it to declare
     * six separate bindings, and a seventh event added later would silently stop being
     * audited — the failure mode the in-memory bus cannot have, because there a subscription
     * to the parent type is just {@code Class.isInstance}.
     */
    @Bean
    TopicExchange pipelineEventExchange() {
        return new TopicExchange(RabbitEventBus.EXCHANGE, true, false);
    }

    /**
     * Records which event a message holds in the {@code __TypeId__} header, under the event's
     * simple name — the same name that ends its routing key, so the wire, the bindings and the
     * management UI all use one vocabulary.
     *
     * <p>The mapping is built from the sealed interface's own permitted subclasses, so a seventh
     * event is carried by the compiler rather than by remembering to extend a list here. Giving
     * it an explicit mapping at all is what keeps fully-qualified Java class names off the wire:
     * left to itself the converter writes {@code com.cka.core.event.ContentCaptured}, which
     * would make a package rename a breaking change to the message format and would mean nothing
     * to a non-Java consumer.
     *
     * <p>This is also why {@code com.cka.core.event} needs no Jackson annotations: the type is
     * recorded by the adapter that does the serialising, leaving the records compiling against
     * nothing but the JDK.
     *
     * <p>A bean in its own right, rather than a local inside the converter below, so that Spring
     * runs its {@code afterPropertiesSet()}. That is where the name-to-class map set here is
     * inverted into the class-to-name one publishing consults, and a mapper built as a local
     * never gets that call: nothing registers it, so nothing runs its lifecycle. Publishing then
     * falls back to the fully-qualified class name and the consumer rejects it as an untrusted
     * package — a failure that surfaces on the far side of the broker, nowhere near its cause.
     */
    @Bean
    DefaultClassMapper pipelineEventClassMapper() {
        Map<String, Class<?>> byName = Arrays.stream(PipelineEvent.class.getPermittedSubclasses())
                .collect(Collectors.toMap(Class::getSimpleName, Function.identity()));
        var classMapper = new DefaultClassMapper();
        classMapper.setIdClassMapping(byName);
        return classMapper;
    }

    /** Carries an event as JSON, tagged with the type id that {@link #pipelineEventClassMapper} assigns. */
    @Bean
    MessageConverter pipelineEventMessageConverter(DefaultClassMapper classMapper) {
        var converter = new JacksonJsonMessageConverter();
        converter.setClassMapper(classMapper);
        return converter;
    }

    /**
     * Declared here rather than left to auto-configuration so that publishing is guaranteed to
     * use the converter above. Boot will set a {@code MessageConverter} bean on the template it
     * builds, but only if the bean exists by the time that auto-configuration runs — and this
     * one is behind a {@code @ConditionalOnProperty}. When it loses that race the template
     * falls back to writing the fully-qualified Java class name into {@code __TypeId__}, which
     * the receiving side then rejects as an untrusted package: a failure that surfaces as a
     * listener exception at runtime, long after the publish looked like it worked.
     */
    @Bean
    RabbitTemplate pipelineEventRabbitTemplate(ConnectionFactory connectionFactory, MessageConverter converter) {
        var template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }
}
