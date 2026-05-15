-- Track arrival vs departure bypass separately so supervisors can grant/revoke finely
-- and consumed arrival bypass does not imply departure is still open unless granted.

ALTER TABLE shift_punch_day_overrides ADD COLUMN allow_arrival_bypass BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE shift_punch_day_overrides ADD COLUMN allow_departure_bypass BOOLEAN NOT NULL DEFAULT TRUE;
