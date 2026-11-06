-- Migration 4: fix the derived YouTube id.
--
-- Migration 1 matched "/v=", which never occurs in the canonical form the editor
-- suggests ("https://www.youtube.com/watch?v=<id>" uses "?v="), so youtube_id was
-- null for every normal YouTube link and the UI silently lost its thumbnail and
-- watch link. Replace the generated column with a pattern covering all the forms
-- the app accepts; `substring(... from ...)` returns capture group 1, so the
-- prefix alternatives must stay non-capturing.

alter table public.recipes drop column youtube_id;

alter table public.recipes add column youtube_id text generated always as (
  case
    when youtube_url ~* '(?:[?&]v=|youtu\.be/|/embed/|/shorts/|/v/)([A-Za-z0-9_-]{11})'
      then substring(youtube_url from '(?:[?&]v=|youtu\.be/|/embed/|/shorts/|/v/)([A-Za-z0-9_-]{11})')
  end
) stored;
