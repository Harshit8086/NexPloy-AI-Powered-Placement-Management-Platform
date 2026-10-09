-- Remove only the demo risk entries inserted by V38. Runtime predictions use
-- generated IDs and are left untouched.
DELETE FROM risk_entries
WHERE id IN (
  'risk_amit',
  'risk_aman',
  'risk_vikram',
  'risk_sneha',
  'risk_rohit',
  'risk_farhan',
  'risk_neha'
);
