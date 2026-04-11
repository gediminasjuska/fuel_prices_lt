-- Run this in Supabase SQL Editor (Dashboard > SQL Editor > New Query)

CREATE TABLE fuel_prices (
  id BIGSERIAL PRIMARY KEY,
  date DATE NOT NULL,
  company TEXT NOT NULL,
  address TEXT NOT NULL,
  municipality TEXT NOT NULL,
  petrol95 DOUBLE PRECISION,
  petrol98 DOUBLE PRECISION,
  diesel DOUBLE PRECISION,
  lpg DOUBLE PRECISION,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  UNIQUE(date, company, address)
);

CREATE INDEX idx_fuel_prices_date ON fuel_prices(date);
CREATE INDEX idx_fuel_prices_company ON fuel_prices(company);
CREATE INDEX idx_fuel_prices_municipality ON fuel_prices(municipality);

-- Enable Row Level Security with public read access
ALTER TABLE fuel_prices ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read" ON fuel_prices FOR SELECT USING (true);
