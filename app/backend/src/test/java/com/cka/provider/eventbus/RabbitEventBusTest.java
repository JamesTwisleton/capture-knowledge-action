package com.cka.provider.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.Action;
import com.cka.core.ActionType;
import com.cka.core.CapturedContent;
import com.cka.core.ChainId;
import com.cka.core.Knowledge;
import com.cka.core.PageReference;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.event.ActionApplied;
import com.cka.core.event.ActionFailed;
import com.cka.core.event.ActionProposed;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.ContentSummarised;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/**
 * T07's acceptance criterion: a test publishes a fake event and a subscriber receives it via
 * the real broker.
 *
 * <p>Against a real RabbitMQ, not the in-memory bus, because the three things that can differ
 * between them are exactly the three things worth proving — the event survives being
 * serialised and read back, routing delivers it to the right subscribers and no others, and
 * delivery is asynchronous rather than finished by the time {@code publish} returns.
 *
 * <p>One container is shared by every test here: starting a broker costs seconds, and nothing
 * below depends on a clean one, since each test subscribes to its own fresh queue. That holds
 * only while these run sequentially, which is JUnit's default and is not overridden anywhere —
 * a wildcard subscriber would otherwise collect events published by whatever ran beside it.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Events reach their subscribers through a real broker")
class RabbitEventBusTest {

    @Container
    @ServiceConnection
    static final RabbitMQContainer BROKER = new RabbitMQContainer("rabbitmq:3-management-alpine");

    /** Generous: it covers a cold broker and a container still attaching its consumer. */
    private static final int WAIT_SECONDS = 15;

    private static final Provenance LLM =
            Provenance.builder().provider("test-llm").model("test-model").build();
    private static final CapturedContent REFINEMENT = CapturedContent.builder()
            .sourceUri("memory://recordings/refinement.mp4")
            .title("Sprint Refinement 2026-10-08")
            .contentType("Sprint Refinement")
            .transcript("We agreed the login bug is a duplicate.")
            .platformSummary("Login bug closed as duplicate.")
            .build();
    private static final Summary SUMMARY =
            Summary.builder().text("Decided: a duplicate.").producedBy(LLM).build();
    private static final Knowledge KNOWLEDGE = Knowledge.builder()
            .page(PageReference.builder()
                    .id("refinement.md")
                    .location("memory://vault/refinement.md")
                    .build())
            .content(REFINEMENT)
            .summary(SUMMARY)
            .build();
    private static final Action ACTION = Action.builder()
            .type(ActionType.CLOSE)
            .workItemId("CKA-42")
            .excerpt("We agreed the login bug is a duplicate.")
            .confidence(0.92)
            .decidedBy(LLM)
            .build();

    /**
     * One populated example of every event {@link PipelineEvent} permits, in the order the
     * pipeline produces them. Declared after the records above because static initialisers run
     * in declaration order.
     *
     * <p>Both the round trip and the wildcard test are driven from this list rather than from a
     * hand-picked event or two, because the converter maps types one at a time: a record whose
     * shape Jackson cannot handle breaks only its own kind, and the five that still work say
     * nothing about the sixth. {@link #everyKindOfEventHasAnExample()} is what keeps the list
     * honest when a seventh event is added.
     */
    private static final List<PipelineEvent> EVERY_KIND_OF_EVENT = List.of(
            ContentCaptured.builder()
                    .chainId(ChainId.next())
                    .content(REFINEMENT)
                    .build(),
            ContentSummarised.builder()
                    .chainId(ChainId.next())
                    .content(REFINEMENT)
                    .summary(SUMMARY)
                    .build(),
            KnowledgeStored.builder()
                    .chainId(ChainId.next())
                    .knowledge(KNOWLEDGE)
                    .build(),
            ActionProposed.builder().chainId(ChainId.next()).action(ACTION).build(),
            ActionApplied.builder().chainId(ChainId.next()).action(ACTION).build(),
            ActionFailed.builder()
                    .chainId(ChainId.next())
                    .action(ACTION)
                    .error("the tracker rejected the transition")
                    .build());

    @Autowired
    private EventBus bus;

    /** Named by type, because a record's {@code toString} is the whole payload. */
    static Stream<Named<PipelineEvent>> everyKindOfEvent() {
        return EVERY_KIND_OF_EVENT.stream()
                .map(event -> Named.of(event.getClass().getSimpleName(), event));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyKindOfEvent")
    @DisplayName("An event published to the broker comes back to a subscriber unchanged")
    void anEventPublishedToTheBrokerComesBackToASubscriberUnchanged(PipelineEvent published) throws Exception {
        // Its own concrete type, so this proves the exact routing key as well as the round trip.
        var inbox = subscribeTo(published.getClass());

        bus.publish(published);

        // Equality across the whole record is the point: it only holds if every field survived
        // the round trip through JSON, including the nested records and the chain id.
        assertThat(next(inbox)).isEqualTo(published);
    }

    @Test
    @DisplayName("Every kind of event the pipeline can publish has an example above")
    void everyKindOfEventHasAnExample() {
        // The guard that makes "every kind" true rather than a claim about whichever events
        // happened to be written here first. The class mapper derives its type ids from the
        // permitted subclasses, so a seventh event is published and routed the moment it
        // compiles — and would reach the broker having never been serialised by a test.
        assertThat(EVERY_KIND_OF_EVENT.stream().<Class<?>>map(Object::getClass))
                .as("add an example to EVERY_KIND_OF_EVENT for every event PipelineEvent permits")
                .containsExactlyInAnyOrder(PipelineEvent.class.getPermittedSubclasses());
    }

    @Test
    @DisplayName("Subscribing to the sealed parent receives every kind of event")
    void subscribingToTheSealedParentReceivesEveryKindOfEvent() throws Exception {
        // What AuditTrail depends on. In memory it falls out of Class.isInstance; a broker has
        // no type hierarchy, so it has to be a wildcard binding — and since one pattern stands
        // in for all six, publishing every kind is what proves the pattern matches every kind.
        var inbox = subscribeTo(PipelineEvent.class);

        EVERY_KIND_OF_EVENT.forEach(bus::publish);

        // In publish order, which one publishing thread, one channel, one queue and one consumer
        // is the arrangement AMQP guarantees.
        var received = new ArrayList<PipelineEvent>();
        for (var _ : EVERY_KIND_OF_EVENT) {
            received.add(next(inbox));
        }
        assertThat(received).containsExactlyElementsOf(EVERY_KIND_OF_EVENT);
    }

    @Test
    @DisplayName("A subscriber is not sent events of other kinds")
    void aSubscriberIsNotSentEventsOfOtherKinds() throws Exception {
        var contentInbox = subscribeTo(ContentCaptured.class);
        // Subscribed second and published to second, so that receiving this one proves the
        // broker got that far — leaving an empty content inbox as evidence of filtering rather
        // than of a message still in flight.
        var parentInbox = subscribeTo(PipelineEvent.class);
        var stored = KnowledgeStored.builder()
                .chainId(ChainId.next())
                .knowledge(KNOWLEDGE)
                .build();

        bus.publish(stored);

        assertThat(next(parentInbox)).isEqualTo(stored);
        assertThat(contentInbox).isEmpty();
    }

    @Test
    @DisplayName("Every subscriber gets its own copy of an event")
    void everySubscriberGetsItsOwnCopyOfAnEvent() throws Exception {
        // Publish/subscribe, not a work queue: two subscribers of the same type must each get
        // the event, rather than the broker handing it to whichever is free.
        var first = subscribeTo(ContentCaptured.class);
        var second = subscribeTo(ContentCaptured.class);
        var published = ContentCaptured.builder()
                .chainId(ChainId.next())
                .content(REFINEMENT)
                .build();

        bus.publish(published);

        assertThat(next(first)).isEqualTo(published);
        assertThat(next(second)).isEqualTo(published);
    }

    @Test
    @DisplayName("The health check reports up while the broker is reachable")
    void theHealthCheckReportsUpWhileTheBrokerIsReachable() {
        assertThat(bus.checkHealth().up()).isTrue();
    }

    private <E extends PipelineEvent> BlockingQueue<E> subscribeTo(Class<E> type) {
        BlockingQueue<E> inbox = new LinkedBlockingQueue<>();
        bus.subscribe(type, inbox::add);
        return inbox;
    }

    /** Fails the test rather than returning null when nothing arrives in time. */
    private <E extends PipelineEvent> E next(BlockingQueue<E> inbox) throws InterruptedException {
        var event = inbox.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertThat(event)
                .as("no event arrived within %ds".formatted(WAIT_SECONDS))
                .isNotNull();
        return event;
    }
}
