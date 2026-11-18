UPDATE users
SET
    password_hash = '$2a$10$c6bZz0YRraMYzZbxb.TaZuc59bWv1i6LF0MBMWgoLAJFGHkUYBtuC',
    account_status = 'ACTIVE',
    is_verified = true
WHERE id = '00000000-0000-0000-0000-000000000001';