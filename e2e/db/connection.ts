import mysql, { Pool } from 'mysql2/promise';
import { env } from '../core/env';

export function createPool(options: { readOnly: boolean }): Pool {
  const pool = mysql.createPool({ ...env.db, connectionLimit: 2, timezone: 'Z' });
  if (options.readOnly) {
    // 이 풀로는 쓰기가 실패한다. 읽기 헬퍼가 실수로 상태를 바꾸지 못하게 세션 단위로 막는다
    pool.on('connection', (connection) => {
      connection.query('SET SESSION TRANSACTION READ ONLY');
    });
  }
  return pool;
}
