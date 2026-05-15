-- DGDA ordre de mission: participant gender lists + départ/retour/observations.

ALTER TABLE field_mission_participants ADD COLUMN salutation VARCHAR(1);

ALTER TABLE field_missions ADD COLUMN departure_note VARCHAR(512);
ALTER TABLE field_missions ADD COLUMN return_note VARCHAR(512);
ALTER TABLE field_missions ADD COLUMN observations_note VARCHAR(4000);
