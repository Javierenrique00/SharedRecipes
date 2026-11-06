-- Sample data so the app has something to render on first run.
-- Apply with: supabase__execute_sql (runs as postgres, bypasses RLS).
-- Safe to re-run: guarded by a title check.

insert into public.recipes (title, description, youtube_url, servings, prep_minutes, cook_minutes, created_by)
select
  'Pad Thai',
  'Rice noodles stir-fried with tamarind, fish sauce and lime. The sauce is balanced before it ever hits the wok.',
  'https://www.youtube.com/watch?v=BWk1C0dMj8o',
  2, 20, 10,
  null
where not exists (select 1 from public.recipes where title = 'Pad Thai');

insert into public.recipe_ingredients (recipe_id, position, quantity, unit, name)
select r.id, v.position, v.quantity, v.unit, v.name
from public.recipes r
cross join (values
  (0, 200.00, 'g',   'flat rice noodles'),
  (1,   3.00, 'tbsp','tamarind paste'),
  (2,   2.00, 'tbsp','fish sauce'),
  (3,   1.00, 'tbsp','palm sugar'),
  (4, 200.00, 'g',   'shrimp, peeled'),
  (5,   2.00, null,  'eggs'),
  (6, 100.00, 'g',   'bean sprouts'),
  (7,   3.00, null,  'spring onions, sliced'),
  (8,   1.00, null,  'lime')
) as v(position, quantity, unit, name)
where r.title = 'Pad Thai'
  and not exists (select 1 from public.recipe_ingredients i where i.recipe_id = r.id);

insert into public.recipe_steps (recipe_id, step_number, instruction)
select r.id, v.step_number, v.instruction
from public.recipes r
cross join (values
  (1, 'Soak the rice noodles in warm water for 15 minutes until pliable but still firm.'),
  (2, 'Whisk the tamarind, fish sauce and palm sugar into a sauce and taste it: sour first, then sweet, then salty.'),
  (3, 'Sear the shrimp in a very hot wok until just opaque, then push to one side.'),
  (4, 'Crack in the eggs, scramble briefly, then add the drained noodles and the sauce.'),
  (5, 'Toss constantly for 2-3 minutes until the noodles absorb the sauce, then fold in the bean sprouts.'),
  (6, 'Finish with spring onions and a wedge of lime.')
) as v(step_number, instruction)
where r.title = 'Pad Thai'
  and not exists (select 1 from public.recipe_steps s where s.recipe_id = r.id);
