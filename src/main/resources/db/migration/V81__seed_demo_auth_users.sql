-- =========================================================
-- LOGIN MVP: seed demo member/user accounts missed by V4.
-- V4 creates role_name = 'ADMIN' but filters role_name = 'Admin',
-- so these INSERT ... SELECT statements produced zero rows.
-- =========================================================

INSERT INTO tbl_dim_member_pseudonym (
    role_id,
    team_id,
    pseudonym,
    external_user_hash,
    active_from,
    created_by,
    updated_by
)
SELECT
    r.role_id,
    NULL,
    v.username,
    encode(digest(v.email, 'sha256'), 'hex'),
    CURRENT_DATE,
    'SYSTEM',
    'SYSTEM'
FROM tbl_dim_role r
CROSS JOIN (
    VALUES
        ('nk_trung', 'Nguyen Khac Trung', 'nk_trung@brycen.com.vn'),
        ('pd_khoa', 'Pham Dang Khoa', 'pd_khoa@brycen.com.vn'),
        ('nvt_dung', 'Nguyen Vo Tien Dung', 'nvt_dung@brycen.com.vn'),
        ('lx_loc', 'Le Xuan Loc', 'lx_loc@brycen.com.vn')
) AS v(username, fullname, email)
WHERE r.role_name = 'ADMIN'
ON CONFLICT (pseudonym) DO NOTHING;

INSERT INTO tbl_auth_user_account (
    member_key,
    username,
    fullname,
    email,
    email_hash,
    password_hash,
    password_algo,
    is_active,
    created_by,
    updated_by
)
SELECT
    m.member_key,
    v.username,
    v.fullname,
    v.email,
    encode(digest(v.email, 'sha256'), 'hex'),
    crypt('Admin@123456', gen_salt('bf')),
    'bcrypt',
    TRUE,
    'SYSTEM',
    'SYSTEM'
FROM (
    VALUES
        ('nk_trung', 'Nguyen Khac Trung', 'nk_trung@brycen.com.vn'),
        ('pd_khoa', 'Pham Dang Khoa', 'pd_khoa@brycen.com.vn'),
        ('nvt_dung', 'Nguyen Vo Tien Dung', 'nvt_dung@brycen.com.vn'),
        ('lx_loc', 'Le Xuan Loc', 'lx_loc@brycen.com.vn')
) AS v(username, fullname, email)
JOIN tbl_dim_member_pseudonym m
    ON m.pseudonym = v.username
ON CONFLICT (username) DO NOTHING;
