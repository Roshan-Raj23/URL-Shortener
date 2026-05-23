CREATE TABLE IF NOT EXISTS url_mapping (
    short_code VARCHAR(255) NOT NULL PRIMARY KEY,
    long_url   VARCHAR(2048) NOT NULL,
    created_at DATETIME(6) NULL
);
