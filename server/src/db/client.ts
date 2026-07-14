import fs from 'fs';
import path from 'path';
import pg from 'pg';
import { config } from '../config.js';

const { Pool } = pg;

export const pool = new Pool({ connectionString: config.databaseUrl });

export async function query<T extends pg.QueryResultRow = pg.QueryResultRow>(
  text: string,
  params?: unknown[]
): Promise<pg.QueryResult<T>> {
  return pool.query<T>(text, params);
}

export async function migrate(): Promise<void> {
  const sqlPath = path.join(import.meta.dirname, '../../migrations/001_init.sql');
  const sql = fs.readFileSync(sqlPath, 'utf8');
  await pool.query(sql);
}
