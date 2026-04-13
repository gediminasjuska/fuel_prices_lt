-- Run this in Supabase SQL Editor
-- Adds lat/lng columns to fuel_prices and creates a geocode cache table

-- Step 1: Add coordinate columns to fuel_prices
ALTER TABLE fuel_prices ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE fuel_prices ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;

-- Step 2: Create geocode cache table (so we don't re-geocode every run)
CREATE TABLE IF NOT EXISTS station_locations (
  company TEXT NOT NULL,
  address TEXT NOT NULL,
  latitude DOUBLE PRECISION,
  longitude DOUBLE PRECISION,
  PRIMARY KEY (company, address)
);

-- Enable public read on station_locations
ALTER TABLE station_locations ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read locations" ON station_locations FOR SELECT USING (true);
