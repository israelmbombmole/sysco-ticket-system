-- DGDA-style mission order extras: transport / itinerary, duration note, expenses.

ALTER TABLE field_missions ADD COLUMN transport_detail VARCHAR(4000);
ALTER TABLE field_missions ADD COLUMN duration_note VARCHAR(512);
ALTER TABLE field_missions ADD COLUMN expenses_note VARCHAR(512);
