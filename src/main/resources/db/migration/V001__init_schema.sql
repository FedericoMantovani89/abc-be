-- ========================================================================
-- ABC Musical Company — schema iniziale completo
-- ========================================================================

-- ========================================================================
-- ROLES & USERS
-- ========================================================================

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE users (
    id                  BIGSERIAL PRIMARY KEY,
    email               VARCHAR(255) NOT NULL UNIQUE,
    password            VARCHAR(255),                   -- nullable per utenti OAuth-only
    first_name          VARCHAR(100),
    last_name           VARCHAR(100),
    oauth_provider      VARCHAR(20),                    -- 'google' | 'facebook' | null
    oauth_id            VARCHAR(255),
    profile_picture_url VARCHAR(500),
    active              BOOLEAN NOT NULL DEFAULT true,
    verified            BOOLEAN NOT NULL DEFAULT false,
    last_login_at       TIMESTAMP,
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    role_id             BIGINT NOT NULL REFERENCES roles(id)
);

CREATE TABLE tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_type  VARCHAR(50) NOT NULL,      -- 'PASSWORD_RESET' | 'EMAIL_VERIFICATION'
    token_value VARCHAR(500) NOT NULL,
    expires_at  TIMESTAMP NOT NULL,
    used        BOOLEAN DEFAULT false,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========================================================================
-- SHOWS
-- ========================================================================

CREATE TABLE shows (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    plot                TEXT NOT NULL DEFAULT '',       -- HTML sanitizzato, Jsoup
    duration_minutes    INT  NOT NULL DEFAULT 0,
    age_recommendation  INT,
    director            VARCHAR(255),
    set_designer        VARCHAR(255),
    costume_designer    VARCHAR(255),
    choreographer       VARCHAR(255),
    hair_and_makeup     VARCHAR(255),
    producer            VARCHAR(255),
    production_year     INT,
    trailer_url         VARCHAR(512),
    official_website_url VARCHAR(512),
    reviews_url         VARCHAR(512),
    social_media_url    VARCHAR(512),
    poster_image_url    VARCHAR(512),
    content_warnings    JSONB DEFAULT '[]',             -- [{"description":"...","severity":"info|warning|danger"}]
    created_by          BIGINT REFERENCES users(id),
    updated_by          BIGINT REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at          TIMESTAMP
);

