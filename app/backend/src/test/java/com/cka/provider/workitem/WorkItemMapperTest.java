package com.cka.provider.workitem;

import static org.assertj.core.api.Assertions.assertThat;

import com.cka.core.WorkItemType;
import com.cka.testsupport.Ticket;
import com.cka.testsupport.TicketMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The mapping contract every work item provider's mapper must keep (PRD 7.9), stated as a test
 * against {@link TicketMapper} — a stand-in for a tracker, since T05 ships no real provider.
 *
 * <p>Mocking a mapper would prove nothing: a stubbed {@code toWorkItem} returns whatever the
 * stub says, which is the very thing under test. So one real mapper over a stand-in native type
 * is what makes the extension point testable at all, and it is the worked example T13's GitHub
 * mapper follows.
 */
@DisplayName("A work item mapper keeps the tracker's meaning (PRD 7.9)")
class WorkItemMapperTest {

    private final WorkItemMapper<Ticket> mapper = new TicketMapper();

    @Test
    @DisplayName("Maps the tracker's own type label alongside the generic type")
    void mapsTheTrackersOwnTypeLabelAlongsideTheGenericType() {
        var workItem = mapper.toWorkItem(Ticket.builder()
                .key("CKA-7")
                .headline("Login fails on Safari")
                .kind("Defect")
                .epicKey("CKA-1")
                .build());

        assertThat(workItem.type()).isEqualTo(WorkItemType.BUG);
        assertThat(workItem.providerTypeName()).isEqualTo("Defect");
    }

    @Test
    @DisplayName("Keeps the tracker's label even when no generic type fits")
    void keepsTheTrackersLabelEvenWhenNoGenericTypeFits() {
        // The case the model exists to protect: a milestone is not an epic, and the core must not
        // be told it is. OTHER plus the real label, never a convenient near-match.
        var workItem = mapper.toWorkItem(Ticket.builder()
                .key("CKA-9")
                .headline("v2 launch")
                .kind("Milestone")
                .build());

        assertThat(workItem.type()).isEqualTo(WorkItemType.OTHER);
        assertThat(workItem.providerTypeName()).isEqualTo("Milestone");
    }

    @Test
    @DisplayName("Nests child items under their parent")
    void nestsChildItemsUnderTheirParent() {
        var child = Ticket.builder()
                .key("CKA-7")
                .headline("Login fails")
                .kind("Defect")
                .epicKey("CKA-1")
                .build();
        var topLevel = Ticket.builder()
                .key("CKA-1")
                .headline("Authentication")
                .kind("Epic")
                .build();

        assertThat(mapper.toWorkItem(child).parentId()).isEqualTo("CKA-1");
        assertThat(mapper.toWorkItem(topLevel).parentId()).isNull();
    }

    @Test
    @DisplayName("Round-trips back to the tracker's own shape")
    void roundTripsBackToTheTrackersOwnShape() {
        var ticket = Ticket.builder()
                .key("CKA-7")
                .headline("Login fails on Safari")
                .kind("Defect")
                .epicKey("CKA-1")
                .build();

        // Back out through the provider's label, not the generic type — mapping to the core and
        // back must not quietly rewrite "Defect" as "Bug".
        assertThat(mapper.toNative(mapper.toWorkItem(ticket))).isEqualTo(ticket);
    }
}
