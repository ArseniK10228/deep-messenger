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
  await pool.query(`
    CREATE TABLE IF NOT EXISTS schema_migrations (
      filename TEXT PRIMARY KEY,
      applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
    )
  `);

  const hasUsers = await pool.query<{ t: string | null }>(
    `SELECT to_regclass('public.users') AS t`
  );
  const bootstrapped = await pool.query<{ c: number }>(
    `SELECT COUNT(*)::int AS c FROM schema_migrations`
  );

  if (hasUsers.rows[0]?.t && bootstrapped.rows[0].c === 0) {
    await pool.query(
      `INSERT INTO schema_migrations (filename) VALUES
         ('001_init.sql'),
         ('002_telegram_gateway.sql'),
         ('003_email_auth.sql')
       ON CONFLICT (filename) DO NOTHING`
    );
  }

  const dir = path.join(import.meta.dirname, '../../migrations');
  const files = fs.readdirSync(dir).filter((f) => f.endsWith('.sql')).sort();

  for (const file of files) {
    const applied = await pool.query('SELECT 1 FROM schema_migrations WHERE filename = $1', [file]);
    if (applied.rowCount && applied.rowCount > 0) continue;

    const sql = fs.readFileSync(path.join(dir, file), 'utf8');
    await pool.query(sql);
    await pool.query('INSERT INTO schema_migrations (filename) VALUES ($1)', [file]);
  }
}
