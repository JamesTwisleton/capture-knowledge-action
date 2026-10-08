package com.cka.provider.eventbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.CapturedContent;
import com.cka.core.ChainId;
import com.cka.core.Knowledge;
import com.cka.core.PageReference;
import com.cka.core.Provenance;
import com.cka.core.Summary;
import com.cka.core.event.ContentCaptured;
import com.cka.core.event.KnowledgeStored;
import com.cka.core.event.PipelineEvent;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
 * below depends on a clean one, since each test subscribes to its own fresh queue.
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

    @Autowired
    private EventBus bus;

    @Test
    @DisplayName("An event published to the broker comes back to a subscriber unchanged")
    void anEventPublishedToTheBrokerComesBackToASubscriberUnchanged() throws Exception {
        var inbox = subscribeTo(ContentCaptured.class);
        var published = ContentCaptured.builder()
                .chainId(ChainId.next())
                .content(REFINEMENT)
                .build();

        bus.publish(published);

        // Equality across the whole record is the point: it only holds if every field survived
        // the round trip through JSON, including the nested CapturedContent and the chain id.
        assertThat(next(inbox)).isEqualTo(published);
    }

    @Test
    @DisplayName("Subscribing to the sealed parent receives every kind of event")
    void subscribingToTheSealedParentReceivesEveryKindOfEvent() throws Exception {
        // What AuditTrail depends on. In memory it falls out of Class.isInstance; a broker has
        // no type hierarchy, so it has to be a wildcard binding.
        var inbox = subscribeTo(PipelineEvent.class);
        var captured = ContentCaptured.builder()
                .chainId(ChainId.next())
                .content(REFINEMENT)
                .build();
        var stored = KnowledgeStored.builder()
                .chainId(ChainId.next())
                .knowledge(Knowledge.builder()
                        .page(PageReference.builder()
                                .id("refinement.md")
                                .location("memory://vault/refinement.md")
                                .build())
                        .content(REFINEMENT)
                        .summary(Summary.builder()
                                .text("Decided: a duplicate.")
                                .producedBy(LLM)
                                .build())
                        .build())
                .build();

        bus.publish(captured);
        bus.publish(stored);

        assertThat(List.of(next(inbox), next(inbox))).containsExactly(captured, stored);
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
                .knowledge(Knowledge.builder()
                        .page(PageReference.builder()
                                .id("refinement.md")
                                .location("memory://vault/refinement.md")
                                .build())
                        .content(REFINEMENT)
                        .summary(Summary.builder()
                                .text("Decided: a duplicate.")
                                .producedBy(LLM)
                                .build())
                        .build())
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