CREATE TABLE show_cast (
    id         BIGSERIAL PRIMARY KEY,
    show_id    BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    first_name VARCHAR(255) NOT NULL,
    last_name  VARCHAR(255) NOT NULL,
    role_name  VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE show_images (
    id            BIGSERIAL PRIMARY KEY,
    show_id       BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    image_url     VARCHAR(512) NOT NULL,
    caption       VARCHAR(255),
    display_order INT DEFAULT 0,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE show_scenes (
    id           BIGSERIAL PRIMARY KEY,
    show_id      BIGINT NOT NULL REFERENCES shows(id) ON DELETE CASCADE,
    scene_number VARCHAR(50),              -- es. "Atto I sc.2", "Scena 3" — libero
    title        VARCHAR(255) NOT NULL,    -- es. "Il duetto del balcone"
    sort_order   INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE scene_cast_roles (
    scene_id  BIGINT NOT NULL REFERENCES show_scenes(id) ON DELETE CASCADE,
    role_name VARCHAR(255) NOT NULL,       -- corrisponde a show_cast.role_name
    PRIMARY KEY (scene_id, role_name)
);

-- ========================================================================
-- EVENTS (pubblici)
-- ========================================================================

CREATE TABLE event_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE events (
    id               BIGSERIAL PRIMARY KEY,
    title            VARCHAR(255) NOT NULL,
    description      TEXT,
    event_date       TIMESTAMP NOT NULL,
    location_venue   VARCHAR(255) NOT NULL,
    location_address VARCHAR(500),
    location_city    VARCHAR(100),
    location_province VARCHAR(2),
    poster_image_url VARCHAR(512),
    booking_open_at  TIMESTAMP,
    booking_close_at TIMESTAMP,
    booking_link     VARCHAR(512),
    contact_email    VARCHAR(100),
    contact_phone    VARCHAR(20),
    event_type_id    BIGINT REFERENCES event_types(id),
    show_id          BIGINT REFERENCES shows(id),
    created_by       BIGINT REFERENCES users(id),
    updated_by       BIGINT REFERENCES users(id),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP
);

-- ========================================================================
-- CALENDAR (interno soci)
-- ========================================================================

CREATE TABLE calendar_event_types (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(100) NOT NULL UNIQUE,
    icon_class       VARCHAR(100),                     -- FontAwesome class, es. "fa-microphone"
    color_hex        VARCHAR(7),                       -- #RRGGBB
    active           BOOLEAN NOT NULL DEFAULT true,
    is_rehearsal_type BOOLEAN NOT NULL DEFAULT false   -- discrimina i tipi "Prova"
);

CREATE TABLE calendar_events (
    id                 BIGSERIAL PRIMARY KEY,
    title              VARCHAR(255) NOT NULL,
    description        TEXT,
    event_type_id      BIGINT REFERENCES calendar_event_types(id),
    start_datetime     TIMESTAMP NOT NULL,
    end_datetime       TIMESTAMP,
    location           VARCHAR(255),
    venue              VARCHAR(255),
    is_recurring       BOOLEAN NOT NULL DEFAULT false,
    recurrence_pattern VARCHAR(100),
    public_event_id    BIGINT,                         -- FK loose a events.id (no constraint)
    target_roles       TEXT,                           -- comma-sep es. "DIRECTOR,STAFF"; null=tutti
    show_id            BIGINT REFERENCES shows(id),    -- solo per eventi tipo "Prova"
    created_by         BIGINT REFERENCES users(id),
    updated_by         BIGINT REFERENCES users(id),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at         TIMESTAMP
);

CREATE TABLE calendar_event_rehearsal_roles (
    event_id  BIGINT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    role_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (event_id, role_name)
);

CREATE TABLE calendar_event_scenes (
    event_id  BIGINT NOT NULL REFERENCES calendar_events(id) ON DELETE CASCADE,
    scene_id  BIGINT NOT NULL REFERENCES show_scenes(id) ON DELETE CASCADE,
    PRIMARY KEY (event_id, scene_id)
);

-- ========================================================================
-- DOCUMENTS / MEDIA
-- ========================================================================

CREATE TABLE folders (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(255) NOT NULL,
    parent_folder_id BIGINT REFERENCES folders(id) ON DELETE CASCADE,
    allowed_roles    TEXT,                             -- comma-sep; null=tutti i soci
    created_by       BIGINT REFERENCES users(id),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at       TIMESTAMP
);

CREATE TABLE documents (
    id                BIGSERIAL PRIMARY KEY,
    uuid              UUID NOT NULL UNIQUE,
    folder_id         BIGINT REFERENCES folders(id) ON DELETE SET NULL,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    file_name         VARCHAR(255) NOT NULL,
    file_path         VARCHAR(512) NOT NULL,
    file_size_bytes   BIGINT,
    mime_type         VARCHAR(100),
    document_category VARCHAR(50),                    -- SCRIPT|SHEET_MUSIC|TEACHING_MATERIAL|OTHER
    visibility        VARCHAR(20) DEFAULT 'MEMBERS',  -- MEMBERS|STAFF_ONLY|ADMIN_ONLY
    media_type        VARCHAR(20) NOT NULL DEFAULT 'DOCUMENT', -- DOCUMENT|AUDIO|VIDEO
    download_count    INT DEFAULT 0,
    created_by        BIGINT REFERENCES users(id),
    updated_by        BIGINT REFERENCES users(id),
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at        TIMESTAMP
);

-- ========================================================================
-- COMMUNICATIONS
-- ========================================================================

CREATE TABLE communication_types (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    active      BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE communications (
    id                    BIGSERIAL PRIMARY KEY,
    title                 VARCHAR(255) NOT NULL,
    content               TEXT NOT NULL,
    communication_type_id BIGINT REFERENCES communication_types(id),
    priority              VARCHAR(20) DEFAULT 'NORMAL',  -- LOW|NORMAL|HIGH|URGENT
    pinned                BOOLEAN DEFAULT false,
    published_at          TIMESTAMP,
    expires_at            TIMESTAMP,
    target_roles          TEXT,                          -- comma-sep; null=tutti i soci
    created_by            BIGINT REFERENCES users(id),
    updated_by            BIGINT REFERENCES users(id),
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at            TIMESTAMP
);

-- ========================================================================
-- AUDIT LOG
-- ========================================================================

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users(id),
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   BIGINT,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ========================================================================
-- INDEXES
-- ========================================================================

CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_users_role           ON users(role_id);
CREATE INDEX idx_users_active         ON users(active) WHERE active = true;

CREATE INDEX idx_tokens_user          ON tokens(user_id);
CREATE INDEX idx_tokens_type_expires  ON tokens(token_type, expires_at);
CREATE INDEX idx_tokens_value         ON tokens(token_value);

CREATE INDEX idx_shows_not_deleted    ON shows(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_show_cast_show_id    ON show_cast(show_id);
CREATE INDEX idx_show_scenes_show     ON show_scenes(show_id);
CREATE INDEX idx_cal_event_scenes     ON calendar_event_scenes(event_id);

CREATE INDEX idx_events_date          ON events(event_date);
CREATE INDEX idx_events_not_deleted   ON events(deleted_at) WHERE deleted_at IS NULL;

CREATE INDEX idx_calendar_start       ON calendar_events(start_datetime);
CREATE INDEX idx_calendar_not_deleted ON calendar_events(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_calendar_events_show ON calendar_events(show_id);

CREATE INDEX idx_documents_folder     ON documents(folder_id);
CREATE INDEX idx_documents_not_deleted ON documents(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_documents_fts        ON documents USING gin(to_tsvector('italian', title));
CREATE INDEX idx_documents_uuid       ON documents(uuid);

CREATE INDEX idx_comms_not_deleted    ON communications(deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_comms_published      ON communications(published_at);

CREATE INDEX idx_audit_user           ON audit_logs(user_id);
CREATE INDEX idx_audit_created        ON audit_logs(created_at);

-- ========================================================================
-- SEED DATA
-- ========================================================================

INSERT INTO roles (name, description) VALUES
    ('PUBLIC',     'Visitatore pubblico'),
    ('MEMBER',     'Socio — accesso area riservata'),
    ('TECHNICIAN', 'Tecnico'),
    ('DIRECTOR',   'Direttore artistico'),
    ('STAFF',      'Staff — gestione contenuti'),
    ('ADMIN',      'Amministratore — accesso completo'),
    ('GOD',        'Account tecnico — come ADMIN');

INSERT INTO event_types (name, description) VALUES
    ('Spettacolo',      'Performance teatrale'),
    ('Workshop',        'Laboratorio o corso'),
    ('Audizione',       'Audizione per cast'),
    ('Prova Generale',  'Prova generale aperta al pubblico'),
    ('Evento Speciale', 'Eventi speciali e celebrazioni');

INSERT INTO calendar_event_types (name, icon_class, color_hex, active, is_rehearsal_type) VALUES
    ('Prova Canto',  'fa-music',        '#7C3AED', true,  true),
    ('Prova Teatro', 'fa-masks-theater','#0EA5E9', true,  true),
    ('Riunione',     'fa-users',        '#16A34A', true,  false),
    ('Spettacolo',   'fa-star',         '#CA8A04', true,  false),
    ('Altro',        'fa-calendar-day', '#475569', true,  false);

INSERT INTO communication_types (name, description, active) VALUES
    ('Avviso',   'Avvisi generali',          true),
    ('Notizie',  'Notizie e aggiornamenti',  true),
    ('Bacheca',  'Messaggi di bacheca',      true),
    ('Urgente',  'Comunicazioni urgenti',    true);

-- L'utente admin NON viene creato qui: viene creato all'avvio dall'AdminSeeder
-- (ApplicationRunner) leggendo ADMIN_EMAIL / ADMIN_PASSWORD dall'ambiente.
