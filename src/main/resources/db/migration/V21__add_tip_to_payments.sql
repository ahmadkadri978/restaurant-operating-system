-- =========================================
-- V21: Add tip_amount to session_payments
-- Tracks gratuity left by customer
-- =========================================

ALTER TABLE session_payments
  ADD COLUMN tip_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00
  AFTER amount_returned;

