-- Allow ticket deletion: courier packets keep their row but lose the ticket link.
ALTER TABLE courier_packets DROP CONSTRAINT fk_cp_ticket;
ALTER TABLE courier_packets ADD CONSTRAINT fk_cp_ticket FOREIGN KEY (linked_ticket_id) REFERENCES tickets(id) ON DELETE SET NULL;
