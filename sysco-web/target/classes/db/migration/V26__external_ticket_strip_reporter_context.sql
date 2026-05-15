-- External (créer un ticket) tickets no longer store reporter DGDA / office context.
UPDATE tickets
SET reporter_direction_id = NULL,
    reporter_sous_direction_id = NULL,
    reporting_office = NULL
WHERE UPPER(TRIM(COALESCE(ticket_type, ''))) = 'EXTERNAL';
