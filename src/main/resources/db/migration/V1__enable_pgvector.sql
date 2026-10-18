-- Step 1: enable extensions the app depends on.
-- Tables (users, documents, interviews, ...) arrive in later migrations, one per feature step.

-- Vector type + similarity operators for storing and searching embeddings
CREATE EXTENSION IF NOT EXISTS vector;

-- gen_random_uuid() for UUID primary keys
CREATE EXTENSION IF NOT EXISTS pgcrypto;
