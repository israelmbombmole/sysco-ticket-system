-- Demo login "admin" / password "sysco": full platform scope (same as SUPER_ADMIN semantics in code).
UPDATE users
SET role = 'SUPER_ADMIN',
    direction_id = NULL,
    sous_direction_id = NULL
WHERE LOWER(TRIM(username)) = 'admin';
