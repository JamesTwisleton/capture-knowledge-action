package com.cka.provider.workitem;

import com.cka.core.WorkItemType;
import com.cka.testsupport.Ticket;
import com.cka.testsupport.TicketMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The mapping contract every work item provider's mapper must keep (PRD 7.9), stated as a test
 * against {@link TicketMapper} — a stand-in for a tracker, since T05 ships no real provider.
 *
 * <p>Mocking a mapper would prove nothing: a stubbed {@code toWorkItem} returns whatever the
 * stub says, which is the very thing under test. So one real mapper over a stand-in native type
 * is what makes the extension point testable at all, and it is the worked example T13's GitHub
 * mapper follows.
 */
class WorkItemMapperTest {

    private final WorkItemMapper<Ticket> mapper = new TicketMapper();

    @Test
    void mapsTheTrackersOwnTypeLabelAlongsideTheGenericType() {
        var workItem = mapper.toWorkItem(new Ticket("CKA-7", "Login fails on Safari", "Defect", "CKA-1"));

        assertThat(workItem.type()).isEqualTo(WorkItemType.BUG);
        assertThat(workItem.providerTypeName()).isEqualTo("Defect");
    }

    @Test
    void keepsTheTrackersLabelEvenWhenNoGenericTypeFits() {
        // The case the model exists to protect: a milestone is not an epic, and the core must not
        // be told it is. OTHER plus the real label, never a convenient near-match.
        var workItem = mapper.toWorkItem(new Ticket("CKA-9", "v2 launch", "Milestone", null));

        assertThat(workItem.type()).isEqualTo(WorkItemType.OTHER);
        assertThat(workItem.providerTypeName()).isEqualTo("Milestone");
    }

    @Test
    void nestsChildItemsUnderTheirParent() {
        assertThat(mapper.toWorkItem(new Ticket("CKA-7", "Login fails", "Defect", "CKA-1")).parentId())
                .isEqualTo("CKA-1");
        assertThat(mapper.toWorkItem(new Ticket("CKA-1", "Authentication", "Epic", null)).parentId())
                .isNull();
    }

    @Test
    void roundTripsBackToTheTrackersOwnShape() {
        var ticket = new Ticket("CKA-7", "Login fails on Safari", "Defect", "CKA-1");

        // Back out through the provider's label, not the generic type — mapping to the core and
        // back must not quietly rewrite "Defect" as "Bug".
        assertThat(mapper.toNative(mapper.toWorkItem(ticket))).isEqualTo(ticket);
    }
}
